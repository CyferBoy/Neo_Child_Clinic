package com.neochildclinic.feature.patient.domain.usecase

import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.core.logger.AuditLogWriter
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.core.security.UserDisplayNameProvider
import com.neochildclinic.domain.model.AuditLog
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Reminder
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.feature.patient.domain.MergePatientsUseCase
import com.neochildclinic.feature.patient.domain.repository.PatientRepository
import com.neochildclinic.feature.reminder.domain.repository.ReminderRepository
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import com.neochildclinic.feature.vaccination.domain.repository.VaccinationRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class MergePatientsUseCaseTest {

    private class TransactionalSimulatorRunner : TransactionRunner {
        var runCount = 0
            private set
        var commitCount = 0
            private set
        var rollbackCount = 0
            private set
        var onRollback: (() -> Unit)? = null

        override suspend fun <T> run(block: suspend () -> T): T {
            runCount++
            try {
                val result = block()
                commitCount++
                return result
            } catch (t: Throwable) {
                rollbackCount++
                onRollback?.invoke()
                throw t
            }
        }
    }

    data class EnqueuedItem(
        val entityName: String,
        val entityId: String,
        val operation: SyncOperation,
        val priority: SyncPriority,
        val transactionGroupId: String?
    )

    private lateinit var transactionRunner: TransactionalSimulatorRunner
    private lateinit var mergePatientsUseCase: MergePatientsUseCase

    // State tracking
    private val enqueuedSyncItems = mutableListOf<EnqueuedItem>()
    private val auditLogs = mutableListOf<AuditLog>()
    private val auditGroups = mutableListOf<String?>()
    private val transferredVaccinations = mutableListOf<Pair<String, String>>()
    private val transferredReminders = mutableListOf<Pair<String, String>>()
    private val transferredInventory = mutableListOf<Pair<String, String>>()
    private val reassignedLinkedRecords = mutableListOf<Triple<String, String, String?>>()
    private val deletedPatients = mutableListOf<Pair<String, String?>>()

    var shouldThrowOnTransferReminders = false

    @Before
    fun setUp() {
        transactionRunner = TransactionalSimulatorRunner()
        enqueuedSyncItems.clear()
        auditLogs.clear()
        auditGroups.clear()
        transferredVaccinations.clear()
        transferredReminders.clear()
        transferredInventory.clear()
        reassignedLinkedRecords.clear()
        deletedPatients.clear()
        shouldThrowOnTransferReminders = false

        val patientRepoProxy = Proxy.newProxyInstance(
            PatientRepository::class.java.classLoader,
            arrayOf(PatientRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "reassignPatientLinkedRecords" -> {
                    reassignedLinkedRecords.add(Triple(args[0] as String, args[1] as String, args[2] as? String))
                    null
                }
                "deletePatient" -> {
                    deletedPatients.add(Pair(args[0] as String, args[1] as? String))
                    null
                }
                else -> null
            }
        } as PatientRepository

        val vaccinationRepoProxy = Proxy.newProxyInstance(
            VaccinationRepository::class.java.classLoader,
            arrayOf(VaccinationRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getVaccinationsForPatient" -> {
                    val dupId = args[0] as String
                    flowOf(listOf(
                        Vaccination(id = "vacc-1", patientId = dupId),
                        Vaccination(id = "vacc-2", patientId = dupId)
                    ))
                }
                "transferVaccinations" -> {
                    transferredVaccinations.add(Pair(args[0] as String, args[1] as String))
                    null
                }
                else -> null
            }
        } as VaccinationRepository

        val reminderRepoProxy = Proxy.newProxyInstance(
            ReminderRepository::class.java.classLoader,
            arrayOf(ReminderRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getPatientReminders" -> {
                    val dupId = args[0] as String
                    flowOf(listOf(
                        Reminder(
                            id = "rem-1",
                            patientId = dupId,
                            originalVisitId = "v1",
                            vaccineName = "Polio",
                            dueDate = "2026-10-01",
                            status = "PENDING"
                        )
                    ))
                }
                "transferReminders" -> {
                    if (shouldThrowOnTransferReminders) {
                        throw IllegalStateException("Simulated database failure during transferReminders")
                    }
                    transferredReminders.add(Pair(args[0] as String, args[1] as String))
                    null
                }
                else -> null
            }
        } as ReminderRepository

        val inventoryRepoProxy = Proxy.newProxyInstance(
            InventoryRepository::class.java.classLoader,
            arrayOf(InventoryRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "transferPatientTransactions" -> {
                    transferredInventory.add(Pair(args[0] as String, args[1] as String))
                    null
                }
                else -> null
            }
        } as InventoryRepository

        val syncRepoProxy = Proxy.newProxyInstance(
            SyncRepository::class.java.classLoader,
            arrayOf(SyncRepository::class.java)
        ) { _, method, args ->
            when (method.name) {
                "enqueue" -> {
                    val name = args[0] as String
                    val id = args[1] as String
                    val op = args[2] as SyncOperation
                    val priority = args[3] as SyncPriority
                    val groupId = if (args.size > 4) args[4] as? String else null
                    enqueuedSyncItems.add(EnqueuedItem(name, id, op, priority, groupId))
                    null
                }
                else -> null
            }
        } as SyncRepository

        val auditWriter = object : AuditLogWriter {
            override suspend fun recordLog(auditLog: AuditLog, transactionGroupId: String?) {
                auditLogs.add(auditLog)
                auditGroups.add(transactionGroupId)
            }
        }

        val client = createSupabaseClient("https://dummy.supabase.co", "dummy_anon_key") {
            install(Postgrest)
            install(Auth) {
                minimalConfig()
            }
        }

        val sessionManager = SessionManager(
            auth = client.auth,
            userDisplayNameProvider = object : UserDisplayNameProvider {
                override suspend fun getUserDisplayName(userId: String): String = "Dr. Test"
            }
        )

        val auditLogger = AuditLogger(
            sessionManager = sessionManager,
            auditLogWriter = auditWriter
        )

        mergePatientsUseCase = MergePatientsUseCase(
            transactionRunner = transactionRunner,
            patientRepository = patientRepoProxy,
            vaccinationRepository = vaccinationRepoProxy,
            reminderRepository = reminderRepoProxy,
            inventoryRepository = inventoryRepoProxy,
            syncRepository = syncRepoProxy,
            auditLogger = auditLogger
        )
    }

    @Test
    fun `invoke with blank masterId returns immediately without running transaction`() = runBlocking {
        mergePatientsUseCase("", listOf("dup-1"))
        assertEquals(0, transactionRunner.runCount)
        assertEquals(0, transferredVaccinations.size)
    }

    @Test
    fun `invoke with empty duplicateIds returns immediately without running transaction`() = runBlocking {
        mergePatientsUseCase("master-1", emptyList())
        assertEquals(0, transactionRunner.runCount)
        assertEquals(0, transferredVaccinations.size)
    }

    @Test
    fun `invoke transfers all child records in single transaction with shared groupId`() = runBlocking {
        mergePatientsUseCase("master-1", listOf("dup-1"))

        assertEquals(1, transactionRunner.runCount)
        assertEquals(1, transactionRunner.commitCount)
        assertEquals(0, transactionRunner.rollbackCount)

        // 1. Verify child transfers
        assertEquals(listOf(Pair("dup-1", "master-1")), transferredVaccinations)
        assertEquals(listOf(Pair("dup-1", "master-1")), transferredReminders)
        assertEquals(listOf(Pair("dup-1", "master-1")), transferredInventory)

        // 2. Verify reassignPatientLinkedRecords received non-null groupId
        assertEquals(1, reassignedLinkedRecords.size)
        val (reassignedFrom, reassignedTo, sharedGroupId) = reassignedLinkedRecords[0]
        assertEquals("dup-1", reassignedFrom)
        assertEquals("master-1", reassignedTo)
        assertNotNull(sharedGroupId)

        // 3. Verify sync enqueue calls all share this single groupId
        assertTrue(enqueuedSyncItems.isNotEmpty())
        enqueuedSyncItems.forEach { item ->
            assertEquals(
                "Every queued mutation during merge must share the single transactionGroupId",
                sharedGroupId,
                item.transactionGroupId
            )
        }

        // 4. Verify specific enqueued entities
        val vaccinationEnqueues = enqueuedSyncItems.filter { it.entityName == "VACCINATION" }
        assertEquals(2, vaccinationEnqueues.size)
        assertEquals(setOf("vacc-1", "vacc-2"), vaccinationEnqueues.map { it.entityId }.toSet())

        val reminderEnqueues = enqueuedSyncItems.filter { it.entityName == "REMINDERS" }
        assertEquals(1, reminderEnqueues.size)
        assertEquals("rem-1", reminderEnqueues[0].entityId)

        val masterPatientEnqueues = enqueuedSyncItems.filter { it.entityName == "PATIENT" }
        assertEquals(1, masterPatientEnqueues.size)
        assertEquals("master-1", masterPatientEnqueues[0].entityId)
        assertEquals(SyncOperation.UPDATE, masterPatientEnqueues[0].operation)

        // 5. Verify audit log was recorded with the shared groupId
        assertEquals(1, auditLogs.size)
        assertEquals("MERGE_PATIENT", auditLogs[0].action)
        assertEquals("master-1", auditLogs[0].patientId)
        assertEquals("Audit row must share the merge groupId", sharedGroupId, auditGroups.first())

        // 6. Verify duplicate patient deletion was called with shared groupId
        assertEquals(listOf(Pair("dup-1", sharedGroupId)), deletedPatients)
    }

    @Test
    fun `invoke rolls back completely when exception is thrown during child transfer`() = runBlocking {
        shouldThrowOnTransferReminders = true

        // Snapshot state for rollback simulation
        var rolledBack = false
        transactionRunner.onRollback = {
            rolledBack = true
            transferredVaccinations.clear()
            transferredReminders.clear()
            transferredInventory.clear()
            reassignedLinkedRecords.clear()
            enqueuedSyncItems.clear()
            deletedPatients.clear()
            auditLogs.clear()
        }

        try {
            mergePatientsUseCase("master-1", listOf("dup-1"))
            fail("Expected exception during transferReminders")
        } catch (e: IllegalStateException) {
            assertEquals("Simulated database failure during transferReminders", e.message)
        }

        // Verify transaction rolled back
        assertEquals(1, transactionRunner.runCount)
        assertEquals(0, transactionRunner.commitCount)
        assertEquals(1, transactionRunner.rollbackCount)
        assertTrue("onRollback handler must be executed on transaction failure", rolledBack)

        // State is completely clear: no partial commits or partial queue entries
        assertEquals(0, transferredVaccinations.size)
        assertEquals(0, deletedPatients.size)
        assertEquals(0, enqueuedSyncItems.size)
    }
}
