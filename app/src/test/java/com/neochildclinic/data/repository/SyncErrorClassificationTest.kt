package com.neochildclinic.data.repository

import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncErrorClassificationTest {

    @Test
    fun `401 is always a session problem regardless of message`() {
        assertTrue(shouldRefreshSessionFor(401, "new row violates row-level security policy for table \"expenses\""))
        assertTrue(shouldRefreshSessionFor(401, "random server message"))
    }

    @Test
    fun `RLS rejection is never treated as a token problem`() {
        assertFalse(shouldRefreshSessionFor(400, "new row violates row-level security policy for table \"expenses\""))
        assertFalse(shouldRefreshSessionFor(400, "new row violates row-level security policy for table \"vaccination_todos\""))
        assertFalse(shouldRefreshSessionFor(null, "new row violates row-level security policy for table \"expenses\""))
    }

    @Test
    fun `non RLS data errors are never treated as a token problem`() {
        assertFalse(shouldRefreshSessionFor(400, "duplicate key value violates unique constraint"))
        assertFalse(shouldRefreshSessionFor(409, "example_fkey foreign key constraint failed"))
        assertFalse(shouldRefreshSessionFor(400, "null value in column \"patient_id\" of relation \"consultation_todos\" violates not-null constraint"))
    }

    @Test
    fun `legacy message fallback still catches jwt text when no status is available`() {
        assertTrue(shouldRefreshSessionFor(null, "JWT expired"))
        assertTrue(shouldRefreshSessionFor(null, "invalid JWT"))
        assertTrue(shouldRefreshSessionFor(null, "The JWT token has expired"))
    }

    @Test
    fun `network or unrelated errors are not session problems`() {
        assertFalse(shouldRefreshSessionFor(null, "Failed to connect to server"))
        assertFalse(shouldRefreshSessionFor(null, "unexpected end of stream"))
        assertFalse(shouldRefreshSessionFor(null, null))
        assertFalse(shouldRefreshSessionFor(500, null))
    }

    @Test
    fun `session resolves when status leaves Initializing`() = runBlocking<Unit> {
        val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val resolved = awaitSessionResolved(status, 200L)
        assertFalse("Should not resolve while still Initializing", resolved)
        assertEquals(SessionStatus.Initializing, status.value)
    }

    @Test
    fun `session resolves to logged out and still counts as resolved`() = runBlocking<Unit> {
        val status = MutableStateFlow<SessionStatus>(SessionStatus.NotAuthenticated())
        assertTrue(
            "A settled NotAuthenticated status resolves and must not be conflated with a timeout",
            awaitSessionResolved(status, 200L)
        )
    }

    @Test
    fun `session that settles shortly after start resolves`() = runBlocking<Unit> {
        val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val job = this.launch {
            delay(20L)
            status.value = SessionStatus.NotAuthenticated()
        }
        assertTrue("Late-settling session should resolve within the wait", awaitSessionResolved(status, 500L))
        job.cancel()
    }

    @Test
    fun `timeout is not conflated with a settled logged out state`() = runBlocking<Unit> {
        val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val started = System.currentTimeMillis()
        assertFalse("Stuck Initializing status must time out as unresolved", awaitSessionResolved(status, 150L))
        val elapsed = System.currentTimeMillis() - started
        assertTrue("Wait must be bounded by the timeout", elapsed in 100L..1_500L)
    }
}