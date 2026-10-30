package com.neochildclinic.domain.model

data class ReminderAudit(
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
    val timestamp: Long = 0,
    val reason: String? = null,
    val notes: String? = null,
    val isSynced: Boolean = false
)
