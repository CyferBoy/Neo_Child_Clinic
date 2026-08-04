package com.neochildclinic.core.model

data class SyncItem(
    val id: Long,
    val entityName: String,
    val entityId: String,
    val operation: SyncOperation,
    val priority: SyncPriority,
    val status: SyncStatus,
    val retryCount: Int,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long
)
