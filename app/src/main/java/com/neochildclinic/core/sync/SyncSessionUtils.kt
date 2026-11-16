package com.neochildclinic.core.sync

import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

// JWT) always counts, whatever the message. Otherwise we fall back to the message text for
// non-REST failures only. A genuine RLS rejection - PostgREST 400 with code "42501",
// "new row violates row-level security policy ..." - is a data-level authorization failure,
// NOT a token failure, so its status (400) wins and it is never treated as refreshable.
internal fun shouldRefreshSessionFor(statusCode: Int?, message: String?): Boolean {
    if (statusCode == 401) return true
    val text = message.orEmpty()
    return text.contains("jwt", ignoreCase = true) &&
        (text.contains("expired", ignoreCase = true) ||
            text.contains("invalid", ignoreCase = true) ||
            text.contains("token", ignoreCase = true))
}

// Classifies a sync upload failure as transient (worth retrying with backoff - the same
// request could well succeed later with nothing else changed) or permanent (will not
// succeed without a code/data fix, so retrying it is pointless and only delays surfacing
// the problem). Used by processNextItems' per-group catch to decide PENDING+retry vs
// FAILED (see "Deletion Failure and Retry Behavior" section D/E - this governs the queued
// DELETE/CREATE/UPDATE operations a vaccination deletion enqueues, same as every other
// entity's sync operations).
//
// Transient: connectivity-layer failures with no HTTP response at all (no network, DNS,
// connection reset/refused, a client-side request timeout - java.io.IOException and
// supabase-kt's own HttpRequestException both represent this), and HTTP responses that are
// explicitly about server-side or rate-limiting trouble rather than the request itself
// being wrong: 408 Request Timeout, 429 Too Many Requests, and 5xx (Supabase/Postgres
// temporarily unavailable).
//
// Permanent: any other RestException - a 4xx such as 400 (invalid payload/schema
// mismatch), 403/a PostgREST 42501 (RLS/permission denial that a retry can't fix), 404,
// 409 (conflict/duplicate key), or 422 (validation) means the request as sent cannot
// succeed, and resending it unchanged will fail identically every time.
//
// 401 is deliberately not special-cased here: it's already handled earlier in
// processNextItems via requiresSessionRefresh/handleSessionRefreshOn401, which retries the
// same item inline after a session refresh or defers the whole group via
// SessionAuthTransientException (never reaching this function). A 401 that does reach here
// means that path already gave up on it, so it falls through and is judged as any other
// RestException would be.
internal fun isTransientSyncError(error: Throwable): Boolean {
    var current: Throwable? = error
    repeat(8) {
        val t = current
        if (t is IOException || t is HttpRequestException) {
            return true
        }
        if (t is RestException) {
            return t.statusCode == 408 || t.statusCode == 429 || t.statusCode in 500..599
        }
        current = t?.cause
    }
    // Unrecognized shape (e.g. a local exception not wrapping a REST/IO failure at all) -
    // fall back to the message text, same spirit as the network-error check this replaces.
    val text = error.message.orEmpty()
    return text.contains("network", ignoreCase = true) ||
        text.contains("timeout", ignoreCase = true) ||
        text.contains("unavailable", ignoreCase = true)
}

// 429 alone. Kept separate from isTransientSyncError because a rate-limited item must be
// requeued WITHOUT incrementing retryCount - see classifyAndRecordError.
internal fun isRateLimitedSyncError(error: Throwable): Boolean {
    var current: Throwable? = error
    repeat(8) {
        val t = current
        if (t is RestException) return t.statusCode == 429
        current = t?.cause
    }
    return false
}

// Blocks until the SDK has left the Initializing status - i.e. it has restored a persisted
// session (autoLoadFromStorage) and/or finished any refresh-on-load, settling on
// Authenticated, NotAuthenticated or RefreshFailure - or until timeoutMs elapses.
// Returns false ONLY on timeout (session still resolving = transient): a settled
// NotAuthenticated status still returns true, so callers can tell "genuinely logged out"
// apart from "still restoring, retry shortly".
internal suspend fun awaitSessionResolved(
    sessionStatus: StateFlow<SessionStatus>,
    timeoutMs: Long
): Boolean =
    withTimeoutOrNull(timeoutMs) {
        sessionStatus.first { it !is SessionStatus.Initializing }
    } != null

// Outcome of the auth prerequisite check that every sync batch must clear before writing.
internal enum class SessionReadiness {
    /** An authenticated session exists and its access token is usable; proceed with sync. */
    USABLE,

    /** Auth isn't ready yet (SDK reload/refresh in progress, or a refresh temporarily
     * failed): proceed later via one quiet background retry. */
    RETRY_LATER,

    /** The SDK has settled on NotAuthenticated: the user is genuinely logged out - never
     * write to Supabase, and do not retry-loop. */
    LOGGED_OUT
}

// True when the access token remains valid for at least graceMillis from nowMillis.
// Deliberately skipping a refresh for a comfortably-valid token avoids consuming the same
// GoTrue refresh token twice when the SDK's own auto-refresh job is alive
// ("refresh_token_not_found" / "No refresh token found").
internal fun isSessionTokenUsable(expiresAtEpochMillis: Long, nowMillis: Long, graceMillis: Long): Boolean =
    expiresAtEpochMillis - nowMillis > graceMillis

// Classifies the outcome of a refresh that failed after it was attempted, purely from the
// SDK's re-inspected status. Preferring status.session keeps the decision aligned with
// what the SDK will actually attach to the next PostgREST request.
internal fun classifySessionReadinessAfterFailedRefresh(
    status: SessionStatus,
    nowMillis: Long,
    graceMillis: Long
): SessionReadiness = when (status) {
    is SessionStatus.NotAuthenticated -> SessionReadiness.LOGGED_OUT
    is SessionStatus.Initializing,
    is SessionStatus.RefreshFailure -> SessionReadiness.RETRY_LATER
    is SessionStatus.Authenticated ->
        if (isSessionTokenUsable(status.session.expiresAt.toEpochMilliseconds(), nowMillis, graceMillis)) {
            SessionReadiness.USABLE
        } else {
            SessionReadiness.RETRY_LATER
        }
}
