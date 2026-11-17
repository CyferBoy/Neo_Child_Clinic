package com.neochildclinic.feature.sync

import androidx.work.ListenableWorker
import com.neochildclinic.app.work.SyncFailureAction
import com.neochildclinic.app.work.classifySyncWork
import com.neochildclinic.app.work.nextFailureCount
import com.neochildclinic.app.work.shouldRaiseSyncAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Behaviour of the sync worker's outcome policy. The policy is a set of pure functions in
 * [com.neochildclinic.app.work] so it can be verified without Unsafe/reflection into a
 * Hilt worker whose constructor needs real WorkManager + Android notification objects
 * (unavailable to JVM unit tests).
 */
class SyncWorkerTest {

    @Test
    fun `permanent failure is classified as failure and counted`() {
        val outcome = classifySyncWork(permanentFailure = true, hasFailedItems = false, error = null)

        assertEquals(ListenableWorker.Result.failure(), outcome.result)
        assertEquals(SyncFailureAction.INCREMENT, outcome.failureAction)
        assertEquals("Some changes could not be synced", outcome.alertMessage)
    }

    @Test
    fun `successful sync resets failure count but still returns success`() {
        val outcome = classifySyncWork(permanentFailure = false, hasFailedItems = false, error = null)

        assertEquals(ListenableWorker.Result.success(), outcome.result)
        assertEquals(SyncFailureAction.RESET, outcome.failureAction)
        assertEquals(0, nextFailureCount(4, outcome.failureAction))
    }

    @Test
    fun `hasFailedItems returns success but still counts a failure`() {
        val outcome = classifySyncWork(permanentFailure = false, hasFailedItems = true, error = null)

        assertEquals(ListenableWorker.Result.success(), outcome.result)
        assertEquals(SyncFailureAction.INCREMENT, outcome.failureAction)
        assertEquals(2, nextFailureCount(1, outcome.failureAction))
    }

    @Test
    fun `general exception is classified as retry with the error message`() {
        val outcome = classifySyncWork(false, false, IOException("Socket timeout"))

        assertEquals(ListenableWorker.Result.retry(), outcome.result)
        assertEquals(SyncFailureAction.INCREMENT, outcome.failureAction)
        assertEquals("Socket timeout", outcome.alertMessage)
    }

    @Test
    fun `a failure count grows only on the increment action`() {
        assertEquals(1, nextFailureCount(0, SyncFailureAction.INCREMENT))
        assertEquals(5, nextFailureCount(4, SyncFailureAction.INCREMENT))
        assertEquals(0, nextFailureCount(4, SyncFailureAction.RESET))
    }

    @Test
    fun `sync alert fires at five consecutive failures only when alerts are enabled`() {
        assertFalse(shouldRaiseSyncAlert(4, syncAlertsEnabled = true))
        assertTrue(shouldRaiseSyncAlert(5, syncAlertsEnabled = true))
        assertTrue(shouldRaiseSyncAlert(6, syncAlertsEnabled = true))
        assertFalse("Alerts disabled in settings must suppress the notification", shouldRaiseSyncAlert(5, syncAlertsEnabled = false))
    }
}
