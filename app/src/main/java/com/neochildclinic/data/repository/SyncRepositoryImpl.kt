package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.core.model.SyncItem
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.model.SyncStatus
import com.neochildclinic.core.model.SyncErrorDetails
import com.neochildclinic.domain.manager.SyncManager
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.SyncState
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val syncManager: SyncManager,
    private val auth: Auth
) : SyncRepository {

    private val syncDao = database.syncQueueDao()

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
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

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

    override fun getPendingCount(): Flow<Int> = syncDao.getPendingCount()

    override fun getSyncQueue(): Flow<List<SyncItem>> = 
        syncDao.getAllItems().map { list -> list.map { it.toDomain() } }

    override suspend fun clearSyncedItems() {
        syncDao.clearSynced()
    }

    override suspend fun processNextItems() {
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

        // Keep a persisted session usable before starting a batch.
        //
        // The SDK loads a saved session asynchronously (autoLoadFromStorage), but a
        // WorkManager run in a freshly-created process (the app having been minimized
        // and its process killed) can read currentSessionOrNull() as null before that
        // load finishes. Without waiting, every upload in this batch goes out anonymous
        // (anon key only), auth.uid() is null, so is_active_staff() is false and Supabase
        // rejects each row with "new row violates row-level security policy for table ...".
        // Wait for the load to settle first (bounded - a cold start must stay offline-first
        // and never hang on a network token-refresh inside the SDK's initial status flow).
        val sessionResolved = awaitSessionResolved(auth.sessionStatus, SESSION_RESOLVE_TIMEOUT_MS)

        val currentSession = auth.currentSessionOrNull()
        if (currentSession == null) {
            // Never push anonymous rows at an RLS-protected database. Leave the queue
            // pending so a later sync picks it up, instead of burning retries on RLS
            // rejections that can't succeed without an authenticated session.
            val genuinelyLoggedOut = sessionResolved && auth.sessionStatus.value !is SessionStatus.RefreshFailure
            if (genuinelyLoggedOut) {
                // Status settled on NotAuthenticated - the user is genuinely logged out.
                // Skip everything; do NOT schedule work, so we never retry-loop without a
                // session. A manual/after-login sync handles it.
                android.util.Log.w("SyncRepository", "No active session; skipping sync batch")
            } else {
                // Either the bounded wait expired while the SDK was still restoring the
                // persisted session (cold start, network), or the SDK is mid-refresh after
                // a (near-)expiry and reports RefreshFailure while it retries internally.
                // Both are transient: requeue one quiet, network-constrained background run
                // (unique work, so no unbounded queue) instead of failing or pushing
                // anonymously.
                android.util.Log.w(
                    "SyncRepository",
                    "Session still restoring after ${SESSION_RESOLVE_TIMEOUT_MS}ms; scheduling a background retry"
                )
                syncManager.scheduleSync()
            }
            _syncState.value = SyncState.IDLE
            return
        }

        // Auth prerequisite: prove there is a usable authenticated session before any
        // Supabase write, refreshing only when appropriate. Never swallows a refresh
        // failure or pushes anonymous rows with a dead session.
        when (ensureAuthenticatedSession()) {
            SessionReadiness.USABLE -> Unit
            SessionReadiness.RETRY_LATER -> {
                // Refresh couldn't complete and no usable session exists (yet) - transient
                // (concurrent SDK refresh, network). No DB writes; one quiet background
                // retry via the scheduler's unique, backoff-bounded work.
                android.util.Log.w("SyncRepository", "Session refresh deferred; scheduling a background retry")
                syncManager.scheduleSync()
                _syncState.value = SyncState.IDLE
                return
            }
            SessionReadiness.LOGGED_OUT -> {
                // The SDK has settled on NotAuthenticated. Never retry-loop without a session.
                android.util.Log.w("SyncRepository", "Session became unavailable; skipping sync batch")
                _syncState.value = SyncState.IDLE
                return
            }
        }
        
        // 1. Sort the queue to respect FK dependencies
        // For CREATE/UPDATE: Parent (Priority 1) before Child (Priority 10)
        // For DELETE: Child (Priority 10 -> -10) before Parent (Priority 1 -> -1)
        val sortedQueue = pending.sortedWith(
            compareBy(
                { 
                    val basePriority = getEntityPriority(it.entityName)
                    if (it.operation == SyncOperation.DELETE.name) -basePriority else basePriority 
                }, 
                { it.createdAt }
            )
        )

        // 2. Group by transactionGroupId
        val groups = sortedQueue.groupBy { it.transactionGroupId ?: java.util.UUID.randomUUID().toString() }

        var hasError = false
        var sessionTransient = false

        for ((groupId, groupItems) in groups) {
            try {
                database.withTransaction {
                    for (item in groupItems) {
                        syncDao.updateStatus(item.queueId, SyncStatus.SYNCING.name)
                    }
                }
                
                // Batch the pre-upsert conflict-check read (Section 6: Batched
                // Synchronization): one SELECT ... WHERE id IN (...) per table for this
                // group, instead of one SELECT per item inside uploadEntity. DELETE and
                // REMINDERS items are excluded because neither used the per-item check this
                // replaces (see fetchRemoteConflictData for why).
                val conflictCheckCandidates = groupItems.filter {
                    it.operation != SyncOperation.DELETE.name && it.entityName != "REMINDERS"
                }
                val remoteConflictData = fetchRemoteConflictData(conflictCheckCandidates)

                // Process items in group sequentially. If Supabase rejects the request
                // with a 401 (expired/invalid access token between the pre-sync refresh
                // and this upload), refresh once and retry the exact same queue item. Any
                // other rejection - notably genuine data-level authz failures such as RLS
                // ("new row violates row-level security policy", PostgREST 400 "42501") -
                // rethrows into the group handler and is never retried as a token problem.
                for (item in groupItems) {
                    try {
                        uploadEntity(item, remoteConflictData)
                    } catch (e: Exception) {
                        if (!requiresSessionRefresh(e)) throw e
                        handleSessionRefreshOn401(e)
                        uploadEntity(item, remoteConflictData)
                    }
                    syncDao.updateStatus(item.queueId, SyncStatus.SYNCED.name)
                    syncDao.deleteItem(item)
                }
            } catch (e: SessionAuthTransientException) {
                // An auth/timing problem, not a data one: once the SDK finishes its own
                // refresh the same rows can sync, so leave them PENDING (never FAILED) and
                // let the end-of-batch handler schedule one quieter background run.
                for (item in groupItems) {
                    syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
                }
                sessionTransient = true
                android.util.Log.w("SyncRepository", "Group $groupId deferred: session refresh unavailable", e)
            } catch (e: Exception) {
                hasError = true
                android.util.Log.e("SyncRepository", "Group sync failed: $groupId", e)
                
                val isNetworkError = e is java.io.IOException || e.message?.contains("network", ignoreCase = true) == true
                
                for (item in groupItems) {
                    if (isNetworkError && item.retryCount < 5) {
                        syncDao.incrementRetryCount(item.queueId, buildSyncErrorDetails(e))
                        syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
                    } else {
                        syncDao.markFailed(item.queueId, SyncStatus.FAILED.name, buildSyncErrorDetails(e))
                    }
                }
            }
        }

        if (sessionTransient) {
            // A group was deferred because auth wasn't available. Don't report ERROR and
            // don't recurse: leave the PENDING rows and let one quiet background run retry.
            _syncState.value = SyncState.IDLE
            syncManager.scheduleSync()
            android.util.Log.w("SyncRepository", "Session refresh unavailable; scheduled a background retry")
            return
        }

        _syncState.value = if (hasError) SyncState.ERROR else SyncState.IDLE

        // 3. Loop if more items arrived during processing
        if (syncDao.getPendingCountSync() > 0 && !hasError) {
            processNextItems()
        }
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
            val response = invokeGetter(throwable, "getResponse")
            if (response != null) {
                val request = invokeGetter(response, "getRequest")
                val url = sanitizeUrl(invokeGetter(request, "getUrl")?.toString())
                val requestHeaders = extractSafeHeaders(invokeGetter(request, "getHeaders"))
                    .mapKeys { "Request-${it.key}" }
                val responseHeaders = extractSafeHeaders(invokeGetter(response, "getHeaders"))
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

    private fun invokeGetter(target: Any?, methodName: String): Any? {
        if (target == null) return null
        return runCatching {
            target.javaClass.methods
                .firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
                ?.invoke(target)
        }.getOrNull()
    }

    private fun extractSafeHeaders(headersObject: Any?): Map<String, String> {
        if (headersObject == null) return emptyMap()

        // Fail-closed allowlist: only header names known to be non-sensitive are ever
        // stored. A denylist would silently start leaking anything new the HTTP client
        // adds in a future version (e.g. a new auth-adjacent header) until someone
        // remembers to block it by name - an allowlist can't make that mistake.
        val safeNames = setOf(
            "content-type", "content-length", "date", "server", "connection",
            "cache-control", "vary", "transfer-encoding", "x-client-info",
            "x-request-id", "retry-after", "accept", "accept-encoding"
        )

        val entries = runCatching {
            val entriesMethod = headersObject.javaClass.methods
                .firstOrNull { it.name == "entries" && it.parameterTypes.isEmpty() }
            @Suppress("UNCHECKED_CAST")
            entriesMethod?.invoke(headersObject) as? Iterable<Any?>
        }.getOrNull() ?: return emptyMap()

        return buildMap {
            entries.forEach { entry ->
                val pair = entry as? Pair<*, *>
                val name = pair?.first?.toString() ?: return@forEach
                if (name.lowercase() !in safeNames) return@forEach
                val value = when (val raw = pair.second) {
                    is Iterable<*> -> raw.joinToString(",")
                    else -> raw?.toString().orEmpty()
                }
                put(name, value)
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

    private fun getEntityPriority(entityName: String): Int {
        return when (entityName) {
            "PATIENT", "VACCINE" -> 1
            "VACCINATION", "VISIT", "BATCH" -> 2
            "VACCINATION_ITEM", "CONSULTATION", "CONSULTATION_TODO", "VACCINATION_TODO", "WASTE", "BORROW" -> 3
            "BORROW_RETURN" -> 4
            "INVENTORY_TRANSACTION", "FINANCE" -> 4
            "EXPENSE" -> 4
            "DOCTOR_WEEKLY_SLOT" -> 2
            "DOCTOR_SLOT_EXCEPTION" -> 3
            "REMINDERS", "PATIENT_NOTE", "AUDIT_LOG", "PERSONAL_REMINDER" -> 5
            else -> 100
        }
    }

    // Shared entityName -> Supabase table mapping, used both by uploadEntity (which still
    // throws on an unrecognized entityName, exactly as before) and by the batched
    // conflict-check prefetch below (which just skips grouping for entries it doesn't
    // recognize, since uploadEntity will throw on them anyway when its turn comes).
    private fun entityTable(entityName: String): String? = when (entityName) {
        "PATIENT" -> "patients"
        "VACCINATION", "VISIT" -> "patient_visits"
        "VACCINATION_ITEM" -> "vaccination_items"
        "WASTE" -> "waste_records"
        "REMINDERS" -> "reminders"
        "VACCINE" -> "vaccines"
        "BATCH" -> "vaccine_batches"
        "TRANSACTION", "INVENTORY_TRANSACTION" -> "inventory_transactions"
        "PATIENT_NOTE" -> "patient_notes"
        "FINANCE" -> "finance_transactions"
        "EXPENSE" -> "expenses"
        "PROFILE", "STAFF" -> "profiles"
        "BORROW" -> "borrow_records"
        "BORROW_RETURN" -> "borrow_returns"
        "AUDIT_LOG" -> "audit_logs"
        "CONSULTATION" -> "consultations"
        "CONSULTATION_TODO" -> "consultation_todos"
        "VACCINATION_TODO" -> "vaccination_todos"
        "PERSONAL_REMINDER" -> "personal_vaccine_reminders"
        "DOCTOR_WEEKLY_SLOT" -> "doctor_weekly_slots"
        "DOCTOR_SLOT_EXCEPTION" -> "doctor_slot_exceptions"
        else -> null
    }

    // Groups items by their target table and issues one `id IN (...)` SELECT per table,
    // instead of the one SELECT per item this replaces (large-data scalability pass,
    // Section 6: Batched Synchronization). REMINDERS and DELETE items are excluded by the
    // caller because neither ever reached the per-item conflict-check this replaces:
    // REMINDERS resolves its own identity/return path earlier in uploadEntity, and DELETE
    // returns before the conflict-check block too - so behavior for both is unchanged.
    // A read failure for one table's batch is logged and simply leaves that table's items
    // out of the map, matching the previous per-item try/catch, which also proceeded with
    // a plain upsert whenever the live check failed.
    private suspend fun fetchRemoteConflictData(
        items: List<SyncQueueEntity>
    ): Map<String, Map<String, kotlinx.serialization.json.JsonElement>> {
        if (items.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Map<String, kotlinx.serialization.json.JsonElement>>()
        val byTable = items.groupBy { entityTable(it.entityName) }

        for ((table, tableItems) in byTable) {
            if (table == null) continue
            val ids = tableItems.map { it.entityId }.distinct()
            if (ids.isEmpty()) continue

            try {
                val rows = postgrest.from(table).select {
                    filter { isIn("id", ids) }
                }.decodeList<Map<String, kotlinx.serialization.json.JsonElement>>()

                for (row in rows) {
                    val id = row["id"]?.toString()?.trim('"') ?: continue
                    result["$table:$id"] = row
                }
            } catch (e: Exception) {
                android.util.Log.w("SyncRepository", "Batched conflict-check read failed for $table", e)
            }
        }

        return result
    }

    private suspend fun uploadEntity(
        item: SyncQueueEntity,
        remoteConflictData: Map<String, Map<String, kotlinx.serialization.json.JsonElement>>
    ) {
        val table = entityTable(item.entityName)
            ?: throw IllegalArgumentException("Unknown entity: ${item.entityName}")

        if (item.operation == SyncOperation.DELETE.name) {
            if (item.entityName == "REMINDERS") {
                val serverId = database.dueReminderDao().getReminderById(item.entityId)?.serverId
                if (serverId != null) {
                    postgrest.from(table).delete {
                        filter { eq("id", serverId) }
                    }
                }
            } else {
                postgrest.from(table).delete {
                    filter { eq("id", item.entityId) }
                }
            }
            return
        }

        // Specialized logic for REMINDERS to handle server-generated IDs
        if (item.entityName == "REMINDERS") {
            val localReminder = database.dueReminderDao().getReminderById(item.entityId)
            if (localReminder != null) {
                if (item.operation == SyncOperation.CREATE.name && localReminder.serverId == null) {
                    // CREATE: use the locally generated UUID as the Supabase primary key.
                    // This keeps Room and Supabase IDs identical and never sends id = NULL.
                    postgrest.from(table).insert(localReminder.toRemote())
                    database.dueReminderDao().updateServerId(localReminder.id, localReminder.id)
                } else if (localReminder.serverId != null || item.operation == SyncOperation.CREATE.name) {
                    // UPDATE or CREATE where the remote identity is already known.
                    postgrest.from(table).upsert(localReminder.toRemote())
                }
                return
            }
        }

        val localData = fetchEntityData(item)
        if (localData != null) {
            val localUpdatedAt = getEntityUpdatedAt(localData)

            // Conflict check: was resolved with its own SELECT per item before this change;
            // now reads from the batch-fetched map built once per sync group in
            // processNextItems (see fetchRemoteConflictData). REMINDERS never reaches this
            // point (it returns earlier above), so the key is always table:entityId.
            val remoteData = remoteConflictData["$table:${item.entityId}"]
            if (remoteData != null) {
                val remoteUpdatedAtStr = remoteData["updated_at"]?.toString()?.replace("\"", "")
                    ?: remoteData["last_updated"]?.toString()?.replace("\"", "")

                val remoteUpdatedAt = com.neochildclinic.core.utils.PatientUtils.isoToLong(remoteUpdatedAtStr)
                val localUpdatedAtLong = com.neochildclinic.core.utils.PatientUtils.isoToLong(localUpdatedAt)

                if (remoteUpdatedAt > localUpdatedAtLong) {
                    // REMOTE IS NEWER: Sync back to local (Self-healing)
                    downloadAndReplaceLocal(item.entityName, remoteData)
                    return
                }
            }

            // EXPLICIT CASTING: Supabase upsert<T> requires the concrete type at compile time
            // to find the correct serializer. Passing 'Any' will fail.
            when (localData) {
                is PatientEntity -> postgrest.from(table).upsert(localData)
                is VisitEntity -> uploadVisit(table, localData)
                is VaccinationItemEntity -> postgrest.from(table).upsert(localData)
                is WasteEntity -> postgrest.from(table).upsert(localData)
                is ReminderEntity -> postgrest.from(table).upsert(localData.toRemote())
                is VaccineEntity -> postgrest.from(table).upsert(localData)
                is VaccineBatchEntity -> uploadBatch(table, localData, item.operation == SyncOperation.CREATE.name)
                is InventoryTransactionEntity -> postgrest.from(table).upsert(localData)
                is FinanceEntity -> postgrest.from(table).upsert(localData)
                is AuditLogEntity -> postgrest.from(table).upsert(localData)
                // profiles has no client-side INSERT policy at all (only the manage-staff
                // edge function, using the service role, is allowed to create rows there -
                // see the migration comment in 20260816_security_hardening.sql). upsert()
                // compiles to INSERT ... ON CONFLICT DO UPDATE, and Postgres RLS requires
                // BOTH the INSERT and UPDATE policies to pass for that statement even when
                // the row already exists and only the UPDATE arm will run - so with no
                // INSERT policy at all, every profile sync failed RLS, including ordinary
                // self-edits (name/phone) that the UPDATE-only policy would otherwise allow
                // fine. A plain UPDATE only needs the UPDATE policy, matching how
                // PatientViewModel already (correctly) writes to profiles elsewhere.
                is ProfileEntity -> postgrest.from(table).update(localData) {
                    filter { eq("id", localData.id) }
                }
                is ConsultationEntity -> postgrest.from(table).upsert(localData)
                is ConsultationTodoEntity -> postgrest.from(table).upsert(localData)
                is VaccinationTodoEntity -> postgrest.from(table).upsert(localData)
                is BorrowEntity -> postgrest.from(table).upsert(localData)
                is BorrowReturnEntity -> postgrest.from(table).upsert(localData)
                is PatientNotesEntity -> postgrest.from(table).upsert(localData)
                is PersonalReminderEntity -> postgrest.from(table).upsert(localData)
                is ExpenseEntity -> postgrest.from(table).upsert(localData)
                is DoctorWeeklySlotEntity -> postgrest.from(table).upsert(localData)
                is DoctorSlotExceptionEntity -> postgrest.from(table).upsert(localData)
            }
        }
    }

    // vaccine_batches.remaining_quantity is maintained server-side by the
    // tr_update_batch_stock trigger, which adjusts it by NEW.quantity for every
    // inventory_transactions row this app inserts (positive for purchases/restocks,
    // negative for deductions - see InventoryRepositoryImpl). If this upsert also sent
    // remaining_quantity, every change would be applied twice: once directly by the app,
    // once again by the trigger reacting to the paired transaction row. So:
    //  - on CREATE, send remaining_quantity = 0 (satisfies any NOT NULL constraint) and
    //    let the paired PURCHASE transaction - inserted right after, same sync group -
    //    bring it up to purchaseQuantity via the trigger.
    //  - on every other operation, omit remaining_quantity entirely so the column is left
    //    untouched by this upsert; whatever accompanying transaction row was inserted for
    //    that change is what the trigger reacts to.
    // Room keeps computing remaining_quantity locally as before, for offline-first display.
    private suspend fun uploadBatch(table: String, localData: VaccineBatchEntity, isCreate: Boolean) {
        val fullJson = kotlinx.serialization.json.Json.encodeToJsonElement(
            VaccineBatchEntity.serializer(), localData
        ) as JsonObject
        val fields = fullJson.toMutableMap()
        if (isCreate) {
            fields["remaining_quantity"] = kotlinx.serialization.json.JsonPrimitive(0)
        } else {
            fields.remove("remaining_quantity")
        }
        postgrest.from(table).upsert(JsonObject(fields))
    }

    // patient_visits.receipt_number is assigned by a database trigger (never by this app -
    // see 20260824_receipt_numbering.sql), so a freshly created visit is upserted with a
    // blank receiptNumber. Asking Postgrest to return the row lets us copy the
    // server-generated "NEO-YY/YY-NNNNNN" number back into Room right away, instead of
    // waiting for a later download to fill it in.
    private suspend fun uploadVisit(table: String, localData: VisitEntity) {
        if (localData.receiptNumber.isNotBlank()) {
            // Already has its number (normal edit path) - a plain upsert is enough.
            postgrest.from(table).upsert(localData)
            return
        }

        // Let a genuine upsert failure (network, RLS, etc.) propagate normally so the queue
        // item is retried/marked failed like any other entity - only the read-back below is
        // best-effort.
        val result = postgrest.from(table).upsert(localData) { select() }
        try {
            val savedRow = result.decodeSingleOrNull<VisitEntity>()
            if (savedRow != null && savedRow.receiptNumber.isNotBlank()) {
                database.vaccinationDao().updateReceiptNumber(localData.id, savedRow.receiptNumber)
            }
        } catch (e: Exception) {
            // The upsert itself already succeeded at this point; decoding the returned row
            // is only used to mirror the DB-assigned number locally right away, so don't fail
            // the sync item over it. The number will still be picked up on the next
            // download/refresh.
            android.util.Log.e("SyncRepository", "Could not read back receipt number for ${localData.id}", e)
        }
    }

    private suspend fun downloadAndReplaceLocal(entityName: String, remoteMap: Map<String, kotlinx.serialization.json.JsonElement>) {
        val json = kotlinx.serialization.json.Json { 
            ignoreUnknownKeys = true 
            coerceInputValues = true
        }
        val element = JsonObject(remoteMap)
        
        when (entityName) {
            "PATIENT" -> {
                val entity = json.decodeFromJsonElement<PatientEntity>(element)
                database.patientDao().insertPatient(entity.copy(isSynced = true))
            }
            "VACCINATION", "VISIT" -> {
                val entity = json.decodeFromJsonElement<VisitEntity>(element)
                database.vaccinationDao().insertVaccination(entity.copy(isSynced = true))
            }
            "VACCINATION_ITEM" -> {
                val entity = json.decodeFromJsonElement<VaccinationItemEntity>(element)
                database.vaccinationItemDao().insertItems(listOf(entity))
            }
            "VACCINE" -> {
                val entity = json.decodeFromJsonElement<VaccineEntity>(element)
                database.vaccineDao().insertVaccine(entity)
            }
            "BATCH" -> {
                val entity = json.decodeFromJsonElement<VaccineBatchEntity>(element)
                database.vaccineDao().insertBatch(entity)
            }
            "FINANCE" -> {
                val entity = json.decodeFromJsonElement<FinanceEntity>(element)
                database.financeDao().insertTransaction(entity.copy(isSynced = true))
            }
            "PATIENT_NOTE" -> {
                val entity = json.decodeFromJsonElement<PatientNotesEntity>(element)
                database.patientNotesDao().insertNote(entity.copy(isSynced = true))
            }
            "AUDIT_LOG" -> {
                val entity = json.decodeFromJsonElement<AuditLogEntity>(element)
                database.auditLogDao().insertLog(entity.copy(isSynced = true))
            }
            "REMINDERS" -> {
                val remote = json.decodeFromJsonElement<RemoteReminder>(element)
                val local = database.dueReminderDao().getReminderByStableId(
                    remote.patientId, 
                    remote.originalVisitId, 
                    remote.vaccineName,
                    remote.type
                )
                database.dueReminderDao().insertReminder(remote.toLocal(localId = local?.id))
            }
            "CONSULTATION" -> {
                val entity = json.decodeFromJsonElement<ConsultationEntity>(element)
                database.consultationDao().insertConsultation(entity.copy(isSynced = true))
            }
            "CONSULTATION_TODO" -> {
                val entity = json.decodeFromJsonElement<ConsultationTodoEntity>(element)
                database.patientTodoDao().insertConsultation(entity.copy(isSynced = true))
            }
            "VACCINATION_TODO" -> {
                val entity = json.decodeFromJsonElement<VaccinationTodoEntity>(element)
                database.patientTodoDao().insertVaccination(entity.copy(isSynced = true))
            }
            "BORROW" -> {
                val entity = json.decodeFromJsonElement<BorrowEntity>(element)
                database.borrowDao().insertRecord(entity.copy(isSynced = true))
            }
            "BORROW_RETURN" -> {
                val entity = json.decodeFromJsonElement<BorrowReturnEntity>(element)
                database.borrowReturnDao().insert(entity.copy(isSynced = true))
            }
            "WASTE" -> {
                val entity = json.decodeFromJsonElement<WasteEntity>(element)
                database.wasteDao().insertWaste(entity.copy(isSynced = true))
            }
            "PERSONAL_REMINDER" -> {
                val entity = json.decodeFromJsonElement<PersonalReminderEntity>(element)
                database.personalReminderDao().insert(entity.copy(isSynced = true))
            }
            "EXPENSE" -> {
                val entity = json.decodeFromJsonElement<ExpenseEntity>(element)
                database.expenseDao().insertExpense(entity.copy(isSynced = true))
            }
            "DOCTOR_WEEKLY_SLOT" -> {
                val entity = json.decodeFromJsonElement<DoctorWeeklySlotEntity>(element)
                database.doctorAvailabilityDao().upsertWeeklySlot(entity.copy(isSynced = true))
            }
            "DOCTOR_SLOT_EXCEPTION" -> {
                val entity = json.decodeFromJsonElement<DoctorSlotExceptionEntity>(element)
                database.doctorAvailabilityDao().upsertException(entity.copy(isSynced = true))
            }
        }
    }

    private fun getEntityUpdatedAt(data: Any?): String {
        return when (data) {
            is PatientEntity -> data.updatedAt ?: ""
            is VisitEntity -> data.updatedAt ?: ""
            is WasteEntity -> data.updatedAt
            is ConsultationEntity -> data.updatedAt ?: ""
            is ConsultationTodoEntity -> data.updatedAt
            is VaccinationTodoEntity -> data.updatedAt
            is ReminderEntity -> data.updatedAt
            is VaccineEntity -> data.lastUpdated
            is VaccineBatchEntity -> data.updatedAt
            is AuditLogEntity -> data.timestamp
            is PatientNotesEntity -> data.timestamp
            is InventoryTransactionEntity -> data.timestamp
            // A returned borrow is a local status update. borrow_records does not
            // have a client-side updated_at field, so using the original borrowed
            // date makes the conflict check incorrectly treat the remote row as newer
            // and download the old is_returned=false row. Use the current timestamp
            // while this pending update is being uploaded so the explicit return wins.
            is BorrowEntity -> if (data.isReturned) {
                com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
            } else {
                data.borrowedDate
            }
            is BorrowReturnEntity -> data.createdAt.ifBlank { data.returnedDate }
            is PersonalReminderEntity -> data.updatedAt
            is ExpenseEntity -> data.updatedAt
            is DoctorWeeklySlotEntity -> data.updatedAt
            is DoctorSlotExceptionEntity -> data.updatedAt
            else -> ""
        }
    }
    
    private suspend fun fetchEntityData(item: SyncQueueEntity): Any? {
        val entityId = item.entityId
        
        return try {
            when (item.entityName) {
                "PATIENT" -> {
                    val entity = database.patientDao().getPatientById(entityId)
                    // Strip 'TEMP-' prefix before uploading
                    if (entity?.patientClinicId?.startsWith("TEMP-") == true) {
                        entity.copy(patientClinicId = null)
                    } else {
                        entity
                    }
                }
                "VACCINATION", "VISIT" -> database.vaccinationDao().getVaccinationById(entityId)
                "VACCINATION_ITEM" -> database.vaccinationItemDao().getItemById(entityId)
                "WASTE" -> database.wasteDao().getWasteById(entityId)
                "REMINDERS" -> database.dueReminderDao().getReminderById(entityId)
                "VACCINE" -> database.vaccineDao().getVaccineById(entityId)
                "BATCH" -> database.vaccineDao().getBatchById(entityId)
                "TRANSACTION", "INVENTORY_TRANSACTION" -> database.vaccineDao().getTransactionById(entityId)
                "FINANCE" -> database.financeDao().getTransactionById(entityId)
                "AUDIT_LOG" -> database.auditLogDao().getLogById(entityId)
                "PROFILE", "STAFF" -> database.profileDao().getProfileById(entityId)
                "CONSULTATION" -> database.consultationDao().getConsultationById(entityId)
                "CONSULTATION_TODO" -> database.patientTodoDao().getConsultationTodoById(entityId)
                "VACCINATION_TODO" -> database.patientTodoDao().getVaccinationTodoById(entityId)
                "BORROW" -> database.borrowDao().getRecordById(entityId)
                "BORROW_RETURN" -> database.borrowReturnDao().getById(entityId)
                "PATIENT_NOTE" -> database.patientNotesDao().getNoteById(entityId)
                "PERSONAL_REMINDER" -> database.personalReminderDao().getById(entityId)
                "EXPENSE" -> database.expenseDao().getExpenseById(entityId)
                "DOCTOR_WEEKLY_SLOT" -> database.doctorAvailabilityDao().getWeeklySlotById(entityId)
                "DOCTOR_SLOT_EXCEPTION" -> database.doctorAvailabilityDao().getExceptionById(entityId)
                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "Error fetching data for sync: ${item.entityName} ID $entityId", e)
            null
        }
    }

    override suspend fun retryFailedItems() {
        val failed = syncDao.getItemsByStatus(SyncStatus.FAILED.name)
        for (item in failed) {
            syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
        }
        syncManager.scheduleSync()
    }

    override suspend fun deleteQueueItem(queueId: Long) {
        syncDao.getItemById(queueId)?.let { item ->
            syncDao.deleteItem(item)
        }
    }

    override suspend fun retryItem(queueId: Long) {
        syncDao.updateStatus(queueId, SyncStatus.PENDING.name)
        syncManager.scheduleSync()
    }

    override suspend fun deleteAllFailed() {
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
