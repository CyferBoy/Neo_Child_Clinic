package com.neochildclinic.domain.repository

import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority

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
}