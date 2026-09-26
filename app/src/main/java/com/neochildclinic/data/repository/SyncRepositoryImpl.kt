package com.neochildclinic.data.repository
import com.neochildclinic.domain.repository.SyncRepository

import com.neochildclinic.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.core.model.SyncItem
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.model.SyncStatus
import com.neochildclinic.core.model.SyncErrorDetails
import com.neochildclinic.data.manager.SyncManagerImpl
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val syncManager: SyncManagerImpl,
    private val auth: Auth
) : SyncRepository {

    private val syncDao = database.syncQueueDao()
    private val uploader = SyncUploader(database, postgrest)

    // At most one manual session refresh is allowed per processNextItems() run (used both
    // by the batch-start prerequisite and by the per-item 401 retry). This keeps the app
    // from consuming a freshly-rotated refresh token twice in a row.
    private var batchRefreshAttempted = false

    companion object {
        // How long to wait for the SDK to finish loading a persisted session
        // (autoLoadFromStorage) before deciding whether a fresh background batch can
        // authenticate. Generous enough for a cold start, short enough that an
        // offline boot never stalls sync waiting on a token-refresh that can't reach
        // the network. Mirrors AuthViewModel's own bounded session-status wait.
        private const val SESSION_RESOLVE_TIMEOUT_MS = 5_000L

        // How much access-token lifetime must remain before a sync batch refreshes at
        // all. Refreshing unnecessarily is what races the SDK's own rotating refresh job
        // (see ensureAuthenticatedSession), so batches with a comfortably-valid token do
        // no manual refresh whatsoever.
        private const val SESSION_REFRESH_GRACE_MS = 60_000L
    }

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    override suspend fun enqueue(
        entityName: String,
        entityId: String,
        operation: SyncOperation,
        priority: SyncPriority,
        transactionGroupId: String?
    ) {
        if (entityId.isBlank() || (entityId == "kotlin.Unit") || (entityId == "Unit") || (entityId == "null")) {
            return
        }

        syncDao.enqueue(
            SyncQueueEntity(
                entityName = entityName,
                entityId = entityId,
                operation = operation.name,
                priority = priority.name,
                transactionGroupId = transactionGroupId
            )
        )
        syncManager.scheduleSync()
    }

    fun getPendingCount(): Flow<Int> = syncDao.getPendingCount()

    fun getSyncQueue(): Flow<List<SyncItem>> = 
        syncDao.getAllItems().map { list -> list.map { it.toDomain() } }

    suspend fun clearSyncedItems() {
        syncDao.clearSynced()
    }

    suspend fun processNextItems() {
        syncDao.cleanCorruptedItems()
        syncDao.requeueStaleSyncingItems(
            staleBefore = com.neochildclinic.core.utils.PatientUtils.getIsoTimestampMinutesAgo(5)
        )

        batchRefreshAttempted = false

        val pending = syncDao.getItemsByStatus(SyncStatus.PENDING.name)
        if (pending.isEmpty()) {
            _syncState.value = SyncState.IDLE
            return
        }

        _syncState.value = SyncState.SYNCING

        if (!resolveSessionOrDefer()) return

        val groups = orderPendingIntoGroups(pending, ::getEntityPriority)

        var hasError = false
        var sessionTransient = false
        var anyTransientRetry = false

        for ((groupId, groupItems) in groups) {
            when (processGroup(groupId, groupItems)) {
                GroupResult.SUCCESS -> Unit
                GroupResult.SESSION_DEFERRED -> sessionTransient = true
                GroupResult.FAILED -> hasError = true
                GroupResult.FAILED_TRANSIENT_RETRY -> {
                    hasError = true
                    anyTransientRetry = true
                }
            }
        }

        if (anyTransientRetry) {
            // At least one item was requeued PENDING for a transient reason (timeout,
            // Supabase 5xx, rate limiting, a dropped connection, ...). The periodic 15-
            // minute worker (see NeoChildApp) would eventually pick these back up on its
            // own, but scheduling now lets WorkManager's exponential backoff (see
            // SyncManagerImpl.scheduleSync) retry them promptly instead of waiting for that
            // next periodic tick.
            syncManager.scheduleSync()
        }

        if (sessionTransient) {
            // A group was deferred because auth wasn't available. Don't report ERROR and
            // don't recurse: leave the PENDING rows and let one quiet background run retry.
            _syncState.value = SyncState.IDLE
            syncManager.scheduleSync()
            android.util.Log.w("SyncRepositoryImpl", "Session refresh unavailable; scheduled a background retry")
            return
        }

        _syncState.value = if (hasError) SyncState.ERROR else SyncState.IDLE

        // 3. Loop if more items arrived during processing
        if (syncDao.getPendingCountSync() > 0 && !hasError) {
            processNextItems()
        }
    }

    /**
     * Resolves the session prerequisite this batch must clear before any Supabase write.
     *
     * Returns false when the batch must stop (genuinely logged out, or session still
     * restoring/refreshing), with any quiet background retry already scheduled and
     * [_syncState] reset to IDLE. Returns true only with a usable session in hand.
     *
     * Why the wait at the front: the SDK loads a saved session asynchronously
     * (autoLoadFromStorage), but a WorkManager run in a freshly-created process (the app
     * having been minimized and its process killed) can read currentSessionOrNull() as null
     * before that load finishes. Without waiting, every upload in this batch goes out
     * anonymous (anon key only), auth.uid() is null, so is_active_staff() is false and
     * Supabase rejects each row with "new row violates row-level security policy for table
     * ...". Wait for the load to settle first (bounded - a cold start must stay
     * offline-first and never hang on a network token-refresh inside the SDK's initial
     * status flow). Never push anonymous rows at an RLS-protected database; leave the queue
     * pending so a later sync picks it up instead of burning retries on RLS rejections.
     */
    private suspend fun resolveSessionOrDefer(): Boolean {
        val sessionResolved = awaitSessionResolved(auth.sessionStatus, SESSION_RESOLVE_TIMEOUT_MS)

        val currentSession = auth.currentSessionOrNull()
        if (currentSession == null) {
            val genuinelyLoggedOut = sessionResolved && auth.sessionStatus.value !is SessionStatus.RefreshFailure
            if (genuinelyLoggedOut) {
                // Status settled on NotAuthenticated - the user is genuinely logged out.
                // Skip everything; do NOT schedule work, so we never retry-loop without a
                // session. A manual/after-login sync handles it.
                android.util.Log.w("SyncRepositoryImpl", "No active session; skipping sync batch")
            } else {
                // Either the bounded wait expired while the SDK was still restoring the
                // persisted session (cold start, network), or the SDK is mid-refresh after
                // a (near-)expiry and reports RefreshFailure while it retries internally.
                // Both are transient: requeue one quiet, network-constrained background run
                // (unique work, so no unbounded queue) instead of failing or pushing
                // anonymously.
                android.util.Log.w(
                    "SyncRepositoryImpl",
                    "Session still restoring after ${SESSION_RESOLVE_TIMEOUT_MS}ms; scheduling a background retry"
                )
                syncManager.scheduleSync()
            }
            _syncState.value = SyncState.IDLE
            return false
        }

        // Auth prerequisite: prove there is a usable authenticated session before any
        // Supabase write, refreshing only when appropriate. Never swallows a refresh
        // failure or pushes anonymous rows with a dead session.
        when (ensureAuthenticatedSession()) {
            SessionReadiness.USABLE -> return true
            SessionReadiness.RETRY_LATER -> {
                // Refresh couldn't complete and no usable session exists (yet) - transient
                // (concurrent SDK refresh, network). No DB writes; one quiet background
                // retry via the scheduler's unique, backoff-bounded work.
                android.util.Log.w("SyncRepositoryImpl", "Session refresh deferred; scheduling a background retry")
                syncManager.scheduleSync()
                _syncState.value = SyncState.IDLE
                return false
            }
            SessionReadiness.LOGGED_OUT -> {
                // The SDK has settled on NotAuthenticated. Never retry-loop without a session.
                android.util.Log.w("SyncRepositoryImpl", "Session became unavailable; skipping sync batch")
                _syncState.value = SyncState.IDLE
                return false
            }
        }
    }

    /**
     * Uploads one ordered group (see [orderPendingIntoGroups] for how the group's own
     * ordering was derived). Batches the pre-upsert conflict-check read for the group:
     * one SELECT ... WHERE id IN (...) per table, instead of one SELECT per item inside
     * uploadEntity. DELETE and REMINDERS items are excluded because neither used the
     * per-item check this replaces (see fetchRemoteConflictData for why). VACCINATION_ITEM
     * has no updatedAt column (getEntityUpdatedAt -> ""); item rows are replaced via
     * DELETE+CREATE under one visit, so last-write-wins conflict checks on items are
     * meaningless - skip like REMINDERS.
     *
     * Items are processed sequentially. If Supabase rejects the request with a 401
     * (expired/invalid access token between the pre-sync refresh and this upload), refresh
     * once and retry the exact same queue item. Any other rejection - notably genuine
     * data-level authz failures such as RLS ("new row violates row-level security
     * policy", PostgREST 400 "42501") - rethrows into the group handler and is never
     * retried as a token problem.
     */
    private suspend fun processGroup(groupId: String, groupItems: List<SyncQueueEntity>): GroupResult {
        return try {
            database.withTransaction {
                for (item in groupItems) {
                    syncDao.updateStatus(item.queueId, SyncStatus.SYNCING.name)
                }
            }

            val conflictCheckCandidates = groupItems.filter {
                it.operation != SyncOperation.DELETE.name &&
                    it.entityName != "REMINDERS" &&
                    it.entityName != "VACCINATION_ITEM"
            }
            val remoteConflictData = uploader.fetchRemoteConflictData(conflictCheckCandidates)

            for (item in groupItems) {
                try {
                    uploader.uploadEntity(item, remoteConflictData)
                } catch (e: Exception) {
                    if (!requiresSessionRefresh(e)) throw e
                    handleSessionRefreshOn401(e)
                    uploader.uploadEntity(item, remoteConflictData)
                }
                uploader.markUploaded(item)
                syncDao.deleteItem(item)
            }
            GroupResult.SUCCESS
        } catch (e: SessionAuthTransientException) {
            // An auth/timing problem, not a data one: once the SDK finishes its own
            // refresh the same rows can sync, so leave them PENDING (never FAILED) and
            // let the end-of-batch handler schedule one quieter background run.
            for (item in groupItems) {
                syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
            }
            android.util.Log.w("SyncRepositoryImpl", "Group $groupId deferred: session refresh unavailable", e)
            GroupResult.SESSION_DEFERRED
        } catch (e: Exception) {
            android.util.Log.e("SyncRepositoryImpl", "Group sync failed: $groupId", e)
            if (classifyAndRecordError(groupItems, e)) {
                GroupResult.FAILED_TRANSIENT_RETRY
            } else {
                GroupResult.FAILED
            }
        }
    }

    /**
     * Applies the per-item retry/failure policy for a group that failed. Returns true when
     * at least one item was requeued PENDING for a transient reason.
     *
     * Transient (retryCount < 5): connectivity-layer failures, 408/429/5xx - the same
     * request could succeed later, so increment the retry count with error details and
     * leave the item PENDING.
     *
     * Permanent (anything else, or a transient one that already used its 5 attempts):
     * a bad payload, schema mismatch, RLS/permission denial, etc. - resending it would
     * never succeed without user/data intervention, or the transient item already churned
     * its limit. Either way mark it FAILED rather than looping forever: the row stays in
     * sync_queue with its error details (lastError), visible on the Sync screen and
     * retryable from there - never silently discarded.
     */
    private suspend fun classifyAndRecordError(groupItems: List<SyncQueueEntity>, e: Exception): Boolean {
        val transient = isTransientSyncError(e)
        var requeued = false

        for (item in groupItems) {
            if (transient && item.retryCount < 5) {
                syncDao.incrementRetryCount(item.queueId, buildSyncErrorDetails(e))
                syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
                requeued = true
            } else {
                syncDao.markFailed(item.queueId, SyncStatus.FAILED.name, buildSyncErrorDetails(e))
            }
        }
        return requeued
    }


    // True only for a *session/token* problem that one refresh + retry can fix. Uses the
    // actual HTTP status where supabase-kt surfaced one: an expired/invalid JWT is a
    // 401 (UnauthorizedRestException), while a genuine RLS rejection is 400 with PostgREST
    // error code "42501" and must stay a failure. The message-text check is only a fallback
    // for non-REST failures (e.g. local JWT parsing) and never matches RLS text.
    private fun requiresSessionRefresh(error: Throwable): Boolean {
        var current: Throwable? = error
        repeat(8) {
            if (shouldRefreshSessionFor((current as? RestException)?.statusCode, current?.message)) return true
            current = current?.cause
        }
        return false
    }

    /**
     * Auth prerequisite for a sync batch: prove there is a usable authenticated session
     * BEFORE doing any Supabase write, refreshing only when actually appropriate.
     *
     * The SDK already refreshes on load (autoLoadFromStorage) and proactively at ~80% of
     * the access-token lifetime (alwaysAutoRefresh is on by default). A manual
     * refreshCurrentSession() on top of that races the SDK's own refresh job: GoTrue
     * rotates refresh tokens on every refresh, so whichever caller loses that race receives
     * a 400 "refresh_token_not_found" (GoTrue message "No refresh token found") - exactly
     * what this app's background syncs used to surface. So: use the current token with no
     * refresh while it is comfortably valid; defer to the SDK while a refresh is already
     * running; refresh manually otherwise; and on a failed refresh classify the outcome
     * from the SDK's re-inspected session status instead of swallowing the error.
     */
    private suspend fun ensureAuthenticatedSession(): SessionReadiness {
        val session = auth.currentSessionOrNull()
            ?: return when (auth.sessionStatus.value) {
                is SessionStatus.NotAuthenticated -> SessionReadiness.LOGGED_OUT
                else -> SessionReadiness.RETRY_LATER
            }
        val now = System.currentTimeMillis()
        if (isSessionTokenUsable(session.expiresAt.toEpochMilliseconds(), now, SESSION_REFRESH_GRACE_MS)) {
            return SessionReadiness.USABLE
        }
        if (auth.isAutoRefreshRunning) {
            // The SDK is already refreshing this near-expiry token on its own schedule; a
            // second concurrent refresh would consume the same token mid-rotation.
            return SessionReadiness.RETRY_LATER
        }
        return try {
            auth.refreshCurrentSession()
            batchRefreshAttempted = true
            SessionReadiness.USABLE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never swallow a refresh failure, and never keep pushing with a dead session.
            classifySessionReadinessAfterFailedRefresh(
                status = auth.sessionStatus.value,
                nowMillis = System.currentTimeMillis(),
                graceMillis = SESSION_REFRESH_GRACE_MS
            )
        }
    }

    /**
     * Handles a 401 on an upload: at most one manual refresh per batch (see
     * [batchRefreshAttempted]), then a single retry of the exact same queue item. A second
     * 401 in the same run is rethrown so the item fails with its real error instead of
     * churning the refresh token. When the SDK is already refreshing - or the refresh
     * cannot yield a usable session - the batch is deferred as transient rather than
     * marking data FAILED for an auth/timing problem.
     */
    private suspend fun handleSessionRefreshOn401(uploadError: Exception) {
        if (batchRefreshAttempted) throw uploadError
        if (auth.isAutoRefreshRunning) throw SessionAuthTransientException()
        batchRefreshAttempted = true
        try {
            auth.refreshCurrentSession()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when (classifySessionReadinessAfterFailedRefresh(
                status = auth.sessionStatus.value,
                nowMillis = System.currentTimeMillis(),
                graceMillis = SESSION_REFRESH_GRACE_MS
            )) {
                SessionReadiness.USABLE -> Unit // the SDK installed a fresh session; retry below
                SessionReadiness.LOGGED_OUT -> throw uploadError // fail this item only; do not loop
                SessionReadiness.RETRY_LATER -> throw SessionAuthTransientException()
            }
        }
    }

    /** Internal marker: a batch must not write yet because auth isn't available. The
     * caller keeps the rows PENDING and schedules one quiet background retry instead of
     * marking data FAILED. */
    private class SessionAuthTransientException : Exception("Session refresh temporarily unavailable")

    private fun buildSyncErrorDetails(error: Throwable): String {
        val reason = error.message?.takeIf { it.isNotBlank() } ?: "Sync failed"
        var current: Throwable? = error

        repeat(8) {
            val throwable = current ?: return@repeat
            if (throwable is io.ktor.client.plugins.ResponseException) {
                val response = throwable.response
                val url = sanitizeUrl(response.call.request.url.toString())
                val requestHeaders = extractSafeHeaders(response.call.request.headers)
                    .mapKeys { "Request-${it.key}" }
                val responseHeaders = extractSafeHeaders(response.headers)
                    .mapKeys { "Response-${it.key}" }
                return SyncErrorDetails(
                    reason = reason,
                    url = url,
                    headers = responseHeaders + requestHeaders
                ).encode()
            }
            current = throwable.cause
        }

        return SyncErrorDetails(reason = reason).encode()
    }

    private fun extractSafeHeaders(headers: io.ktor.http.Headers): Map<String, String> {
        // Fail-closed allowlist: only header names known to be non-sensitive are ever
        // stored. A denylist would silently start leaking anything new the HTTP client
        // adds in a future version (e.g. a new auth-adjacent header) until someone
        // remembers to block it by name - an allowlist can't make that mistake.
        val safeNames = setOf(
            "content-type", "content-length", "date", "server", "connection",
            "cache-control", "vary", "transfer-encoding", "x-client-info",
            "x-request-id", "retry-after", "accept", "accept-encoding"
        )

        return buildMap {
            headers.entries().forEach { (name, values) ->
                if (name.lowercase() !in safeNames) return@forEach
                put(name, values.joinToString(","))
            }
        }
    }

    // Query strings can carry secrets (e.g. a Storage signed URL's token=...), so only
    // the scheme/host/path is ever kept - never the query string.
    private fun sanitizeUrl(rawUrl: String?): String? {
        if (rawUrl == null) return null
        val queryIndex = rawUrl.indexOf('?')
        return if (queryIndex >= 0) rawUrl.substring(0, queryIndex) else rawUrl
    }

    private fun getEntityPriority(entityName: String): Int =
        SYNC_ENTITY_REGISTRY[entityName]?.priority ?: 100

    suspend fun retryFailedItems() {
        val failed = syncDao.getItemsByStatus(SyncStatus.FAILED.name)
        for (item in failed) {
            syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
        }
        syncManager.scheduleSync()
    }

    suspend fun deleteQueueItem(queueId: Long) {
        syncDao.getItemById(queueId)?.let { item ->
            syncDao.deleteItem(item)
        }
    }

    suspend fun retryItem(queueId: Long) {
        syncDao.updateStatus(queueId, SyncStatus.PENDING.name)
        syncManager.scheduleSync()
    }

    suspend fun deleteAllFailed() {
        val failed = syncDao.getItemsByStatus(SyncStatus.FAILED.name)
        for (item in failed) {
            syncDao.deleteItem(item)
        }
    }
}

