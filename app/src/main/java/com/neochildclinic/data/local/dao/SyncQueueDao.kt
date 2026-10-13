package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY priority DESC, createdAt ASC")
    suspend fun getItemsByStatus(status: String): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE queueId = :id LIMIT 1")
    suspend fun getItemById(id: Long): SyncQueueEntity?

    @Query("UPDATE sync_queue SET status = :status, updatedAt = :timestamp WHERE queueId = :id")
    suspend fun updateStatus(id: Long, status: String, timestamp: String = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp())

    @Query("UPDATE sync_queue SET status = :status, retryCount = retryCount + 1, lastError = :error, updatedAt = :timestamp WHERE queueId = :id")
    suspend fun markFailed(id: Long, status: String, error: String, timestamp: String = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp())

    @Query("DELETE FROM sync_queue WHERE entityId = 'kotlin.Unit' OR entityId = 'Unit' OR entityId = 'null'")
    suspend fun cleanCorruptedItems()

    // Queue-row dedup for repeated saves of the same operation (see
    // SyncRepositoryImpl.enqueue). Only PENDING rows: an in-flight (SYNCING) row is owned
    // by a running batch and a FAILED row carries its error details for the Sync screen.
    @Query("DELETE FROM sync_queue WHERE entityName = :name AND entityId = :id AND operation = :operation AND status = 'PENDING'")
    suspend fun deletePendingDuplicates(name: String, id: String, operation: String)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error, updatedAt = :timestamp WHERE queueId = :id")
    suspend fun incrementRetryCount(id: Long, error: String, timestamp: String = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp())

    @Query("UPDATE sync_queue SET status = 'PENDING' WHERE status = 'SYNCING' AND updatedAt < :staleBefore")
    suspend fun requeueStaleSyncingItems(staleBefore: String)

    @Delete
    suspend fun deleteItem(item: SyncQueueEntity)

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
}
