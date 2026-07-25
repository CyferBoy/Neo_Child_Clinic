package com.clinic.neochild.domain.repository

import com.clinic.neochild.data.local.entity.ReminderAuditEntity
import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.domain.model.*
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
    
    fun getDueToday(): Flow<List<Vaccination>>
    fun getDueTomorrow(): Flow<List<Vaccination>>
    fun getOverdue(): Flow<List<Vaccination>>
    
    // New Manual Management Observation
    fun getCompletedDueRecords(): Flow<List<CompletedDueRecord>>
    fun getDismissedDueRecords(): Flow<List<DismissedDueRecord>>
    fun getOtherEstablishmentDueRecords(): Flow<List<OtherEstablishmentDueRecord>>

    // Smart Follow-up logic
    suspend fun scheduleFollowUp(
        patientId: String,
        originalVisitId: String,
        vaccineNames: List<String>,
        dueDate: String,
        notes: String,
        priority: String,
        reminderEnabled: Boolean,
        performedBy: String
    )

    // Core Business Actions (Atomic)
    suspend fun markRequirementSatisfied(requirement: PendingRequirement, performedBy: String, linkedVaccinationId: String? = null)
    suspend fun reschedule(requirement: PendingRequirement, newDate: String, reminderDate: String, reason: String, performedBy: String)
    suspend fun markVaccinatedElsewhere(
        requirement: PendingRequirement,
        hospitalName: String,
        vaccinatedDate: String,
        notes: String,
        performedBy: String
    )
    suspend fun dismissReminder(requirement: PendingRequirement, reason: String, performedBy: String)
    suspend fun restoreReminder(requirement: PendingRequirement, performedBy: String)
    suspend fun deleteReminder(requirement: PendingRequirement, performedBy: String) // Admin only check usually in VM
    
    suspend fun undoAction(auditId: Long, performedBy: String)

    // Audit Trail & Management
    fun getAuditTrail(patientId: String): Flow<List<ReminderAuditEntity>>
    fun getPatientFollowUps(patientId: String): Flow<List<ReminderEntity>>

    // Dashboard Stats
    fun getDashboardStats(): Flow<ReminderStats>

    // Infrastructure / Internal
    fun triggerImmediateCheck()
    suspend fun refreshReminders()
    
    // Legacy support
    suspend fun markCompleted(id: Long, timestamp: Long = System.currentTimeMillis())
    suspend fun insertReminder(reminder: ReminderEntity): Long
    suspend fun transferReminders(duplicateId: String, masterId: String)
}

data class ReminderStats(
    val dueToday: Int = 0,
    val dueTomorrow: Int = 0,
    val overdue: Int = 0,
    val completedToday: Int = 0,
    val rescheduledToday: Int = 0,
    val externalToday: Int = 0,
    val dismissedToday: Int = 0,
    val notificationsSentToday: Int = 0
)
