package com.neochildclinic.core.sync

import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority

/**
 * Interface abstraction allowing core components (e.g. AuditLogger) to enqueue
 * outbox mutation operations without coupling core to feature-layer sync repositories.
 */
interface SyncEnqueuer {
    suspend fun enqueue(
        entityName: String,
        entityId: String,
        operation: SyncOperation,
        priority: SyncPriority = SyncPriority.MEDIUM,
        transactionGroupId: String? = null
    )
}
