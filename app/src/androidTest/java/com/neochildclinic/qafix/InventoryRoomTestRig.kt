package com.neochildclinic.qafix

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neochildclinic.core.logger.AuditLogWriter
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.preferences.NotificationSettingsManager
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.core.security.UserDisplayNameProvider
import com.neochildclinic.data.local.dao.VaccineDao
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.database.TransactionRunnerImpl
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.SyncQueueEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.AuditLog
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.domain.model.VaccineBatch
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.feature.inventory.data.InventoryRemoteDataSource
import com.neochildclinic.feature.inventory.data.InventoryRepositoryImpl
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import java.lang.reflect.Proxy
import java.util.Collections

/**
 * Shared fixture for the real-Room inventory integrity tests. Only the collaborators that talk
 * to the network are faked; batch, ledger, queue and the transaction runner are the real ones,
 * so rollback semantics are SQLite's, not a hand-rolled snapshot.
 */
internal class InventoryRoomTestRig(
    context: Context,
    duplicatePkInLedger: Boolean = false,
    private val failOnEnqueueNumber: Int? = null
) {
    val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.query("PRAGMA busy_timeout = 30000").close()
            }
        })
        .build()

    val enqueued: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val realVaccineDao: VaccineDao = db.vaccineDao()

    private val vaccineDao: VaccineDao = if (duplicatePkInLedger) {
        object : VaccineDao by realVaccineDao {
            override suspend fun insertTransaction(transaction: InventoryTransactionEntity) {
                realVaccineDao.insertTransaction(transaction)
                // @Insert is ABORT (not REPLACE): this second insert is a real SQL UNIQUE error.
                realVaccineDao.insertTransaction(transaction.copy())
            }
        }
    } else realVaccineDao

    private val noopSync: SyncRepository = proxy { _, _ -> null }
    private val syncRepository = object : SyncRepository by noopSync {
        override suspend fun enqueue(
            entityName: String,
            entityId: String,
            operation: SyncOperation,
            priority: SyncPriority,
            transactionGroupId: String?
        ) {
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityName = entityName,
                    entityId = entityId,
                    operation = operation.name,
                    priority = priority.name,
                    transactionGroupId = transactionGroupId
                )
            )
            enqueued += entityName
            if (failOnEnqueueNumber != null && enqueued.size == failOnEnqueueNumber) {
                // Forced duplicate PK after one queue row has been written: proves the queue
                // write is rolled back too, not just the batch + ledger.
                throw android.database.sqlite.SQLiteConstraintException(
                    "UNIQUE constraint failed: sync_queue.queueId (forced mid-transaction failure)"
                )
            }
        }
    }

    val repository: InventoryRepositoryImpl by lazy {
        val client = createSupabaseClient("https://dummy.supabase.co", "dummy_key") {
            install(Postgrest)
            install(io.github.jan.supabase.auth.Auth) { minimalConfig() }
        }
        val sessionManager = SessionManager(
            auth = client.auth,
            userDisplayNameProvider = object : UserDisplayNameProvider {
                override suspend fun getUserDisplayName(userId: String): String = "Dr. Test"
            }
        )
        InventoryRepositoryImpl(
            transactionRunner = TransactionRunnerImpl(db),
            syncQueueDao = db.syncQueueDao(),
            vaccineDao = vaccineDao,
            inventoryDeductionDao = db.inventoryDeductionDao(),
            postgrest = client.postgrest,
            inventoryRemoteDataSource = proxy { _, _ -> null },
            syncRepository = syncRepository,
            auditLogger = AuditLogger(sessionManager, object : AuditLogWriter {
                override suspend fun recordLog(auditLog: AuditLog, transactionGroupId: String?) = Unit
            }),
            settingsManager = NotificationSettingsManager(context),
            sessionManager = sessionManager,
            context = context
        )
    }

    suspend fun seedBatch(remaining: Int = 10) {
        db.vaccineDao().upsertBatch(
            VaccineBatchEntity(
                batchId = BATCH_ID, vaccineId = "vac-001", batchNumber = "B-2026-X",
                manufacturer = "Serum Institute", purchaseDate = "2026-01-01", expiryDate = "2035-01-01",
                purchaseQuantity = remaining, remainingQuantity = remaining, supplier = "Distributor A",
                purchaseCost = 500.0, sellingPrice = 750.0
            )
        )
    }

    fun updatedBatch(remaining: Int) = VaccineBatch(
        batchId = BATCH_ID, vaccineId = "vac-001", batchNumber = "B-2026-X",
        remainingQuantity = remaining, purchaseDate = "2026-01-01", expiryDate = "2035-01-01",
        purchaseCost = 500.0, sellingPrice = 750.0
    )

    suspend fun remaining(): Int? = db.vaccineDao().getBatchById(BATCH_ID)?.remainingQuantity

    fun ledgerCount(): Int = db.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM inventory_transactions").use { c -> c.moveToFirst(); c.getInt(0) }

    fun queueCount(): Int = db.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM sync_queue").use { c -> c.moveToFirst(); c.getInt(0) }

    fun close() = db.close()

    private companion object {
        const val BATCH_ID = "batch-101"
    }
}

@Suppress("UNCHECKED_CAST")
private inline fun <reified T : Any> proxy(crossinline handler: (String, Array<Any?>?) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        handler(method.name, args)
    } as T
