package com.neochildclinic.feature.consultation.data

import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.core.logger.AuditLogWriter
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.core.security.UserDisplayNameProvider
import com.neochildclinic.data.local.dao.ConsultationDao
import com.neochildclinic.data.local.dao.FinanceDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.entity.ConsultationEntity
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.data.local.entity.VaccinationEntity
import com.neochildclinic.domain.model.AuditLog
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class ConsultationRepositoryImplTest {

    private class ImmediateTransactionRunner : TransactionRunner {
        var runCount = 0
            private set
        override suspend fun <T> run(block: suspend () -> T): T {
            runCount++
            return block()
        }
    }

    data class EnqueuedItem(
        val entityName: String,
        val entityId: String,
        val operation: SyncOperation,
        val priority: SyncPriority,
        val transactionGroupId: String?
    )

    private lateinit var repository: ConsultationRepositoryImpl
    private lateinit var transactionRunner: ImmediateTransactionRunner

    // In-memory test state
    private val localConsultations = mutableMapOf<String, ConsultationEntity>()
    private val localVisits = mutableMapOf<String, VaccinationEntity>()
    private val localFinance = mutableMapOf<String, FinanceEntity>()
    private val enqueuedSyncs = mutableListOf<EnqueuedItem>()
    private val auditLogs = mutableListOf<AuditLog>()
    private val executionEventLog = mutableListOf<String>()

    @Before
    fun setUp() {
        localConsultations.clear()
        localVisits.clear()
        localFinance.clear()
        enqueuedSyncs.clear()
        auditLogs.clear()
        executionEventLog.clear()
        transactionRunner = ImmediateTransactionRunner()

        val consultationDaoProxy = Proxy.newProxyInstance(
            ConsultationDao::class.java.classLoader,
            arrayOf(ConsultationDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertConsultation" -> {
                    val entity = args[0] as ConsultationEntity
                    localConsultations[entity.id] = entity
                    executionEventLog.add("INSERT_CONSULTATION:${entity.id}")
                    null
                }
                "getConsultationById" -> {
                    val id = args[0] as String
                    localConsultations[id]
                }
                "deleteConsultation" -> {
                    val id = args[0] as String
                    localConsultations.remove(id)
                    executionEventLog.add("DELETE_CONSULTATION:$id")
                    null
                }
                else -> null
            }
        } as ConsultationDao

        val vaccinationDaoProxy = Proxy.newProxyInstance(
            VaccinationDao::class.java.classLoader,
            arrayOf(VaccinationDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getVaccinationById" -> {
                    val id = args[0] as String
                    localVisits[id]
                }
                "upsertVaccination" -> {
                    val entity = args[0] as VaccinationEntity
                    localVisits[entity.id] = entity
                    executionEventLog.add("UPSERT_VISIT:${entity.id}")
                    null
                }
                "deleteVaccination" -> {
                    val id = args[0] as String
                    localVisits.remove(id)
                    executionEventLog.add("DELETE_VISIT:$id")
                    null
                }
                else -> null
            }
        } as VaccinationDao

        val financeDaoProxy = Proxy.newProxyInstance(
            FinanceDao::class.java.classLoader,
            arrayOf(FinanceDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getTransactionsByVisitId" -> {
                    val visitId = args[0] as String
                    localFinance.values.filter { it.visitId == visitId }
                }
                "deleteTransactionById" -> {
                    val id = args[0] as String
                    localFinance.remove(id)
                    executionEventLog.add("DELETE_FINANCE:$id")
                    null
                }
                else -> null
            }
        } as FinanceDao

        val syncRepoProxy = Proxy.newProxyInstance(
            SyncRepository::class.java.classLoader,
            arrayOf(SyncRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "enqueue" -> {
                    val entityName = args[0] as String
                    val entityId = args[1] as String
                    val op = args[2] as SyncOperation
                    val priority = args[3] as SyncPriority
                    val groupId = if (args.size > 4) args[4] as? String else null
                    val item = EnqueuedItem(entityName, entityId, op, priority, groupId)
                    enqueuedSyncs.add(item)
                    executionEventLog.add("SYNC_ENQUEUE:${item.entityName}:${item.entityId}:${item.operation}")
                    null
                }
                else -> null
            }
        } as SyncRepository

        val syncQueueDaoProxy = Proxy.newProxyInstance(
            SyncQueueDao::class.java.classLoader,
            arrayOf(SyncQueueDao::class.java)
        ) { _, _, _ -> null } as SyncQueueDao

        val auditWriter = object : AuditLogWriter {
            override suspend fun recordLog(auditLog: AuditLog, transactionGroupId: String?) {
                auditLogs.add(auditLog)
            }
        }

        val client = createSupabaseClient("https://dummy.supabase.co", "dummy_key") {
            install(Postgrest)
            install(Auth) {
                minimalConfig()
            }
        }

        val sessionManager = SessionManager(
            auth = client.auth,
            userDisplayNameProvider = object : UserDisplayNameProvider {
                override suspend fun getUserDisplayName(userId: String): String = "Dr. Pediatrician"
            }
        )

        val auditLogger = AuditLogger(
            sessionManager = sessionManager,
            auditLogWriter = auditWriter
        )

        repository = ConsultationRepositoryImpl(
            consultationDao = consultationDaoProxy,
            vaccinationDao = vaccinationDaoProxy,
            financeDao = financeDaoProxy,
            transactionRunner = transactionRunner,
            syncRepository = syncRepoProxy,
            syncQueueDao = syncQueueDaoProxy,
            postgrest = client.postgrest,
            sessionManager = sessionManager,
            auditLogger = auditLogger
        )
    }

    @Test
    fun `updateConsultation throws IllegalArgumentException when consultation not found`() = runBlocking {
        val nonExistent = Consultation(id = "missing-c", patientId = "p1")
        try {
            repository.updateConsultation(nonExistent)
            fail("Expected IllegalArgumentException when consultation does not exist")
        } catch (e: IllegalArgumentException) {
            assertEquals("Consultation not found", e.message)
        }
    }

    @Test
    fun `updateConsultation when visit header fields change synchronizes visit and enqueues VISIT update`() = runBlocking {
        // Setup existing visit and consultation
        localVisits["v1"] = VaccinationEntity(
            id = "v1",
            patientId = "p1",
            dateGiven = "2026-10-01",
            doctor = "Dr. Smith",
            doctorId = "doc-1",
            notes = "Initial problem",
            cashAmount = 500.0,
            onlineAmount = 0.0,
            totalPaid = 500.0,
            availabilitySlotId = "slot-1",
            isSynced = true
        )
        localConsultations["c1"] = ConsultationEntity(
            id = "c1",
            patientId = "p1",
            visitId = "v1",
            date = "2026-10-01",
            doctorName = "Dr. Smith",
            doctorId = "doc-1",
            problem = "Initial problem",
            cashAmount = 500.0,
            onlineAmount = 0.0,
            amount = 500.0,
            availabilitySlotId = "slot-1",
            isSynced = true
        )

        // Change doctor name and fee amount in consultation update
        val updatedConsultation = Consultation(
            id = "c1",
            patientId = "p1",
            visitId = "v1",
            date = "2026-10-01",
            doctorName = "Dr. Jones", // Changed!
            doctorId = "doc-2",     // Changed!
            problem = "Initial problem",
            cashAmount = 700.0,      // Changed!
            onlineAmount = 0.0,
            amount = 700.0,          // Changed!
            availabilitySlotId = "slot-1"
        )

        val txGroupId = "test-group-123"
        repository.updateConsultation(updatedConsultation, txGroupId)

        // 1. Verify transaction runner ran
        assertEquals(1, transactionRunner.runCount)

        // 2. Verify visit header was synchronized
        val updatedVisit = localVisits["v1"]
        assertNotNull(updatedVisit)
        assertEquals("Dr. Jones", updatedVisit!!.doctor)
        assertEquals("doc-2", updatedVisit.doctorId)
        assertEquals(700.0, updatedVisit.cashAmount, 0.001)
        assertEquals(700.0, updatedVisit.totalPaid, 0.001)
        assertFalse(updatedVisit.isSynced)

        // 3. Verify VISIT sync was enqueued with high priority and transactionGroupId
        val visitSync = enqueuedSyncs.firstOrNull { it.entityName == "VISIT" && it.entityId == "v1" }
        assertNotNull("VISIT sync must be enqueued when mirrored fields change", visitSync)
        assertEquals(SyncOperation.UPDATE, visitSync!!.operation)
        assertEquals(SyncPriority.HIGH, visitSync.priority)
        assertEquals(txGroupId, visitSync.transactionGroupId)

        // 4. Verify CONSULTATION sync was enqueued with medium priority and transactionGroupId
        val consultationSync = enqueuedSyncs.firstOrNull { it.entityName == "CONSULTATION" && it.entityId == "c1" }
        assertNotNull(consultationSync)
        assertEquals(SyncOperation.UPDATE, consultationSync!!.operation)
        assertEquals(SyncPriority.MEDIUM, consultationSync.priority)
        assertEquals(txGroupId, consultationSync.transactionGroupId)

        // 5. Verify audit log was recorded
        val log = auditLogs.firstOrNull { it.entityId == "c1" }
        assertNotNull(log)
        assertEquals("CONSULTATION_UPDATED", log!!.action)
    }

    @Test
    fun `updateConsultation when visit header fields are unchanged does NOT sync visit or enqueue VISIT update`() = runBlocking {
        localVisits["v1"] = VaccinationEntity(
            id = "v1",
            patientId = "p1",
            dateGiven = "2026-10-01",
            doctor = "Dr. Smith",
            doctorId = "doc-1",
            notes = "Problem unchanged",
            cashAmount = 500.0,
            onlineAmount = 0.0,
            totalPaid = 500.0,
            availabilitySlotId = "slot-1",
            isSynced = true
        )
        localConsultations["c1"] = ConsultationEntity(
            id = "c1",
            patientId = "p1",
            visitId = "v1",
            date = "2026-10-01",
            doctorName = "Dr. Smith",
            doctorId = "doc-1",
            problem = "Problem unchanged",
            cashAmount = 500.0,
            onlineAmount = 0.0,
            amount = 500.0,
            availabilitySlotId = "slot-1",
            notes = "Mild fever", // Only consultation-specific notes changed
            isSynced = true
        )

        // Update consultation without modifying any mirrored fields
        val updatedConsultation = Consultation(
            id = "c1",
            patientId = "p1",
            visitId = "v1",
            date = "2026-10-01",
            doctorName = "Dr. Smith",
            doctorId = "doc-1",
            problem = "Problem unchanged",
            cashAmount = 500.0,
            onlineAmount = 0.0,
            amount = 500.0,
            availabilitySlotId = "slot-1",
            notes = "Fever resolved" // Consultation-specific field changed
        )

        repository.updateConsultation(updatedConsultation)

        // 1. Visit entity must NOT have been updated
        assertFalse(
            "UPSERT_VISIT must not be called when mirrored fields did not change",
            executionEventLog.any { it.startsWith("UPSERT_VISIT") }
        )

        // 2. VISIT sync must NOT have been enqueued
        val visitSync = enqueuedSyncs.firstOrNull { it.entityName == "VISIT" }
        assertNull("No VISIT sync should be enqueued for consultation-only change", visitSync)

        // 3. CONSULTATION sync MUST be enqueued
        val consultationSync = enqueuedSyncs.firstOrNull { it.entityName == "CONSULTATION" }
        assertNotNull(consultationSync)
        assertEquals(SyncOperation.UPDATE, consultationSync!!.operation)
    }

    @Test
    fun `deleteConsultation executes cascading delete in strict order consultation then finance then visit header`() = runBlocking {
        // Setup Consultation with linked visit and two finance transactions
        localConsultations["c1"] = ConsultationEntity(
            id = "c1",
            patientId = "p1",
            visitId = "v1",
            date = "2026-10-01",
            amount = 500.0
        )
        localVisits["v1"] = VaccinationEntity(
            id = "v1",
            patientId = "p1",
            dateGiven = "2026-10-01"
        )
        localFinance["f1"] = FinanceEntity(
            id = "f1",
            type = "INCOME",
            category = "CONSULTATION",
            amount = 500.0,
            paymentMethod = "CASH",
            recordedBy = "Dr. Tester",
            patientId = "p1",
            visitId = "v1"
        )
        localFinance["f2"] = FinanceEntity(
            id = "f2",
            type = "INCOME",
            category = "CONSULTATION",
            amount = 200.0,
            paymentMethod = "ONLINE",
            recordedBy = "Dr. Tester",
            patientId = "p1",
            visitId = "v1"
        )

        repository.deleteConsultation("c1")

        // 1. Verify all records deleted locally
        assertNull(localConsultations["c1"])
        assertNull(localFinance["f1"])
        assertNull(localFinance["f2"])
        assertNull(localVisits["v1"])

        // 2. Verify strict deletion and queue order:
        // Order must be: Consultation (child) -> Finance (children) -> Visit (mother header)
        val deleteEvents = executionEventLog.filter { it.startsWith("DELETE_") }
        assertEquals(
            listOf(
                "DELETE_CONSULTATION:c1",
                "DELETE_FINANCE:f1",
                "DELETE_FINANCE:f2",
                "DELETE_VISIT:v1"
            ),
            deleteEvents
        )

        // 3. Verify sync enqueues occurred in the exact same order
        val syncEntities = enqueuedSyncs.filter { it.operation == SyncOperation.DELETE }.map { "${it.entityName}:${it.entityId}" }
        assertEquals(
            listOf(
                "CONSULTATION:c1",
                "FINANCE:f1",
                "FINANCE:f2",
                "VISIT:v1"
            ),
            syncEntities
        )

        // 4. Verify audit log was created
        val log = auditLogs.firstOrNull { it.entityId == "c1" }
        assertNotNull(log)
        assertEquals("DELETED", log!!.action)
        assertEquals("CONSULTATION", log.entityType)
    }
}
