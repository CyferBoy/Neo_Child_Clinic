package com.neochildclinic.feature.inventory.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.core.logger.AuditLogWriter
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.preferences.NotificationSettingsManager
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.core.security.UserDisplayNameProvider
import com.neochildclinic.data.local.dao.InventoryDeductionDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.dao.VaccineDao
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.AuditLog
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.model.VaccineBatch
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * Verifies updateBatch() commits and rolls back atomically, driven through the real
 * InventoryRepositoryImpl constructor with in-memory DAO/sync fakes (no Unsafe, no field
 * injection). The test runner snapshots in-memory state on begin and restores it on error.
 */
class InventoryAtomicRollbackTest {

    private class TransactionalRunner : TransactionRunner {
        var inTransaction = false
            private set
        var commitCount = 0
            private set
        var rollbackCount = 0
            private set
        var onRollback: (() -> Unit)? = null

        override suspend fun <T> run(block: suspend () -> T): T {
            inTransaction = true
            try {
                val result = block()
                commitCount++
                return result
            } catch (t: Throwable) {
                rollbackCount++
                onRollback?.invoke()
                throw t
            } finally {
                inTransaction = false
            }
        }
    }

    private class TestContext : ContextWrapper(null) {
        private val dir = File(System.getProperty("java.io.tmpdir"), "ncc-rollback-${System.nanoTime()}").apply { mkdirs() }
        override fun getFilesDir(): File = dir
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = throw UnsupportedOperationException()
        override fun getApplicationContext(): Context = this
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> fake(clazz: Class<T>, handler: (String, Array<Any?>?) -> Any?): T =
        Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, args -> handler(method.name, args) } as T

    private lateinit var runner: TransactionalRunner
    private lateinit var repository: InventoryRepositoryImpl
    private val batchStore = mutableMapOf<String, VaccineBatchEntity>()
    private val transactionStore = mutableListOf<InventoryTransactionEntity>()
    private val syncQueueStore = mutableListOf<String>()

    private var failOnInsertTransaction = false
    private var failOnSyncEnqueue = false

    @Before
    fun setUp() {
        runner = TransactionalRunner()
        batchStore.clear()
        transactionStore.clear()
        syncQueueStore.clear()
        failOnInsertTransaction = false
        failOnSyncEnqueue = false

        val initialBatch = VaccineBatchEntity(
            batchId = "batch-101", vaccineId = "vac-001", batchNumber = "B-2026-X",
            manufacturer = "Serum Institute", purchaseDate = "2026-01-01", expiryDate = "2028-01-01",
            purchaseQuantity = 10, remainingQuantity = 10, supplier = "Distributor A",
            purchaseCost = 500.0, sellingPrice = 750.0
        )
        batchStore[initialBatch.batchId] = initialBatch

        var snapshotBatch = initialBatch.copy()
        var snapshotTransactions = transactionStore.toList()
        var snapshotSyncQueue = syncQueueStore.toList()
        runner.onRollback = {
            batchStore.clear()
            batchStore[snapshotBatch.batchId] = snapshotBatch
            transactionStore.clear()
            transactionStore.addAll(snapshotTransactions)
            syncQueueStore.clear()
            syncQueueStore.addAll(snapshotSyncQueue)
        }

        val vaccineDao = fake(VaccineDao::class.java) { name, args ->
            when (name) {
                "getBatchById" -> {
                    assertTrue("getBatchById must run inside transaction", runner.inTransaction)
                    batchStore[args!![0] as String]
                }
                "updateBatch" -> {
                    assertTrue("updateBatch must run inside transaction", runner.inTransaction)
                    val entity = args!![0] as VaccineBatchEntity
                    batchStore[entity.batchId] = entity
                    null
                }
                "insertTransaction" -> {
                    assertTrue("insertTransaction must run inside transaction", runner.inTransaction)
                    if (failOnInsertTransaction) throw IllegalStateException("Database disk I/O error inserting inventory transaction")
                    transactionStore.add(args!![0] as InventoryTransactionEntity)
                    null
                }
                else -> null
            }
        }

        val syncRepo = fake(SyncRepository::class.java) { name, args ->
            if (name == "enqueue") {
                assertTrue("sync enqueue must run inside transaction", runner.inTransaction)
                if (failOnSyncEnqueue) throw IllegalStateException("Sync queue table locked by concurrent process")
                syncQueueStore.add("${args!![0]}:${args[1]}")
            }
            null
        }

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
        val auditWriter = object : AuditLogWriter {
            override suspend fun recordLog(auditLog: AuditLog, transactionGroupId: String?) = Unit
        }

        repository = InventoryRepositoryImpl(
            transactionRunner = runner,
            syncQueueDao = fake(SyncQueueDao::class.java) { _, _ -> null },
            vaccineDao = vaccineDao,
            inventoryDeductionDao = fake(InventoryDeductionDao::class.java) { _, _ -> null },
            postgrest = client.postgrest,
            inventoryRemoteDataSource = fake(InventoryRemoteDataSource::class.java) { _, _ -> null },
            syncRepository = syncRepo,
            auditLogger = AuditLogger(sessionManager, auditWriter),
            settingsManager = NotificationSettingsManager(TestContext()),
            sessionManager = sessionManager,
            context = TestContext()
        )
    }

