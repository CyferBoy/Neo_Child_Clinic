package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val user: String,
    val module: String, // PATIENT, VACCINE, FINANCE, INVENTORY, STAFF, USERS, SYNC
    val entityType: String, // PATIENT, VISIT, REMINDER, BATCH, PAYMENT, etc.
    val entityId: String,
    val action: String, // CREATED, UPDATED, DELETED, COMPLETED, DISMISSED, etc.
    val oldValue: String? = null, // JSON representation
    val newValue: String? = null, // JSON representation
    val remarks: String? = null,
    val device: String? = null,
    val isSynced: Boolean = false,
    val patientId: String? = null // Helper field for fast timeline filtering
)