// True only when a request failed as a *session/token* problem. A 401 (expired/invalid
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
        if (t is java.io.IOException || t is io.github.jan.supabase.exceptions.HttpRequestException) {
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
    kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
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

// Groups pending items by transactionGroupId first (an item with no group id still becomes
// its own singleton group, same as before), then orders the GROUPS (not the raw items)
// using the entity-priority FK heuristic:
//   For CREATE/UPDATE: Parent (Priority 1) before Child (Priority 10)
//   For DELETE: Child (Priority 10 -> -10) before Parent (Priority 1 -> -1)
// This still governs ordering between items that were queued independently, with no shared
// transactionGroupId (e.g. a PATIENT create from one flow that must reach Supabase before
// an unrelated VACCINATION create queued afterward).
//
// WITHIN one transactionGroupId, the exact order the app enqueued these ops is preserved -
// NOT re-ranked by the entity-priority heuristic above. A shared group id means a single
// local business transaction (e.g. deleteVaccination) already sequenced its child
// unlink/delete calls correctly relative to a parent delete, and re-deriving that order
// from entity priority breaks as soon as a group mixes a parent DELETE with a child UPDATE:
// FINANCE's positive UPDATE key (basePriority 4 -> +4) sorted AFTER VACCINATION's negated
// DELETE key (basePriority 2 -> -2), so the old code deleted the patient_visits row before
// finance_transactions.visit_id was nulled, and Supabase rejected the delete with FK
// violation "finance_transactions_visit_id_fkey" (Postgres 23503). Sorting by
// createdAt/queueId instead keeps the unlink-before-delete (and child-delete-before-parent-
// delete) order that deleteVaccination() already builds the queue in.
internal fun orderPendingIntoGroups(
    pending: List<SyncQueueEntity>,
    priority: (String) -> Int
): Map<String, List<SyncQueueEntity>> {
    val rawGroups = pending.groupBy { it.transactionGroupId ?: java.util.UUID.randomUUID().toString() }

    fun sortKey(item: SyncQueueEntity): Int {
        val basePriority = priority(item.entityName)
        return if (item.operation == SyncOperation.DELETE.name) -basePriority else basePriority
    }

    return rawGroups.entries
        .sortedWith(
            compareBy(
                { entry -> entry.value.minOf(::sortKey) },
                { entry -> entry.value.minOf { it.createdAt } }
            )
        )
        .associate { (groupId, items) ->
            groupId to items.sortedWith(compareBy({ it.createdAt }, { it.queueId }))
        }
}

enum class SyncState { IDLE, SYNCING, ERROR }

// Outcome of one ordered group upload in processNextItems.
internal enum class GroupResult {
    SUCCESS,

    /** Auth wasn't available (session refresh deferred); rows left PENDING for a quiet retry. */
    SESSION_DEFERRED,

    /** The group failed and every item was marked FAILED (permanent, or retries exhausted). */
    FAILED,

    /** The group failed but at least one item was requeued PENDING for a transient reason. */
    FAILED_TRANSIENT_RETRY
}
