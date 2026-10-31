package com.neochildclinic.core.sync

import android.util.Log
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared wrapper for repository pull-from-Cloud refresh: run [block] on IO,
 * log failures, optionally rethrow (for callers that surface errors to UI).
 *
 * A session/expired-token failure is ALWAYS rethrown, even when the caller asked to swallow
 * errors. Swallowing it made a deactivated or expired staff session look like "no data":
 * every pull path caught the exception, returned an empty list, and the screen rendered stale
 * local numbers as if they were current. A transient network failure is still swallowed by
 * default, because showing an error for "offline" on every screen would be noise.
 */
internal suspend fun cloudRefresh(tag: String, rethrow: Boolean = false, block: suspend () -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Refresh failed", e)
            if (isSessionFailure(e)) {
                Log.e(tag, "Refresh rejected as an authentication failure - surfacing instead of showing stale data", e)
                throw e
            }
            if (rethrow) throw e
        }
    }
}

/** True when a pull failed because the session/token is gone, not because of the network. */
internal fun isSessionFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    repeat(8) {
        val t = current
        if (t is RestException) {
            return shouldRefreshSessionFor(t.statusCode, t.message)
        }
        current = t?.cause
    }
    return shouldRefreshSessionFor(null, error.message)
}