package com.neochildclinic.inventoryfix

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
import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.VaccineEntity
import com.neochildclinic.domain.model.AuditLog
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.VaccinationItem
import com.neochildclinic.domain.model.VaccineBatch
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.feature.inventory.data.InventoryRemoteDataSource
import com.neochildclinic.feature.inventory.data.InventoryRepositoryImpl
import com.neochildclinic.feature.vaccination.domain.FakeReminderRepository
import com.neochildclinic.feature.vaccination.domain.FakeVaccinationRepository
import com.neochildclinic.feature.vaccination.domain.RecordingTransactionRunner
import com.neochildclinic.feature.vaccination.domain.VaccinationEditEngine
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * Regressions around stock deduction from a single batch and the edit-path expiry guard.
 *
 * Drives the real [InventoryRepositoryImpl] and [VaccinationEditEngine] with in-memory
 * DAO/sync fakes through their public constructors - no field injection, no Robolectric.
 */
class InventoryFixRegressionTest {

    private class TestContext : ContextWrapper(null) {
        private val dir = File(System.getProperty("java.io.tmpdir"), "ncc-inventoryfix-${System.nanoTime()}").apply { mkdirs() }
        override fun getFilesDir(): File = dir
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = throw UnsupportedOperationException()
        override fun getApplicationContext(): Context = this
    }

    private class NoopFinanceRepository : FinanceRepository {
        override fun getAllTransactions(): Flow<List<FinanceTransaction>> = flowOf(emptyList())
        override suspend fun recordIncome(amount: Double, cashAmount: Double, onlineAmount: Double, category: String, patientId: String?, visitId: String?, remarks: String?, recordedBy: String, transactionGroupId: String?) = Unit
        override suspend fun updateConsultationIncome(visitId: String, consultationId: String, originalAmount: Double, originalCashAmount: Double, originalOnlineAmount: Double, amount: Double, cashAmount: Double, onlineAmount: Double, remarks: String?, recordedBy: String, transactionGroupId: String?) = Unit
        override suspend fun updateIncomeForVisit(visitId: String, amount: Double, cashAmount: Double, onlineAmount: Double, remarks: String?, recordedBy: String, transactionGroupId: String?) = Unit
        override suspend fun migrateLegacyVaccinationCogs(vaccinations: List<Vaccination>) = Unit
        override suspend fun refreshTransactions() = Unit
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> fake(clazz: Class<T>, handler: (String, Array<Any?>?) -> Any?): T =
        Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, method, args -> handler(method.name, args) } as T

    private fun freshBatch(remaining: Int = 10) = VaccineBatchEntity(
        batchId = "b1", vaccineId = "vac-1", batchNumber = "B-FIX-1",
        manufacturer = "Serum Institute", purchaseDate = "2026-01-01", expiryDate = "2026-06-01",
        purchaseQuantity = 10, remainingQuantity = remaining, supplier = "Distributor A",
        purchaseCost = 500.0, sellingPrice = 750.0
    )

    private lateinit var runner: RecordingTransactionRunner
    private lateinit var repository: InventoryRepositoryImpl
    private lateinit var engine: VaccinationEditEngine
    private val batchStore = mutableMapOf<String, VaccineBatchEntity>()
    private val vaccineStore = mutableMapOf<String, VaccineEntity>()
    private val transactionStore = mutableListOf<InventoryTransactionEntity>()
    private val deductionStore = mutableListOf<InventoryDeductionEntity>()
    private val syncQueue = mutableListOf<String>()

