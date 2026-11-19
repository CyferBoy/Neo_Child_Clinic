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
import com.neochildclinic.domain.repository.SyncRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.io.File

class InventoryStockMovementTest {

    private fun batch(remaining: Int = 10, used: Int = 0, wasted: Int = 0, borrowed: Int = 0) = VaccineBatchEntity(
        batchId = "b1",
        vaccineId = "v1",
        batchNumber = "B1",
        manufacturer = "M",
        purchaseDate = "2025-01-01",
        expiryDate = "2030-01-01",
        purchaseQuantity = remaining,
        remainingQuantity = remaining,
        supplier = "S",
        purchaseCost = 10.0,
        sellingPrice = 20.0,
        usedQuantity = used,
        wastedQuantity = wasted,
        borrowedQuantity = borrowed
    )

    @Test
    fun `vaccination deduction routes quantity into the used bucket`() {
        val deducted = batch().deducted(4, InventoryTransactionType.VACCINATION, "doctor")
        assertEquals(6, deducted.remainingQuantity)
        assertEquals(4, deducted.usedQuantity)
        assertEquals("doctor", deducted.updatedBy)
    }

    @Test
    fun `expired damage and cold chain deductions route into the wasted bucket`() {
        listOf(
            InventoryTransactionType.EXPIRED,
            InventoryTransactionType.DAMAGED,
            InventoryTransactionType.COLD_CHAIN_FAILURE,
            InventoryTransactionType.CONTAMINATED,
            InventoryTransactionType.OTHER
        ).forEach { type ->
            val deducted = batch().deducted(2, type, "owner")
            assertEquals(8, deducted.remainingQuantity)
            assertEquals(2, deducted.wastedQuantity)
        }
    }

    @Test
    fun `borrowed deduction routes into the borrowed bucket`() {
        val deducted = batch().deducted(3, InventoryTransactionType.BORROWED, "owner")
        assertEquals(7, deducted.remainingQuantity)
        assertEquals(3, deducted.borrowedQuantity)
    }

    @Test
    fun `a zero deduction is rejected instead of writing a bogus no-op movement`() {
        try {
            batch().deducted(0, InventoryTransactionType.VACCINATION, "doctor")
            throw AssertionError("expected a zero quantity to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("greater than zero"))
        }
    }

    @Test
    fun `a negative deduction is rejected instead of adding stock back and corrupting the buckets`() {
        try {
            batch().deducted(-2, InventoryTransactionType.VACCINATION, "doctor")
            throw AssertionError("expected a negative quantity to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("greater than zero"))
        }
    }

    @Test
    fun `stock transaction maps the movement fields and stamps user and timestamp`() {
        val tx = buildStockTransaction(
            vaccineId = "v1",
            batchId = "b1",
            transactionType = InventoryTransactionType.REVERSAL,
            quantity = 2,
            previousQuantity = 5,
            currentQuantity = 7,
            user = "owner",
            notes = "reversal",
            patientId = "p1",
            visitId = "visit-1"
        )
        assertEquals("REVERSAL", tx.transactionType)
        assertEquals(2, tx.quantity)
        assertEquals(5, tx.previousQuantity)
        assertEquals(7, tx.currentQuantity)
        assertEquals("p1", tx.patientId)
        assertEquals("visit-1", tx.visitId)
        assertEquals("owner", tx.createdBy)
        assertEquals("owner", tx.updatedBy)
        assertTrue(tx.transactionId.isNotBlank())
        val tx2 = buildStockTransaction("v1", "b1", InventoryTransactionType.PURCHASE, 5, 0, 5, "owner", null)
        assertEquals("PURCHASE", tx2.transactionType)
        assertNull(tx2.patientId)
    }

    // ---- updateBatch tests: real repository via its public constructor + interface fakes ----

    private class RecordingRunner : TransactionRunner {
        var runs = 0
            private set
        var inTransaction = false
            private set
        override suspend fun <T> run(block: suspend () -> T): T {
            runs++
            inTransaction = true
            try {
                return block()
            } finally {
                inTransaction = false
            }
        }
    }

    private class TestContext : ContextWrapper(null) {
        private val dir = File(System.getProperty("java.io.tmpdir"), "ncc-stock-${System.nanoTime()}").apply { mkdirs() }
        override fun getFilesDir(): File = dir
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = throw UnsupportedOperationException()
        override fun getApplicationContext(): Context = this
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> fake(clazz: Class<T>, handler: (String, Array<Any?>?) -> Any?): T =
        Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, args -> handler(method.name, args) } as T

    private lateinit var runner: RecordingRunner
    private lateinit var repository: InventoryRepositoryImpl
    private var updateInsideTransaction = false
    private var insertInsideTransaction = false

    @Before
    fun setUp() {
        runner = RecordingRunner()
        updateInsideTransaction = false
        insertInsideTransaction = false
        val existing = batch(remaining = 10)

        val vaccineDao = fake(VaccineDao::class.java) { name, args ->
            when (name) {
                "getBatchById" -> existing
                "updateBatch" -> { updateInsideTransaction = runner.inTransaction; null }
                "insertTransaction" -> { insertInsideTransaction = runner.inTransaction; null }
                else -> null
            }
        }
        val syncRepo = fake(SyncRepository::class.java) { _, _ -> null }
        val settingsManager = NotificationSettingsManager(TestContext())
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
            settingsManager = settingsManager,
            sessionManager = sessionManager,
            context = TestContext()
        )
    }

    @Test
    fun `updateBatch executes inside transactionRunner run`() = runBlocking {
        repository.updateBatch(
            com.neochildclinic.domain.model.VaccineBatch(
                batchId = "b1", vaccineId = "v1", batchNumber = "B1", remainingQuantity = 8,
                purchaseDate = "2025-01-01", expiryDate = "2030-01-01", purchaseCost = 10.0, sellingPrice = 20.0
            ),
            "admin", "Adjustment"
        )

        assertEquals("Transaction runner must run once", 1, runner.runs)
        assertTrue("updateBatch must execute inside transaction", updateInsideTransaction)
        assertTrue("insertTransaction must execute inside transaction", insertInsideTransaction)
    }

    @Test
    fun `updateBatch rejects negative quantity before entering transaction`() = runBlocking {
        val negativeBatch = com.neochildclinic.domain.model.VaccineBatch(
            batchId = "b1", vaccineId = "v1", batchNumber = "B1", remainingQuantity = -1,
            purchaseDate = "2025-01-01", expiryDate = "2030-01-01", purchaseCost = 10.0, sellingPrice = 20.0
        )

        try {
            repository.updateBatch(negativeBatch, "admin", null)
            org.junit.Assert.fail("Expected IllegalStateException for negative quantity")
        } catch (e: IllegalStateException) {
            assertEquals("Quantity cannot be negative.", e.message)
            assertEquals("Transaction runner should not run when guard fails", 0, runner.runs)
        }
    }
}
