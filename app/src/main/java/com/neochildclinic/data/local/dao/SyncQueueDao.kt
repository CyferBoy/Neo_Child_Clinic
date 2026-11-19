package com.neochildclinic.data.local.dao
import com.neochildclinic.core.common.DateUtils

import androidx.room.*
import com.neochildclinic.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity)

    // Priority is an entity-name weight, not the TEXT `priority` column; orderPendingIntoGroups
    // applies it after this query. FIFO here avoids the misleading TEXT sort (NCC-025).
    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getItemsByStatus(status: String): List<SyncQueueEntity>

    // Bounded variant for the upload batch. Without a limit a continuously-enqueued queue
    // kept the single-flight run alive forever; with it each run drains a finite batch and a
    // later scheduled worker picks up the rest.
    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getItemsByStatus(status: String, limit: Int): List<SyncQueueEntity>

    // Scoped failed lookup for enqueue coalescing - avoids loading every FAILED row and
    // filtering it in Kotlin on each enqueue.
    @Query("SELECT * FROM sync_queue WHERE entityName = :name AND entityId = :id AND status = 'FAILED'")
    suspend fun getFailedForEntity(name: String, id: String): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE queueId = :id LIMIT 1")
    suspend fun getItemById(id: Long): SyncQueueEntity?

    @Query("UPDATE sync_queue SET status = :status, updatedAt = :timestamp WHERE queueId = :id")
    suspend fun updateStatus(id: Long, status: String, timestamp: String = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp())

    @Query("UPDATE sync_queue SET status = :status, retryCount = retryCount + 1, lastError = :error, updatedAt = :timestamp WHERE queueId = :id")
    suspend fun markFailed(id: Long, status: String, error: String, timestamp: String = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp())

    @Query("DELETE FROM sync_queue WHERE entityId = 'kotlin.Unit' OR entityId = 'Unit' OR entityId = 'null'")
    suspend fun cleanCorruptedItems()

    @Query("SELECT * FROM sync_queue WHERE entityName = :name AND entityId = :id AND status = 'PENDING'")
    suspend fun getPendingForEntity(name: String, id: String): List<SyncQueueEntity>

    @Query("DELETE FROM sync_queue WHERE queueId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error, updatedAt = :timestamp WHERE queueId = :id")
    suspend fun incrementRetryCount(id: Long, error: String, timestamp: String = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp())

    // 429 is the server asking us to slow down, not this item failing: requeue it and keep
    // its retry budget intact so a busy afternoon cannot exhaust five attempts.
    @Query("UPDATE sync_queue SET status = 'PENDING', lastError = :error, updatedAt = :timestamp WHERE queueId = :id")
    suspend fun requeueRateLimited(id: Long, error: String, timestamp: String = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp())

    @Query("UPDATE sync_queue SET status = 'PENDING' WHERE status = 'SYNCING' AND updatedAt < :staleBefore")
    suspend fun requeueStaleSyncingItems(staleBefore: String)

    @Delete
    suspend fun deleteItem(item: SyncQueueEntity)

    /** Drops every queued upload. Used by backup restore before it re-enqueues restored rows. */
    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    /**
     * Rows not yet acknowledged by the server (queued, in flight, or failed). Used by
     * the sign-out gate: these are the changes that would be lost by erasing local data.
     */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status != 'SYNCED'")
    suspend fun getUnacknowledgedCountSync(): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    suspend fun getPendingCountSync(): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :status")
    suspend fun getCountByStatus(status: String): Int

    @Query("SELECT * FROM sync_queue ORDER BY updatedAt DESC")
    fun getAllItems(): Flow<List<SyncQueueEntity>>

    // SYNCED is never written (rows are deleted after ack), so the old WHERE status =
    // 'SYNCED' made this a no-op. Clear what "clear" can mean: rows that are neither
    // queued (PENDING) nor in flight (SYNCING) - i.e. terminal FAILED rows - and never
    // touch rows a live batch owns or still owes the server.
    @Query("DELETE FROM sync_queue WHERE status NOT IN ('PENDING', 'SYNCING')")
    suspend fun clearSynced()

    @Query("SELECT EXISTS(SELECT 1 FROM sync_queue WHERE entityName = :name AND entityId = :id AND status != 'SYNCED')")
    suspend fun isUnsynced(name: String, id: String): Boolean

    // Same question as [isUnsynced], minus the row currently being uploaded.
    //
    // The item in flight is itself SYNCING, not SYNCED, so plain isUnsynced always matched it
    // and every `!isUnsynced(...)` guard was permanently false - including the remote-newer
    // self-heal branch in SyncUploader, which could never run (NCC-020). The row being uploaded
    // says nothing about whether the local edit is already safely on the server; it is about to
    // be. What actually matters is whether some OTHER unacknowledged edit for the same entity is
    // still queued.
    @Query("SELECT EXISTS(SELECT 1 FROM sync_queue WHERE entityName = :name AND entityId = :id AND status != 'SYNCED' AND queueId != :queueId)")
    suspend fun isUnsyncedExcluding(name: String, id: String, queueId: Long): Boolean

    // Bulk form of [isUnsynced] for pull/reconciliation loops. Calling isUnsynced once per
    // remote row turns a 5,000-row refresh into 5,000 queries (15,000 in refreshPatients,
    // which also looks the patient up twice per row). One query per entity name per refresh
    // turns it into one. Same predicate, so the guard semantics are unchanged.
    @Query("SELECT entityId FROM sync_queue WHERE entityName = :name AND status != 'SYNCED'")
    suspend fun unsyncedIds(name: String): List<String>

    /**
     * [unsyncedIds] as a set, for membership tests inside reconciliation loops.
     *
     * Fetch it inside the same transaction as the loop it guards (the in-transaction pull
     * loops do): the batch is only equivalent to the per-row [isUnsynced] it replaces when
     * both read the same SQLite snapshot. Fetched before a transaction, or with no
     * transaction around the loop, a queue row enqueued while the loop runs is invisible -
     * the per-row read would have seen it - and the pull can overwrite that edit.
     */
    suspend fun unsyncedIdSet(name: String): Set<String> = unsyncedIds(name).toHashSet()
}
