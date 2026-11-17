package com.neochildclinic.data.local.entity
import com.neochildclinic.core.common.DateUtils

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.SyncItem
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.domain.model.SyncStatus

@Entity(
    tableName = "sync_queue",
    indices = [
        Index("status"),
        Index("priority"),
        Index(value = ["entityName", "entityId"]),
        Index("createdAt")
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,
    val entityName: String, // e.g., "PATIENT", "VACCINATION", "INVENTORY"
    val entityId: String,
    val operation: String, // SyncOperation
    val priority: String = SyncPriority.MEDIUM.name,
    val status: String = SyncStatus.PENDING.name,
    val transactionGroupId: String? = null,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: String = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp(),
    val updatedAt: String = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp()
)

fun SyncQueueEntity.toDomain() = SyncItem(
    id = queueId,
    entityName = entityName,
    entityId = entityId,
    operation = SyncOperation.valueOf(operation),
    priority = SyncPriority.valueOf(priority),
    status = SyncStatus.valueOf(status),
    transactionGroupId = transactionGroupId,
    retryCount = retryCount,
    lastError = lastError,
    createdAt = com.neochildclinic.core.common.DateUtils.isoToLong(createdAt),
    updatedAt = com.neochildclinic.core.common.DateUtils.isoToLong(updatedAt)
)
