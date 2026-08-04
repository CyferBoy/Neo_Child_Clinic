package com.neochildclinic.data.local.entity

/**
 * Legacy data class for UI support.
 * No longer a Room Entity - replaced by AuditLogEntity.
 */
data class ReminderAuditEntity(
    val auditId: Long = 0,
    val patientId: String,
    val originalVisitId: String,
    val vaccineName: String,
    val action: String,
    val oldStatus: String?,
    val newStatus: String,
    val oldDate: String?,
    val newDate: String?,
    val priority: String?,
    val reminderEnabled: Boolean?,
    val performedBy: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String? = null,
    val notes: String? = null,
    val isSynced: Boolean = false
)
