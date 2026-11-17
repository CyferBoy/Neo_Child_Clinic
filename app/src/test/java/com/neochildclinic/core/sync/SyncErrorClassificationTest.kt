package com.neochildclinic.core.sync

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
    fun `stuck Initializing session times out as unresolved`() = runBlocking<Unit> {
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
        val settle = CompletableDeferred<Unit>()
        val settleJob = launch {
            settle.await()
            status.value = SessionStatus.NotAuthenticated()
        }
        val resolveJob = async { awaitSessionResolved(status, 500L) }
        yield() // let the waiter start before the status settles
        settle.complete(Unit)

        assertTrue("Late-settling session should resolve within the wait", resolveJob.await())
        settleJob.join()
    }

    // ---- Auth prerequisite for background sync (session readiness) ----

    @Test
    fun `access token well within grace period needs no manual refresh`() {
        val now = System.currentTimeMillis()
        assertTrue("A token valid for another hour must be used as-is", isSessionTokenUsable(now + 3_600_000L, now, 60_000L))
    }

    @Test
    fun `access token at or under the grace threshold must defer`() {
        val now = System.currentTimeMillis()
        assertFalse("Exactly at the grace boundary is not comfortably usable", isSessionTokenUsable(now + 60_000L, now, 60_000L))
        assertFalse(isSessionTokenUsable(now, now, 60_000L))
        assertFalse("Expired tokens must refresh", isSessionTokenUsable(now - 60_000L, now, 60_000L))
    }

    @Test
    fun `failed refresh with a still usable authenticated session can proceed`() {
        val now = System.currentTimeMillis()
        val session = UserSession(
            accessToken = "access", refreshToken = "refresh", expiresIn = 3_600L, tokenType = "bearer"
        )
        assertEquals(
            "A concurrent SDK refresh may already have installed a fresh session",
            SessionReadiness.USABLE,
            classifySessionReadinessAfterFailedRefresh(SessionStatus.Authenticated(session), now, 60_000L)
        )
    }

    @Test
    fun `failed refresh with an expiring authenticated session is deferred`() {
        val now = System.currentTimeMillis()
        val session = UserSession(
            accessToken = "access", refreshToken = "refresh", expiresIn = 1L, tokenType = "bearer"
        )
        assertEquals(
            SessionReadiness.RETRY_LATER,
            classifySessionReadinessAfterFailedRefresh(SessionStatus.Authenticated(session), now, 60_000L)
        )
    }

    @Test
    fun `failed refresh while SDK still restoring or in RefreshFailure defers`() {
        val now = System.currentTimeMillis()
        assertEquals(
            SessionReadiness.RETRY_LATER,
            classifySessionReadinessAfterFailedRefresh(SessionStatus.Initializing, now, 60_000L)
        )
        assertEquals(
            SessionReadiness.RETRY_LATER,
            classifySessionReadinessAfterFailedRefresh(
                SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IllegalStateException("unreachable"))),
                now,
                60_000L
            )
        )
    }

    @Test
    fun `failed refresh with logged out status means genuinely logged out`() {
        val now = System.currentTimeMillis()
        assertEquals(
            SessionReadiness.LOGGED_OUT,
            classifySessionReadinessAfterFailedRefresh(SessionStatus.NotAuthenticated(), now, 60_000L)
        )
    }
}