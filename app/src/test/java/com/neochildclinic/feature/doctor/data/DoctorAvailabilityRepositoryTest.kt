package com.neochildclinic.feature.doctor.data

import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.data.local.dao.DoctorAvailabilityDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import sun.misc.Unsafe

class DoctorAvailabilityRepositoryTest {

    private lateinit var repository: DoctorAvailabilityRepositoryImpl
    private var upsertedExceptionCount = 0

    @Before
    fun setUp() {
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as Unsafe
        repository = unsafe.allocateInstance(DoctorAvailabilityRepositoryImpl::class.java) as DoctorAvailabilityRepositoryImpl

        val daoProxy = Proxy.newProxyInstance(
            DoctorAvailabilityDao::class.java.classLoader,
            arrayOf(DoctorAvailabilityDao::class.java)
        ) { _, method, args ->
            if (method.name.startsWith("upsertException")) {
                upsertedExceptionCount++
            }
            null
        } as DoctorAvailabilityDao

        val syncRepoProxy = Proxy.newProxyInstance(
            SyncRepository::class.java.classLoader,
            arrayOf(SyncRepository::class.java)
        ) { _, _, _ -> null } as SyncRepository

        val auditLogDaoProxy = Proxy.newProxyInstance(
            com.neochildclinic.data.local.dao.AuditLogDao::class.java.classLoader,
            arrayOf(com.neochildclinic.data.local.dao.AuditLogDao::class.java)
        ) { _, _, _ -> null } as com.neochildclinic.data.local.dao.AuditLogDao

        val sessionManager = unsafe.allocateInstance(com.neochildclinic.core.security.SessionManager::class.java) as com.neochildclinic.core.security.SessionManager

        val auditLogger = unsafe.allocateInstance(AuditLogger::class.java) as AuditLogger
        setField(auditLogger, "scope", kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()))
        setField(auditLogger, "auditLogDao", auditLogDaoProxy)
        setField(auditLogger, "sessionManager", sessionManager)
        setField(auditLogger, "syncRepository", syncRepoProxy)

        setField(repository, "dao", daoProxy)
        setField(repository, "syncRepository", syncRepoProxy)
        setField(repository, "auditLogger", auditLogger)
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        error("Field $fieldName not found on ${target.javaClass}")
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
