package com.neochildclinic.domain.repository

import com.neochildclinic.core.model.SyncItem
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SyncState { IDLE, SYNCING, ERROR }

interface SyncRepository {
    val syncState: Flow<SyncState>
    
    suspend fun enqueue(
        entityName: String,
        entityId: String,
        operation: SyncOperation,
        priority: SyncPriority = SyncPriority.MEDIUM
    )

    fun getPendingCount(): Flow<Int>
    fun getSyncQueue(): Flow<List<SyncItem>>
    suspend fun processNextItems()
    suspend fun retryFailedItems()
    suspend fun clearSyncedItems()
    suspend fun deleteQueueItem(queueId: Long)
    suspend fun retryItem(queueId: Long)
    suspend fun deleteAllFailed()
}
