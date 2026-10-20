package com.neochildclinic.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruner decision + chunking rules for remote-deletion propagation (Phase 2 Batch 1,
 * DA-01/NCC-023). A local synced row that (a) no longer appears in the remote id set,
 * and (b) has no pending queue entry, must be deleted locally — chunked for SQLite's
 * 999-bind limit. Pure function: the Room wiring is covered by instrumented suites in CI.
 */
class RemotePruneTest {

    @Test
    fun deletesSyncedLocalIdsAbsentFromRemote() {
        val batches = RemotePrune.computeBatches(
            localIds = listOf("a", "b", "c"),
            syncedIds = setOf("a", "b", "c"),
            remoteIds = setOf("a"),
            pendingIds = emptySet()
        )
        assertEquals(listOf(listOf("b", "c")), batches)
    }

    @Test
    fun keepsRemotePresentIds() {
        val batches = RemotePrune.computeBatches(
            localIds = listOf("a"),
            syncedIds = setOf("a"),
            remoteIds = setOf("a", "remote-extra"),
            pendingIds = emptySet()
        )
        assertEquals(emptyList<List<String>>(), batches)
    }

    @Test
    fun pendingQueueEntryWinsOverAbsence() {
        val batches = RemotePrune.computeBatches(
            localIds = listOf("a"),
            syncedIds = setOf("a"),
            remoteIds = emptySet(),
            pendingIds = setOf("a")
        )
        assertEquals(emptyList<List<String>>(), batches)
    }

    @Test
    fun unsyncedLocalRowIsNeverPruned() {
        val batches = RemotePrune.computeBatches(
            localIds = listOf("a"),
            syncedIds = setOf<String>(), // a exists locally but is not synced yet
            remoteIds = emptySet(),
            pendingIds = emptySet()
        )
        assertEquals(emptyList<List<String>>(), batches)
    }

    @Test
    fun tableWithoutSyncedColumnDropsAbsentNonPendingRows() {
        // FINANCE / VACCINE / BATCH / PROFILE-style tables: no isSynced column, so the
        // synced-guard cannot apply — pending-queue guard only.
        val batches = RemotePrune.computeBatches(
            localIds = listOf("a", "b"),
            syncedIds = null,
            remoteIds = setOf("a"),
            pendingIds = setOf()
        )
        assertEquals(listOf(listOf("b")), batches)
    }

    @Test
    fun tableWithoutSyncedColumnStillHoldsPendingRows() {
        val batches = RemotePrune.computeBatches(
            localIds = listOf("a"),
            syncedIds = null,
            remoteIds = emptySet(),
            pendingIds = setOf("a")
        )
        assertEquals(emptyList<List<String>>(), batches)
    }

    @Test
    fun largeDeletesAreChunked() {
        val doomed = (1..1201).map { "id-$it" }
        val batches = RemotePrune.computeBatches(
            localIds = doomed,
            syncedIds = doomed.toSet(),
            remoteIds = emptySet(),
            pendingIds = emptySet(),
            chunkSize = 500
        )
        assertEquals(3, batches.size)
        assertEquals(500, batches[0].size)
        assertEquals(500, batches[1].size)
        assertEquals(201, batches[2].size)
    }

    @Test
    fun duplicateLocalIdsCollapse() {
        val batches = RemotePrune.computeBatches(
            localIds = listOf("b", "b", "b"),
            syncedIds = setOf("b"),
            remoteIds = emptySet(),
            pendingIds = emptySet()
        )
        assertEquals(listOf(listOf("b")), batches)
    }

    // REMINDERS variant: a reminder's local PK (id) is a device UUID; the remote PK lives in
    // serverId once the row has ever been on the server. To-remote maps id = serverId ?: id,
    // so any row that has been uploaded or downloaded carries serverId == its remote PK.

    @Test
    fun reminders_syncedAcknowledgedRowAbsentRemotelyIsPruned() {
        val batches = RemotePrune.computeRemindersBatches(
            localReminders = listOf(
                RemotePrune.ReminderRef(localId = "loc-1", remoteId = "srv-1", synced = true)
            ),
            remoteIds = emptySet(),
            pendingIds = emptySet()
        )
        assertEquals(listOf(listOf("loc-1")), batches)
    }

    @Test
    fun reminders_rowNeverAcknowledgedToServerIsKept() {
        val batches = RemotePrune.computeRemindersBatches(
            localReminders = listOf(
                RemotePrune.ReminderRef(localId = "loc-1", remoteId = null, synced = true)
            ),
            remoteIds = emptySet(),
            pendingIds = emptySet()
        )
        assertEquals(emptyList<List<String>>(), batches)
    }

    @Test
    fun reminders_unsyncedOrPendingIdentityIsKept() {
        val batches = RemotePrune.computeRemindersBatches(
            localReminders = listOf(
                RemotePrune.ReminderRef(localId = "loc-1", remoteId = "srv-1", synced = false),
                RemotePrune.ReminderRef(localId = "loc-2", remoteId = "srv-2", synced = true),
                RemotePrune.ReminderRef(localId = "loc-3", remoteId = "srv-3", synced = true)
            ),
            remoteIds = setOf("srv-2"),
            pendingIds = setOf("loc-1", "srv-3")
        )
        assertEquals(emptyList<List<String>>(), batches)
    }

    @Test
    fun reminders_remotePresentIdentityIsKeptAndDeletesKeyOnLocalId() {
        val batches = RemotePrune.computeRemindersBatches(
            localReminders = listOf(
                RemotePrune.ReminderRef(localId = "loc-a", remoteId = "srv-a", synced = true),
                RemotePrune.ReminderRef(localId = "loc-b", remoteId = "srv-gone", synced = true)
            ),
            remoteIds = setOf("srv-a"),
            pendingIds = emptySet()
        )
        assertEquals(listOf(listOf("loc-b")), batches)
    }

    private fun idsToSet(ids: List<String>) = ids.toSet()
}
