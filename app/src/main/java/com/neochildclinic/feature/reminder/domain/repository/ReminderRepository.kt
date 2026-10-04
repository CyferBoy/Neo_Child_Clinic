package com.neochildclinic.feature.reminder.domain.repository

import com.neochildclinic.domain.model.ReminderAudit
import com.neochildclinic.domain.model.Reminder
import com.neochildclinic.domain.model.ReminderStats
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun getDueList(
        searchQuery: String = "",
        filterStatus: List<ReminderStatus>? = null
    ): Flow<List<Vaccination>>
    fun getPatientReminders(patientId: String): Flow<List<Reminder>>
    fun getAuditTrail(patientId: String): Flow<List<ReminderAudit>>
    suspend fun getRemindersByVisitId(visitId: String): List<Reminder>
    suspend fun markReminderCompleted(
        reminder: Reminder,
        performedBy: String,
        linkedVaccinationId: String? = null,
        transactionGroupId: String? = null
    )
    suspend fun deleteReminder(reminder: Reminder, performedBy: String)
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
    suspend fun transferReminders(duplicateId: String, masterId: String)
    suspend fun refreshReminders()

    fun getDashboardStats(): Flow<ReminderStats>
    suspend fun getReminderById(id: String): Reminder?
    fun getAllReminders(): Flow<List<Reminder>>

    // Upcoming Vaccination drill-down (Statistics -> Vaccination -> Upcoming).
    // Filtering happens in SQL (see DueReminderDao), not in Kotlin, and these share one
    // predicate with the statistics counts so the card total and the detail rows always agree.
    fun getUpcomingVaccinations(): Flow<List<Reminder>>
    fun getUpcomingVaccinationsByType(type: String): Flow<List<Reminder>>
    fun getUpcomingVaccinationsByVaccineId(vaccineId: String): Flow<List<Reminder>>
    suspend fun reschedule(
        reminder: Reminder,
        newDate: String,
        reminderDate: String,
        reason: String,
        performedBy: String
    )
    suspend fun dismissReminder(reminder: Reminder, reason: String, performedBy: String)
    suspend fun restoreReminder(reminder: Reminder, performedBy: String)
    suspend fun cancelNextVaccinationVaccine(
        reminder: Reminder,
        vaccineId: String,
        reason: String,
        performedBy: String
    )
}