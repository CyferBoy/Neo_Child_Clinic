package com.neochildclinic.domain.repository

import com.neochildclinic.core.sync.SyncEnqueuer
import com.neochildclinic.domain.model.SyncItem
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.domain.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Outcome of one queue drain. [isOwner] is true only for the run that actually executed. */
data class SyncRunResult(
    val permanentFailure: Boolean,
    val hasFailedItems: Boolean,
    val error: Throwable?,
    val isOwner: Boolean
)

// Domain-facing sync contract: only the enqueue entry point the business layer needs.
// Queue processing, retries, dedup and conflict resolution are data-layer internals behind
// the implementation (see SyncRepositoryImpl); workers drive them through this contract.
interface SyncRepository : SyncEnqueuer {
    override suspend fun enqueue(
        entityName: String,
        entityId: String,
        operation: SyncOperation,
        priority: SyncPriority,
        transactionGroupId: String?
    )

    val syncState: StateFlow<SyncState>
    fun getPendingCount(): kotlinx.coroutines.flow.Flow<Int>

    /** Drains the queue; returns true when at least one item ended FAILED (see SyncWorker). */
    suspend fun processNextItems(): Boolean
    suspend fun processNextItemsWithOwnership(): SyncRunResult

    suspend fun hasFailedItems(): Boolean
    suspend fun getUnacknowledgedCount(): Int
    suspend fun clearLocalData()

    // Queue-management entry points used by the Sync screen (SA-03: presentation
    // depends on this contract, never on the data-layer implementation).
    fun getSyncQueue(): Flow<List<SyncItem>>
    suspend fun clearSyncedItems()
    suspend fun retryItem(queueId: Long)
    suspend fun deleteQueueItem(queueId: Long)
    suspend fun retryFailedItems()
    suspend fun deleteAllFailed()
}