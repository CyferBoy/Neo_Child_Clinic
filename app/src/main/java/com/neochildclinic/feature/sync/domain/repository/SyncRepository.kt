package com.neochildclinic.feature.sync.domain.repository

import com.neochildclinic.core.sync.SyncEnqueuer
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.domain.model.SyncState
import kotlinx.coroutines.flow.StateFlow

// Domain-facing sync contract: only the enqueue entry point the business layer needs.
// Queue processing, retries, dedup and conflict resolution are data-layer internals behind
// the implementation (see SyncRepositoryImpl); the worker drives them through the impl.
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

    suspend fun hasFailedItems(): Boolean
    suspend fun getUnacknowledgedCount(): Int
    suspend fun clearLocalData()
}