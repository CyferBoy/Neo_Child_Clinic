package com.neochildclinic.feature.doctor.data

import com.neochildclinic.core.logger.AuditLogWriter
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.core.security.UserDisplayNameProvider
import com.neochildclinic.data.local.dao.DoctorAvailabilityDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.domain.model.AuditLog
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class DoctorAvailabilityRepositoryTest {

    private lateinit var repository: DoctorAvailabilityRepositoryImpl
    private var upsertedExceptionCount = 0

    @Before
    fun setUp() {
        val daoProxy = Proxy.newProxyInstance(
            DoctorAvailabilityDao::class.java.classLoader,
            arrayOf(DoctorAvailabilityDao::class.java)
        ) { _, method, _ ->
            if (method.name.startsWith("upsertException")) {
                upsertedExceptionCount++
            }
            null
        } as DoctorAvailabilityDao

        val syncRepoProxy = Proxy.newProxyInstance(
            SyncRepository::class.java.classLoader,
            arrayOf(SyncRepository::class.java)
        ) { _, _, _ -> null } as SyncRepository

        val syncQueueDaoProxy = Proxy.newProxyInstance(
            SyncQueueDao::class.java.classLoader,
            arrayOf(SyncQueueDao::class.java)
        ) { _, _, _ -> null } as SyncQueueDao

        val auditWriter = object : AuditLogWriter {
            override suspend fun recordLog(auditLog: AuditLog, transactionGroupId: String?) = Unit
        }

        val client = createSupabaseClient("https://dummy.supabase.co", "dummy_anon_key") {
            install(Postgrest)
            install(Auth) {
                minimalConfig()
            }
        }

        val userDisplayNameProvider = object : UserDisplayNameProvider {
            override suspend fun getUserDisplayName(userId: String): String = "Test User"
        }

        val sessionManager = SessionManager(
            auth = client.auth,
            userDisplayNameProvider = userDisplayNameProvider
        )

        val auditLogger = AuditLogger(
            sessionManager = sessionManager,
            auditLogWriter = auditWriter
        )

        repository = DoctorAvailabilityRepositoryImpl(
            dao = daoProxy,
            postgrest = client.postgrest,
            syncRepository = syncRepoProxy,
            syncQueueDao = syncQueueDaoProxy,
            auditLogger = auditLogger
        )
    }

    @Test
    fun `addException throws when startMinute is not strictly before endMinute`() = runBlocking {
        val exception = DoctorSlotException(
            doctorId = "doc-1",
            exceptionDate = "2026-10-12",
            startMinute = 600,
            endMinute = 500
        )
        try {
            repository.addException(exception, "admin")
            fail("Expected IllegalArgumentException for startMinute >= endMinute")
        } catch (e: IllegalArgumentException) {
            assertEquals("startMinute must be strictly before endMinute", e.message)
        }
    }

    @Test
    fun `addException throws when startMinute equals endMinute`() = runBlocking {
        val exception = DoctorSlotException(
            doctorId = "doc-1",
            exceptionDate = "2026-10-12",
            startMinute = 600,
            endMinute = 600
        )
        try {
            repository.addException(exception, "admin")
            fail("Expected IllegalArgumentException for startMinute == endMinute")
        } catch (e: IllegalArgumentException) {
            assertEquals("startMinute must be strictly before endMinute", e.message)
        }
    }

    @Test
    fun `addException throws when startMinute is negative`() = runBlocking {
        val exception = DoctorSlotException(
            doctorId = "doc-1",
            exceptionDate = "2026-10-12",
            startMinute = -10,
            endMinute = 600
        )
        try {
            repository.addException(exception, "admin")
            fail("Expected IllegalArgumentException for negative startMinute")
        } catch (e: IllegalArgumentException) {
            assertEquals("Minutes must be between 0 and 1440", e.message)
        }
    }

    @Test
    fun `addException throws when endMinute exceeds 1440`() = runBlocking {
        val exception = DoctorSlotException(
            doctorId = "doc-1",
            exceptionDate = "2026-10-12",
            startMinute = 600,
            endMinute = 1441
        )
        try {
            repository.addException(exception, "admin")
            fail("Expected IllegalArgumentException for endMinute > 1440")
        } catch (e: IllegalArgumentException) {
            assertEquals("Minutes must be between 0 and 1440", e.message)
        }
    }

    @Test
    fun `addException throws when only one minute boundary is provided`() = runBlocking {
        val exception = DoctorSlotException(
            doctorId = "doc-1",
            exceptionDate = "2026-10-12",
            startMinute = 600,
            endMinute = null
        )
        try {
            repository.addException(exception, "admin")
            fail("Expected IllegalArgumentException when endMinute is null")
        } catch (e: IllegalArgumentException) {
            assertEquals("Both startMinute and endMinute must be provided", e.message)
        }
    }

    @Test
    fun `addException accepts valid start and end minutes`() = runBlocking {
        val exception = DoctorSlotException(
            doctorId = "doc-1",
            exceptionDate = "2026-10-12",
            startMinute = 540,
            endMinute = 600
        )
        repository.addException(exception, "admin")
        assertEquals(1, upsertedExceptionCount)
    }

    @Test
    fun `addException accepts full day exception with null minutes`() = runBlocking {
        val exception = DoctorSlotException(
            doctorId = "doc-1",
            exceptionDate = "2026-10-12",
            startMinute = null,
            endMinute = null
        )
        repository.addException(exception, "admin")
        assertEquals(1, upsertedExceptionCount)
    }
}
