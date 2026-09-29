package com.neochildclinic.data.local.entity

import com.neochildclinic.domain.model.ReminderAudit

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

fun ReminderAuditEntity.toDomain() = ReminderAudit(
    auditId = auditId, patientId = patientId, originalVisitId = originalVisitId,
    vaccineName = vaccineName, action = action, oldStatus = oldStatus, newStatus = newStatus,
    oldDate = oldDate, newDate = newDate, priority = priority, reminderEnabled = reminderEnabled,
    performedBy = performedBy, timestamp = timestamp, reason = reason, notes = notes, isSynced = isSynced
)
