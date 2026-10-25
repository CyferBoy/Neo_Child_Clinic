package com.neochildclinic.core.sync

/**
 * Remote-deletion propagation for per-repository pulls (Phase 2 Batch 1, DA-01 /
 * Bug Register NCC-023).
 *
 * Every pull path is documented as insert/update-only (`docs/SYNC.md` conflict policy):
 * a row deleted on another device therefore never leaves this device — the phantom wastes
 * memory, appears in counts/statistics, and a queued edit against a remotely-deleted row
 * uploads as a silent no-op. This helper supplies the missing delete half of the pull.
 *
 * A local row is deleted if, after the repository's upsert pass has run:
 *  1. no remote row carries its id (the remote filters `is_deleted = false`, so the pulled
 *     set IS the full live set — nothing legitimately absent from it exists remotely),
 *     and the caller has not passed an empty set without opting in: a 200 with `[]` can be
 *     an RLS/policy regression, and deleting every local row is unrecoverable,
 *  2. the local row has no pending queue entry (an unacknowledged local edit is
 *     authoritative until pushed — same trust rule as the pull's upsert guard),
 *  3. and the row is already synced. Rows whose table has no `isSynced` column
 *     (FINANCE / VACCINE / BATCH / VACCINATION_ITEM / PROFILE) cannot test (3) — they pass
 *     `syncedIds = null` and rely on the queue guard alone.
 *
 * Pure function; the DAO wiring per repository is covered by the instrumented suites
 * (CI `android-instrumented` job). Returned ids are chunked so callers can issue
 * `DELETE ... WHERE id IN (&lt;chunk&gt;)` without tripping SQLite's 999-bind limit.
 */
object RemotePrune {

    private const val DEFAULT_CHUNK = 500

    fun computeBatches(
        localIds: List<String>,
        syncedIds: Set<String>?,
        remoteIds: Set<String>,
        pendingIds: Set<String>,
        chunkSize: Int = DEFAULT_CHUNK,
        // An empty remote set is suspicious: it can mean "this clinic has no rows", but it
        // can also be a 200 with [] from an RLS/policy regression. Deleting every local
        // row in that case is unrecoverable, so callers must opt in explicitly when an
        // empty remote set is known to be authoritative.
        allowEmptyRemote: Boolean = false
    ): List<List<String>> {
        if (!allowEmptyRemote && remoteIds.isEmpty() && localIds.isNotEmpty()) return emptyList()
        val doomed = localIds.distinct().filter { id ->
            id !in remoteIds && id !in pendingIds && (syncedIds == null || id in syncedIds)
        }
        if (doomed.isEmpty()) return emptyList()
        return doomed.chunked(chunkSize)
    }

    data class ReminderRef(val localId: String, val remoteId: String?, val synced: Boolean)

    fun computeRemindersBatches(
        localReminders: List<ReminderRef>,
        remoteIds: Set<String>,
        pendingIds: Set<String>,
        chunkSize: Int = DEFAULT_CHUNK,
        allowEmptyRemote: Boolean = false
    ): List<List<String>> {
        if (!allowEmptyRemote && remoteIds.isEmpty() &&
            localReminders.any { it.synced && it.remoteId != null }
        ) return emptyList()
        val doomed = localReminders.filter { r ->
            r.synced && r.remoteId != null &&
                    r.remoteId !in remoteIds &&
                    r.localId !in pendingIds && r.remoteId !in pendingIds
        }.map { it.localId }.distinct()
        if (doomed.isEmpty()) return emptyList()
        return doomed.chunked(chunkSize)
    }
}
