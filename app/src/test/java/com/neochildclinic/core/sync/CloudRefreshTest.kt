package com.neochildclinic.core.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every pull path funnels through cloudRefresh. A swallowed session failure made an expired or
 * deactivated staff session indistinguishable from "no data": the path caught the exception,
 * returned an empty list, and the screen rendered stale local numbers as if they were current.
 * The wrapper must always surface an authentication failure, while still swallowing an ordinary
 * offline failure unless the caller asked otherwise.
 *
 * RestException cannot be constructed in a JVM test (it needs a real ktor HttpResponse), so
 * these use the message-based fallback - a genuine path for non-REST failures. The
 * status-code-based branch is covered by SyncErrorClassificationTest.
 */
class CloudRefreshTest {

    @Test
    fun `an expired session is rethrown even though the caller asked to swallow errors`() = runBlocking {
        var caught: Exception? = null
        try {
            cloudRefresh("Test", rethrow = false) {
                throw RuntimeException("JWT expired")
            }
        } catch (e: Exception) {
            caught = e
        }
        assertNotNull("an expired session must reach the user, not look like empty data", caught)
    }

    @Test
    fun `an rls rejection is not treated as a session failure`() {
        // 42501 is a data-level authorization failure, not an expired token: mistaking it for
        // one would turn every RLS error into a re-auth prompt.
        assertFalse(isSessionFailure(RuntimeException("new row violates row-level security policy")))
        assertFalse(isSessionFailure(RuntimeException("permission denied for table patients")))
    }

    @Test
    fun `an ordinary network failure is not a session failure`() {
        assertFalse(isSessionFailure(java.io.IOException("no network")))
        assertFalse(isSessionFailure(java.io.IOException("offline")))
    }

    @Test
    fun `an offline failure is swallowed when the caller does not rethrow`() = runBlocking {
        var threw = false
        try {
            cloudRefresh("Test", rethrow = false) { throw java.io.IOException("offline") }
        } catch (e: Exception) {
            threw = true
        }
        assertFalse("offline is not worth an error screen on every refresh", threw)
    }

    @Test
    fun `a non-auth failure is still rethrown when the caller asks for it`() = runBlocking {
        var threw = false
        try {
            cloudRefresh("Test", rethrow = true) { throw java.io.IOException("offline") }
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `a successful refresh does not throw`() = runBlocking {
        var ran = false
        cloudRefresh("Test", rethrow = true) { ran = true }
        assertTrue(ran)
    }
}