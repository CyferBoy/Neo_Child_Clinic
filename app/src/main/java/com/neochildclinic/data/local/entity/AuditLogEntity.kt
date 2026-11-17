package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.neochildclinic.domain.model.AuditLog

/**
 * Unified Audit Log for all clinic activities.
 * Every business event is recorded here.
 * Patient and Vaccine timelines are generated from this table.
 */
@Serializable
@Entity(
    tableName = "audit_logs",
    indices = [
        Index("timestamp"),
        Index("patientId"),
        Index("entityType"),
        Index("entityId"),
        Index("module"),
        Index("user")
    ]
)
data class AuditLogEntity(
    @PrimaryKey @SerialName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerialName("timestamp") val timestamp: String = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp(),
    @SerialName("user") val user: String,
    @SerialName("module") val module: String, // PATIENT, VACCINE, FINANCE, INVENTORY, STAFF, USERS, SYNC
    @SerialName("entity_type") val entityType: String, // PATIENT, VISIT, REMINDER, BATCH, PAYMENT, etc.
    @SerialName("entity_id") val entityId: String,
    @SerialName("action") val action: String, // CREATED, UPDATED, DELETED, COMPLETED, DISMISSED, etc.
    @SerialName("old_value") val oldValue: String? = null, // JSON representation
    @SerialName("new_value") val newValue: String? = null, // JSON representation
    @SerialName("remarks") val remarks: String? = null,
    @SerialName("device") val device: String? = null,
    @SerialName("is_synced") override val isSynced: Boolean = false,
    @SerialName("patient_id") val patientId: String? = null // Helper field for fast timeline filtering
) : SyncableEntity

fun AuditLogEntity.toDomain() = AuditLog(
    id = id,
    timestamp = timestamp,
    user = user,
    module = module,
    entityType = entityType,
    entityId = entityId,
    action = action,
    oldValue = oldValue,
    newValue = newValue,
    remarks = remarks,
    device = device,
    patientId = patientId
)
