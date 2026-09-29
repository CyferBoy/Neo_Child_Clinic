package com.neochildclinic.feature.sync.domain.repository

import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.domain.model.SyncState
import kotlinx.coroutines.flow.StateFlow

// Domain-facing sync contract: only the enqueue entry point the business layer needs.
// Queue processing, retries, dedup and conflict resolution are data-layer internals behind
// the implementation (see SyncRepositoryImpl); the worker drives them through the impl.
interface SyncRepository {
    suspend fun enqueue(
        entityName: String,
        entityId: String,
        operation: SyncOperation,
        priority: SyncPriority = SyncPriority.MEDIUM,
        transactionGroupId: String? = null
    )

    val syncState: StateFlow<SyncState>
    fun getPendingCount(): kotlinx.coroutines.flow.Flow<Int>
    suspend fun processNextItems()
}