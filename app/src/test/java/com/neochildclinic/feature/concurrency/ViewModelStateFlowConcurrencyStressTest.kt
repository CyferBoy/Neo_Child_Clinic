package com.neochildclinic.feature.concurrency

import com.neochildclinic.domain.model.AuditLog
import com.neochildclinic.domain.repository.AuditLogRepository
import com.neochildclinic.feature.audit.presentation.PatientAuditLogPager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ViewModelStateFlowConcurrencyStressTest {

    @Test
    fun patientAuditLogPagerHandles100ConcurrentLoadMoreCallsGracefully() = runBlocking {
        val pageCallCount = AtomicInteger(0)
        val fakeRepo = object : AuditLogRepository {
            override suspend fun getPaged(patientId: String?, offset: Int, limit: Int): List<AuditLog> {
                pageCallCount.incrementAndGet()
                return (offset until offset + limit).map { i ->
                    AuditLog(
                        id = "log_$i",
                        timestamp = "2026-11-16T12:00:00Z",
                        user = "doctor@clinic.com",
                        module = "PATIENT",
                        entityType = "Patient",
                        entityId = patientId ?: "unknown",
                        action = "VIEW"
                    )
                }
            }
        }

        val testScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val pager = PatientAuditLogPager(fakeRepo, testScope)

        pager.load("patient-123")
        val firstPage = withTimeout(5000) {
            pager.state.first { !it.isLoading && it.logs.isNotEmpty() }
        }
        assertEquals(30, firstPage.logs.size)

        val jobs = (1..100).map { testScope.launch { pager.loadMore() } }
        jobs.joinAll()

        // Wait for the pager's own state to settle rather than guessing a wall-clock delay.
        withTimeout(5000) { pager.state.first { !it.isLoadingMore } }
        val finalState = pager.state.value

        assertNull("Error must be null", finalState.error)
        assertTrue("Logs must contain at least 30 items", finalState.logs.size >= 30)

        val ids = finalState.logs.map { it.id }
        assertEquals(
            "Distinct ID count must equal logs size (no corrupted duplicates)",
            ids.distinct().size,
            ids.size
        )

        testScope.cancel()
    }
}
