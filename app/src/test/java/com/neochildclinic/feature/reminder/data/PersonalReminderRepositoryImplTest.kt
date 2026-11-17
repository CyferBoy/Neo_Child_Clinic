package com.neochildclinic.feature.reminder.data

import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.core.security.UserDisplayNameProvider
import com.neochildclinic.data.local.dao.AuditLogDao
import com.neochildclinic.data.local.dao.PatientDao
import com.neochildclinic.data.local.dao.PersonalReminderDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.dao.VaccineDao
import com.neochildclinic.data.local.entity.AuditLogEntity
import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import com.neochildclinic.data.local.entity.VaccineEntity
import com.neochildclinic.domain.model.PersonalReminder
import com.neochildclinic.domain.model.PersonalReminderStatus
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.feature.personalreminder.data.PersonalReminderRepositoryImpl
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.net.InetSocketAddress

class PersonalReminderRepositoryImplTest {

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
        val priority: SyncPriority
    )

    private lateinit var repository: PersonalReminderRepositoryImpl
    private lateinit var transactionRunner: ImmediateTransactionRunner
    private var httpServer: HttpServer? = null
    private lateinit var auditLogWritten: CompletableDeferred<Unit>

    // In-memory test state
    private val localReminders = mutableMapOf<String, PersonalReminderEntity>()
    private val localPatients = mutableMapOf<String, PatientEntity>()
    private val localVaccines = mutableMapOf<String, VaccineEntity>()
    private val unsyncedIdsInQueue = mutableSetOf<String>()
    private val enqueuedSyncs = mutableListOf<EnqueuedItem>()
    private val auditLogs = mutableListOf<AuditLogEntity>()
    private val deletedReminderIds = mutableListOf<String>()

    // Responses from mock HTTP server for personal_vaccine_reminders
    private var mockRemoteRemindersJson = "[]"
    private var requestCount = 0

    @Before
    fun setUp() {
        localReminders.clear()
        localPatients.clear()
        localVaccines.clear()
        unsyncedIdsInQueue.clear()
        enqueuedSyncs.clear()
        auditLogs.clear()
        deletedReminderIds.clear()
        requestCount = 0
        transactionRunner = ImmediateTransactionRunner()
        auditLogWritten = CompletableDeferred()

        // 1. Setup in-memory HTTP server to mock Supabase PostgREST
        httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/rest/v1/personal_vaccine_reminders") { exchange ->
                requestCount++
                val responseBytes = if (requestCount == 1) {
                    mockRemoteRemindersJson.toByteArray(Charsets.UTF_8)
                } else {
                    "[]".toByteArray(Charsets.UTF_8)
                }
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, responseBytes.size.toLong())
                exchange.responseBody.use { it.write(responseBytes) }
            }
            start()
        }
        val serverPort = httpServer!!.address.port
        val client = createSupabaseClient("http://127.0.0.1:$serverPort", "dummy_key") {
            install(Postgrest)
            install(Auth) {
                minimalConfig()
            }
        }

        // 2. Setup DAOs via Proxy
        val reminderDaoProxy = Proxy.newProxyInstance(
            PersonalReminderDao::class.java.classLoader,
            arrayOf(PersonalReminderDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insert" -> {
                    val entity = args[0] as PersonalReminderEntity
                    localReminders[entity.id] = entity
                    null
                }
                "getById" -> {
                    val id = args[0] as String
                    localReminders[id]
                }
                "delete" -> {
                    val id = args[0] as String
                    localReminders.remove(id)
                    deletedReminderIds.add(id)
                    null
                }
                "getPersonalReminderIds" -> localReminders.keys.toList()
                "getSyncedPersonalReminderIds" -> localReminders.values.filter { it.isSynced }.map { it.id }
                else -> null
            }
        } as PersonalReminderDao

        val patientDaoProxy = Proxy.newProxyInstance(
            PatientDao::class.java.classLoader,
            arrayOf(PatientDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getPatientById" -> {
                    val id = args[0] as String
                    localPatients[id]
                }
                else -> null
            }
        } as PatientDao

        val vaccineDaoProxy = Proxy.newProxyInstance(
            VaccineDao::class.java.classLoader,
            arrayOf(VaccineDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getVaccineById" -> {
                    val id = args[0] as String
                    localVaccines[id]
                }
                else -> null
            }
        } as VaccineDao

        val syncQueueDaoProxy = Proxy.newProxyInstance(
            SyncQueueDao::class.java.classLoader,
            arrayOf(SyncQueueDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "unsyncedIdSet" -> unsyncedIdsInQueue.toSet()
                else -> null
            }
        } as SyncQueueDao

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
                    enqueuedSyncs.add(EnqueuedItem(entityName, entityId, op, priority))
                    null
                }
                else -> null
            }
        } as SyncRepository

        val auditLogDaoProxy = Proxy.newProxyInstance(
            AuditLogDao::class.java.classLoader,
            arrayOf(AuditLogDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertLog" -> {
                    synchronized(auditLogs) {
                        auditLogs.add(args[0] as AuditLogEntity)
                    }
                    auditLogWritten.complete(Unit)
                    null
                }
                else -> null
            }
        } as AuditLogDao

        val sessionManager = SessionManager(
            auth = client.auth,
            userDisplayNameProvider = object : UserDisplayNameProvider {
                override suspend fun getUserDisplayName(userId: String): String = "Dr. Tester"
            }
        )

        val auditLogger = AuditLogger(
            sessionManager = sessionManager,
            auditLogWriter = com.neochildclinic.feature.audit.data.AuditLogRepositoryImpl(
                postgrest = client.postgrest,
                auditLogDao = auditLogDaoProxy,
                syncRepository = syncRepoProxy
            )
        )

        repository = PersonalReminderRepositoryImpl(
            dao = reminderDaoProxy,
            patientDao = patientDaoProxy,
            vaccineDao = vaccineDaoProxy,
            transactionRunner = transactionRunner,
            syncRepository = syncRepoProxy,
            syncQueueDao = syncQueueDaoProxy,
            postgrest = client.postgrest,
            sessionManager = sessionManager,
            auditLogger = auditLogger
        )
    }

    @After
    fun tearDown() {
        httpServer?.stop(0)
    }

    // AuditLogger.log is fire-and-forget on its own scope; the proxy signals the write so
    // the test awaits the actual completion instead of polling the wall clock.
    private suspend fun awaitAuditLog(entityId: String): AuditLogEntity? {
        withTimeout(1000) { auditLogWritten.await() }
        return synchronized(auditLogs) { auditLogs.firstOrNull { it.entityId == entityId } }
    }

    @Test
    fun `createReminder persists entity with isSynced false and enqueues CREATE sync and logs audit`() = runBlocking {
        val reminder = PersonalReminder(
            id = "rem-1",
            patientName = "Test Baby",
            patientPhone = "9876543210",
            vaccineLabel = "BCG"
        )

        repository.createReminder(reminder)

        // 1. Verify entity persisted
        val saved = localReminders["rem-1"]
        assertNotNull(saved)
        assertEquals("Test Baby", saved!!.patientName)
        assertFalse("Created reminder must be un-synced initially", saved.isSynced)

        // 2. Verify Sync Queue
        val sync = enqueuedSyncs.firstOrNull { it.entityId == "rem-1" }
        assertNotNull(sync)
        assertEquals("PERSONAL_REMINDER", sync!!.entityName)
        assertEquals(SyncOperation.CREATE, sync.operation)
        assertEquals(SyncPriority.MEDIUM, sync.priority)

        // 3. Verify Audit Log
        val log = awaitAuditLog("rem-1")
        assertNotNull(log)
        assertEquals("CREATED", log!!.action)
        assertEquals("REMINDER", log.module)
    }

    @Test
    fun `updateReminder updates entity and enqueues UPDATE sync and logs audit`() = runBlocking {
        localReminders["rem-1"] = PersonalReminderEntity(
            id = "rem-1",
            patientName = "Old Name",
            patientPhone = "123",
            isSynced = true
        )

        val updated = PersonalReminder(
            id = "rem-1",
            patientName = "New Name",
            patientPhone = "123"
        )
        repository.updateReminder(updated)

        val saved = localReminders["rem-1"]
        assertEquals("New Name", saved?.patientName)
        assertFalse("Updated reminder must be marked isSynced = false", saved?.isSynced ?: true)

        val sync = enqueuedSyncs.firstOrNull { it.entityId == "rem-1" }
        assertNotNull(sync)
        assertEquals(SyncOperation.UPDATE, sync!!.operation)

        val log = awaitAuditLog("rem-1")
        assertNotNull(log)
        assertEquals("UPDATED", log!!.action)
    }

    @Test
    fun `markCompleted transitions status to COMPLETED and records completed timestamp and sync`() = runBlocking {
        localReminders["rem-1"] = PersonalReminderEntity(
            id = "rem-1",
            patientName = "Test Baby",
            patientPhone = "123",
            status = PersonalReminderStatus.PENDING.name,
            isSynced = true
        )

        repository.markCompleted("rem-1")

        val saved = localReminders["rem-1"]
        assertNotNull(saved)
        assertEquals(PersonalReminderStatus.COMPLETED.name, saved!!.status)
        assertNotNull(saved.completedAt)
        assertFalse(saved.isSynced)

        val sync = enqueuedSyncs.firstOrNull { it.entityId == "rem-1" }
        assertNotNull(sync)
        assertEquals(SyncOperation.UPDATE, sync!!.operation)

        val log = awaitAuditLog("rem-1")
        assertNotNull(log)
        assertEquals("COMPLETED", log!!.action)
    }

    @Test
    fun `cancel transitions status to CANCELLED and records cancelled timestamp and sync`() = runBlocking {
        localReminders["rem-1"] = PersonalReminderEntity(
            id = "rem-1",
            patientName = "Test Baby",
            patientPhone = "123",
            status = PersonalReminderStatus.PENDING.name,
            isSynced = true
        )

        repository.cancel("rem-1")

        val saved = localReminders["rem-1"]
        assertNotNull(saved)
        assertEquals(PersonalReminderStatus.CANCELLED.name, saved!!.status)
        assertNotNull(saved.cancelledAt)
        assertFalse(saved.isSynced)

        val sync = enqueuedSyncs.firstOrNull { it.entityId == "rem-1" }
        assertEquals(SyncOperation.UPDATE, sync?.operation)

        val log = awaitAuditLog("rem-1")
        assertEquals("CANCELLED", log?.action)
    }

    @Test
    fun `deleteReminder deletes from DAO and enqueues DELETE sync and logs audit`() = runBlocking {
        localReminders["rem-1"] = PersonalReminderEntity(
            id = "rem-1",
            patientName = "To Delete",
            patientPhone = "123",
            isSynced = true
        )

        repository.deleteReminder("rem-1")

        assertNull(localReminders["rem-1"])
        assertTrue(deletedReminderIds.contains("rem-1"))

        val sync = enqueuedSyncs.firstOrNull { it.entityId == "rem-1" }
        assertNotNull(sync)
        assertEquals(SyncOperation.DELETE, sync!!.operation)

        val log = awaitAuditLog("rem-1")
        assertNotNull(log)
        assertEquals("DELETED", log!!.action)
    }

    @Test
    fun `refresh foreign key validation skips reminder whose patient is missing locally`() = runBlocking {
        // Remote reminder points to patient "pat-missing", which does not exist in localPatients
        mockRemoteRemindersJson = """
            [
                {
                    "id": "rem-orphan",
                    "patient_id": "pat-missing",
                    "patient_name": "Ghost Child",
                    "patient_phone": "0000000000",
                    "status": "PENDING",
                    "is_synced": true
                }
            ]
        """.trimIndent()

        repository.refresh()

        // Foreign key guard must skip inserting this orphaned reminder
        assertNull("Reminder with non-existent patient must be skipped", localReminders["rem-orphan"])
    }

    @Test
    fun `refresh foreign key validation nulls missing vaccine reference to prevent FK violation`() = runBlocking {
        localPatients["pat-1"] = PatientEntity(
            id = "pat-1",
            name = "Valid Child",
            phone = "9999999999",
            dob = "2026-01-01",
            gender = "Male"
        )

        // Remote reminder points to "vac-deleted" which does NOT exist in localVaccines
        mockRemoteRemindersJson = """
            [
                {
                    "id": "rem-null-vac",
                    "patient_id": "pat-1",
                    "patient_name": "Valid Child",
                    "patient_phone": "9999999999",
                    "vaccine_id": "vac-deleted",
                    "vaccine_label": "MMR",
                    "status": "PENDING",
                    "is_synced": true
                }
            ]
        """.trimIndent()

        repository.refresh()

        val saved = localReminders["rem-null-vac"]
        assertNotNull("Reminder should be inserted", saved)
        assertNull("Missing vaccine ID must be nulled out to prevent Room FK violation", saved!!.vaccineId)
        assertEquals("MMR", saved.vaccineLabel)
        assertTrue(saved.isSynced)
    }

    @Test
    fun `refresh outbox protection preserves unsynced local changes from remote overwrite`() = runBlocking {
        // Local reminder has unsynced status
        localReminders["rem-unsynced"] = PersonalReminderEntity(
            id = "rem-unsynced",
            patientName = "Local Child Unsynced Edit",
            patientPhone = "1111111111",
            isSynced = false
        )
        // Also register in syncQueueDao pending set
        unsyncedIdsInQueue.add("rem-unsynced")

        // Remote returns an older or different state
        mockRemoteRemindersJson = """
            [
                {
                    "id": "rem-unsynced",
                    "patient_name": "Remote Child Overwrite Attempt",
                    "patient_phone": "2222222222",
                    "is_synced": true
                }
            ]
        """.trimIndent()

        repository.refresh()

        val current = localReminders["rem-unsynced"]
        assertEquals(
            "Outbox protection must prevent overwriting unpushed local changes",
            "Local Child Unsynced Edit",
            current?.patientName
        )
        assertFalse(current?.isSynced ?: true)
    }

    @Test
    fun `refresh prunes remotely deleted reminders when not pending in outbox`() = runBlocking {
        // Local has a synced reminder that the remote server no longer returns
        localReminders["rem-stale"] = PersonalReminderEntity(
            id = "rem-stale",
            patientName = "Deleted on Remote",
            patientPhone = "123",
            isSynced = true
        )
        // Local also has an active reminder that remains live on remote
        localReminders["rem-active"] = PersonalReminderEntity(
            id = "rem-active",
            patientName = "Still on Remote",
            patientPhone = "456",
            isSynced = true
        )
        // Remote set contains rem-active, but rem-stale is deleted/absent
        mockRemoteRemindersJson = """
            [
                {
                    "id": "rem-active",
                    "patient_name": "Still on Remote",
                    "patient_phone": "456",
                    "is_synced": true
                }
            ]
        """.trimIndent()

        repository.refresh()

        assertNull("Remotely deleted reminder must be pruned from local database", localReminders["rem-stale"])
        assertNotNull("Still active remote reminder must remain in local database", localReminders["rem-active"])
        assertTrue(deletedReminderIds.contains("rem-stale"))
    }
}