    private fun updatedBatch(remaining: Int) = VaccineBatch(
        batchId = "batch-101", vaccineId = "vac-001", batchNumber = "B-2026-X",
        remainingQuantity = remaining, purchaseDate = "2026-01-01", expiryDate = "2028-01-01",
        purchaseCost = 500.0, sellingPrice = 750.0
    )

    @Test
    fun `successful updateBatch commits stock quantity change and ledger transaction atomically`() = runBlocking {
        repository.updateBatch(updatedBatch(7), "dr_user", "Routine clinic adjustment")

        assertEquals("Transaction must commit exactly once", 1, runner.commitCount)
        assertEquals("Zero rollbacks on success", 0, runner.rollbackCount)
        assertEquals("Remaining quantity must be updated to 7", 7, batchStore["batch-101"]?.remainingQuantity)
        assertEquals("One inventory transaction must be created", 1, transactionStore.size)
        assertEquals(InventoryTransactionType.MANUAL_ADJUSTMENT.name, transactionStore.first().transactionType)
        assertEquals(-3, transactionStore.first().quantity)
    }

    @Test
    fun `exception during transaction insert triggers full rollback of stock quantity update`() = runBlocking {
        failOnInsertTransaction = true

        try {
            repository.updateBatch(updatedBatch(4), "dr_user", "Faulty adjustment")
            fail("Expected exception during insertTransaction")
        } catch (e: IllegalStateException) {
            assertTrue("Expected disk I/O error message", e.message!!.contains("Database disk I/O error"))
        }

        assertEquals("Transaction must NOT commit", 0, runner.commitCount)
        assertEquals("Transaction must roll back exactly once", 1, runner.rollbackCount)
        val preservedBatch = batchStore["batch-101"]
        assertNotNull(preservedBatch)
        assertEquals("Stock quantity must rollback to pre-transaction value (10)", 10, preservedBatch!!.remainingQuantity)
        assertTrue("No inventory transactions must survive after rollback", transactionStore.isEmpty())
        assertTrue("No sync queue entries must survive after rollback", syncQueueStore.isEmpty())
    }

    @Test
    fun `exception during sync queue enqueue triggers full rollback of stock quantity and transaction`() = runBlocking {
        failOnSyncEnqueue = true

        try {
            repository.updateBatch(updatedBatch(2), "dr_user", "Failing enqueue")
            fail("Expected exception during sync enqueue")
        } catch (e: IllegalStateException) {
            assertTrue("Expected sync table locked message", e.message!!.contains("Sync queue table locked"))
        }

        assertEquals("Transaction must NOT commit", 0, runner.commitCount)
        assertEquals("Transaction must roll back", 1, runner.rollbackCount)
        assertEquals("Stock quantity must remain 10 after rollback", 10, batchStore["batch-101"]?.remainingQuantity)
        assertTrue("Inventory transactions must be rolled back", transactionStore.isEmpty())
    }

    @Test
    fun `non-existent batch id exits gracefully without performing writes`() = runBlocking {
        val phantomBatch = VaccineBatch(
            batchId = "batch-does-not-exist", vaccineId = "vac-001", batchNumber = "GHOST",
            remainingQuantity = 5, purchaseDate = "2026-01-01", expiryDate = "2028-01-01",
            purchaseCost = 500.0, sellingPrice = 750.0
        )

        repository.updateBatch(phantomBatch, "dr_user", null)

        assertEquals("Transaction completes early via return@run", 1, runner.commitCount)
        assertEquals(0, runner.rollbackCount)
        assertEquals("Original batch remains untouched", 10, batchStore["batch-101"]?.remainingQuantity)
        assertTrue("No transactions written for phantom batch", transactionStore.isEmpty())
    }
}
