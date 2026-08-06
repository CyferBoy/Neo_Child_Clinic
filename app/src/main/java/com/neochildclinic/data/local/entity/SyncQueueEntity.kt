package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.core.model.SyncItem
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.model.SyncStatus

@Entity(
    tableName = "sync_queue",
    indices = [Index("status"), Index("priority")]
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
    val createdAt: String = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
    val updatedAt: String = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
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
    createdAt = com.neochildclinic.core.utils.PatientUtils.isoToLong(createdAt),
    updatedAt = com.neochildclinic.core.utils.PatientUtils.isoToLong(updatedAt)
)

fun SyncItem.toEntity() = SyncQueueEntity(
    queueId = id,
    entityName = entityName,
    entityId = entityId,
    operation = operation.name,
    priority = priority.name,
    status = status.name,
    transactionGroupId = transactionGroupId,
    retryCount = retryCount,
    lastError = lastError,
    createdAt = com.neochildclinic.core.utils.PatientUtils.formatDate(java.util.Date(createdAt)), 
    updatedAt = com.neochildclinic.core.utils.PatientUtils.formatDate(java.util.Date(updatedAt))
)
