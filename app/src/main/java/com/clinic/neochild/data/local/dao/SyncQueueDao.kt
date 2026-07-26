package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.SyncQueueEntity
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
    suspend fun updateStatus(id: Long, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = :status, retryCount = retryCount + 1, lastError = :error, updatedAt = :timestamp WHERE queueId = :id")
    suspend fun markFailed(id: Long, status: String, error: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_queue WHERE entityId = 'kotlin.Unit' OR entityId = 'Unit' OR entityId = 'null'")
    suspend fun cleanCorruptedItems()

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error, updatedAt = :timestamp WHERE queueId = :id")
    suspend fun incrementRetryCount(id: Long, error: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'PENDING' WHERE status = 'SYNCING' AND updatedAt < :staleBeforeMillis")
    suspend fun requeueStaleSyncingItems(staleBeforeMillis: Long)

    @Delete
    suspend fun deleteItem(item: SyncQueueEntity)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_queue ORDER BY updatedAt DESC")
    fun getAllItems(): Flow<List<SyncQueueEntity>>

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED'")
    suspend fun clearSynced()

    @Query("SELECT EXISTS(SELECT 1 FROM sync_queue WHERE entityName = :name AND entityId = :id AND status != 'SYNCED')")
    suspend fun isUnsynced(name: String, id: String): Boolean
}
