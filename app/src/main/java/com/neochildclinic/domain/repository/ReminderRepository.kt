package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.ReminderAuditEntity
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Single Source of Truth for Vaccination Reminders.
 */
interface ReminderRepository {
    
    // Unified Data Sources
    fun getDueList(
        searchQuery: String = "",
        filterStatus: List<ReminderStatus>? = null
    ): Flow<List<Vaccination>>
    
    // Next Vaccination logic
    suspend fun saveNextVaccination(
        patientId: String,
        originalVisitId: String,
        type: String,
        vaccineNames: List<String>,
        nxtVaccineId: List<String> = emptyList(),
        dueDate: String,
        notes: String,
        priority: String = "NORMAL",
        reminderEnabled: Boolean = true,
        performedBy: String
    )

    // Core Business Actions (Atomic)
    suspend fun markReminderCompleted(reminder: ReminderEntity, performedBy: String, linkedVaccinationId: String? = null, transactionGroupId: String? = null)
    suspend fun reschedule(reminder: ReminderEntity, newDate: String, reminderDate: String, reason: String, performedBy: String)
    suspend fun dismissReminder(reminder: ReminderEntity, reason: String, performedBy: String)
    suspend fun restoreReminder(reminder: ReminderEntity, performedBy: String)
    suspend fun deleteReminder(reminder: ReminderEntity, performedBy: String) // Admin only check usually in VM
    suspend fun updateReminderForEdit(reminder: ReminderEntity, performedBy: String, transactionGroupId: String? = null)
    
    suspend fun undoAction(auditId: String, performedBy: String)

    // Audit Trail & Management
    fun getAuditTrail(patientId: String): Flow<List<ReminderAuditEntity>>
    fun getPatientReminders(patientId: String): Flow<List<ReminderEntity>>
    fun getAllReminders(): Flow<List<ReminderEntity>>
    suspend fun getRemindersByVisitId(visitId: String): List<ReminderEntity>
    suspend fun getReminderById(id: String): ReminderEntity?

    // Dashboard Stats
    fun getDashboardStats(): Flow<ReminderStats>

    // Infrastructure / Internal
    fun triggerImmediateCheck()
    suspend fun refreshReminders()
    
    // Legacy support
    suspend fun markCompleted(id: String, timestamp: Long = System.currentTimeMillis())
    suspend fun insertReminder(reminder: ReminderEntity): String
    suspend fun transferReminders(duplicateId: String, masterId: String)
}

data class ReminderStats(
    val dueToday: Int = 0,
    val dueTomorrow: Int = 0,
    val overdue: Int = 0,
    val completedToday: Int = 0,
    val dismissedToday: Int = 0,
    val notificationsSentToday: Int = 0
)