    @Before
    fun setUp() {
        runner = RecordingTransactionRunner()
        batchStore.clear()
        vaccineStore.clear()
        transactionStore.clear()
        deductionStore.clear()
        syncQueue.clear()
        batchStore["b1"] = freshBatch()
        vaccineStore["vac-1"] = VaccineEntity(
            id = "vac-1", type = "MANDATORY", brandName = "Fluarix",
            companyName = "GSK", mrp = 750.0, netRate = 500.0
        )

        val vaccineDao = fake(VaccineDao::class.java) { name, args ->
            when (name) {
                "getBatchById" -> batchStore[args!![0] as String]
                "getVaccineById" -> vaccineStore[args!![0] as String]
                "getBatchByVaccineAndNumber" -> {
                    val vaccineId = args!![0] as String
                    val batchNumber = args[1] as String
                    batchStore.values.firstOrNull {
                        it.vaccineId == vaccineId && it.batchNumber.equals(batchNumber, ignoreCase = true)
                    }
                }
                "insertBatch" -> {
                    val entity = args!![0] as VaccineBatchEntity
                    batchStore[entity.batchId] = entity
                    null
                }
                "updateBatch" -> {
                    val entity = args!![0] as VaccineBatchEntity
                    batchStore[entity.batchId] = entity
                    null
                }
                "updateVaccine" -> {
                    val entity = args!![0] as VaccineEntity
                    vaccineStore[entity.id] = entity
                    null
                }
                "insertTransaction" -> {
                    transactionStore.add(args!![0] as InventoryTransactionEntity)
                    null
                }
                else -> null
            }
        }
        val deductionDao = fake(InventoryDeductionDao::class.java) { name, args ->
            when (name) {
                "getForVaccination" -> deductionStore.filter { it.vaccinationId == args!![0] as String }
                "insert" -> {
                    deductionStore.add(args!![0] as InventoryDeductionEntity)
                    null
                }
                "deleteForVaccination" -> {
                    deductionStore.removeAll { it.vaccinationId == args!![0] as String }
                    null
                }
                else -> null
            }
        }
        val syncRepo = fake(com.neochildclinic.domain.repository.SyncRepository::class.java) { name, args ->
            if (name == "enqueue") syncQueue.add("${args!![0]}:${args[2]}")
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
        val auditLogger = AuditLogger(sessionManager, object : AuditLogWriter {
            override suspend fun recordLog(auditLog: AuditLog, transactionGroupId: String?) = Unit
        })

        repository = InventoryRepositoryImpl(
            transactionRunner = runner,
            syncQueueDao = fake(SyncQueueDao::class.java) { _, _ -> null },
            vaccineDao = vaccineDao,
            inventoryDeductionDao = deductionDao,
            postgrest = client.postgrest,
            inventoryRemoteDataSource = fake(InventoryRemoteDataSource::class.java) { _, _ -> null },
            syncRepository = syncRepo,
            auditLogger = auditLogger,
            settingsManager = NotificationSettingsManager(TestContext()),
            sessionManager = sessionManager,
            context = TestContext()
        )
        engine = VaccinationEditEngine(
            transactionRunner = runner,
            vaccinationRepository = FakeVaccinationRepository(runner),
            inventoryRepository = repository,
            financeRepository = NoopFinanceRepository(),
            reminderRepository = FakeReminderRepository(runner)
        )
    }

    private suspend fun deduct(quantity: Int) = repository.deductStockFromBatch(
        batchId = "b1",
        quantity = quantity,
        user = "nurse",
        transactionType = InventoryTransactionType.VACCINATION,
        visitId = "visit-1",
        patientId = "p-1",
        notes = null,
        givenDate = "2026-05-15",
        transactionGroupId = null
    )

    private fun item(quantity: Int) = VaccinationItem(
        id = "i-b1", vaccinationId = "visit-1", vaccineId = "vac-1",
        vaccineName = "Fluarix", batchId = "b1", quantity = quantity
    )

    /** QA-07-style guard: the second deduction must read the first one's new stock level,
     * not a stale snapshot, so both the batch and the ledger end up consistent. */
    @Test
    fun `two deductions on the same batch keep stock and ledger in sync`() = runBlocking {
        deduct(2)
        deduct(3)

        assertEquals(5, batchStore["b1"]!!.remainingQuantity)
        assertEquals(5, batchStore["b1"]!!.usedQuantity)
        assertEquals(2, transactionStore.size)
        assertEquals(listOf(10, 8), transactionStore.map { it.previousQuantity })
        assertEquals(listOf(8, 5), transactionStore.map { it.currentQuantity })
        assertEquals(listOf(-2, -3), transactionStore.map { it.quantity })
    }

    /** CL-02: a batch already on the visit gets no expiry bypass. Expiry is always judged
     * against the given date: expired-before-visit blocks (FAILED ledger, no stock moved),
     * while expired-today-but-valid-on-visit succeeds (COMPLETED ledger, stock deducted). */
    @Test
    fun `expired before visit blocks edit but expired today valid on visit succeeds`() = runBlocking {
        // expiry 2026-06-01; today (test run) is after it; visit date is before it.
        val validOnVisit = Vaccination(id = "visit-1", patientId = "p-1", dateGiven = "2026-05-15", items = listOf(item(1)))
        engine.execute(validOnVisit, validOnVisit.copy(items = listOf(item(2))), "nurse", emptyList())

        assertEquals("A batch valid on the visit date must still be deductible even if expired today",
            9, batchStore["b1"]!!.remainingQuantity)
        assertEquals(1, deductionStore.size)
        assertEquals("COMPLETED", deductionStore.single().status)

        // Same batch, same quantity bump, but the visit date falls AFTER expiry.
        batchStore["b1"] = freshBatch()
        deductionStore.clear()
        transactionStore.clear()

        val expiredOnVisit = validOnVisit.copy(dateGiven = "2026-07-01")
        engine.execute(expiredOnVisit, expiredOnVisit.copy(items = listOf(item(2))), "nurse", emptyList())

        assertEquals("A batch expired before the visit date must not be deducted",
            10, batchStore["b1"]!!.remainingQuantity)
        assertTrue("Ledger must record the blocked deduction", deductionStore.isNotEmpty())
        assertEquals("FAILED", deductionStore.single().status)
        assertTrue("Block reason must mention expiry", deductionStore.single().errorMessage!!.contains("expired"))
    }

    private fun stockEntry(
        number: String,
        qty: Int,
        expiry: String = "2026-06-01",
        purchaseCost: Double = 500.0,
        sellingPrice: Double = 750.0
    ) = VaccineBatch(
        batchId = "new-${System.nanoTime()}", vaccineId = "vac-1", batchNumber = number,
        purchaseDate = "2026-05-01", expiryDate = expiry,
        purchaseQuantity = qty, remainingQuantity = qty,
        purchaseCost = purchaseCost, sellingPrice = sellingPrice
    )

    @Test
    fun `addStockBatch tops up an existing batch and writes one purchase row`() = runBlocking {
        repository.addStockBatch(mapOf("vac-1" to listOf(stockEntry(" b-fix-1 ", 5))), "owner")

        assertEquals(setOf("b1"), batchStore.keys)
        assertEquals(15, batchStore["b1"]!!.purchaseQuantity)
        assertEquals(15, batchStore["b1"]!!.remainingQuantity)
        val txn = transactionStore.single()
        assertEquals("PURCHASE", txn.transactionType)
        assertEquals(5, txn.quantity)
        assertEquals(10, txn.previousQuantity)
        assertEquals(15, txn.currentQuantity)
        assertEquals(listOf("BATCH:UPDATE", "INVENTORY_TRANSACTION:CREATE"), syncQueue)
    }

    @Test
    fun `addStockBatch rejects a batch number whose expiry differs`() = runBlocking {
        val error = try {
            repository.addStockBatch(mapOf("vac-1" to listOf(stockEntry("B-FIX-1", 5, expiry = "2026-07-01"))), "owner")
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertTrue("Expected an expiry mismatch error, got $error", error?.message?.contains("2026-06-01") == true)
        assertEquals(10, batchStore["b1"]!!.remainingQuantity)
        assertTrue(transactionStore.isEmpty())
    }

    @Test
    fun `addStockBatch rejects a batch number whose price differs`() = runBlocking {
        val error = try {
            repository.addStockBatch(
                mapOf("vac-1" to listOf(stockEntry("b-fix-1", 5, sellingPrice = 800.0))),
                "owner"
            )
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertTrue("Expected a price mismatch error, got $error", error?.message?.contains("750.0") == true)
        assertEquals(10, batchStore["b1"]!!.remainingQuantity)
        assertTrue(transactionStore.isEmpty())
    }

    @Test
    fun `addStockBatch still inserts a new batch number`() = runBlocking {
        repository.addStockBatch(mapOf("vac-1" to listOf(stockEntry("NEW-1", 4))), "owner")

        assertEquals(2, batchStore.size)
        assertEquals(4, batchStore.values.single { it.batchNumber == "NEW-1" }.remainingQuantity)
        assertTrue(syncQueue.contains("BATCH:CREATE"))
    }
}
