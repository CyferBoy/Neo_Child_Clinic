package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.ReminderAuditEntity
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.domain.model.ReminderStats
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import kotlinx.coroutines.flow.Flow

// ponytail: reminder rows (ReminderEntity) and their audit trail (ReminderAuditEntity) are
// passed as Room DTOs - the reminder IS a persistent row and the domain services operate on
// it wholesale (never on a projection). A parallel domain model would double every constructor
// for zero behavioral gain today.
interface ReminderRepository {
    fun getDueList(
        searchQuery: String = "",
        filterStatus: List<ReminderStatus>? = null
    ): Flow<List<Vaccination>>
    fun getPatientReminders(patientId: String): Flow<List<ReminderEntity>>
    fun getAuditTrail(patientId: String): Flow<List<ReminderAuditEntity>>
    suspend fun getRemindersByVisitId(visitId: String): List<ReminderEntity>
    suspend fun markReminderCompleted(
        reminder: ReminderEntity,
        performedBy: String,
        linkedVaccinationId: String? = null,
        transactionGroupId: String? = null
    )
    suspend fun deleteReminder(reminder: ReminderEntity, performedBy: String)
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
    suspend fun getReminderById(id: String): ReminderEntity?
    fun getAllReminders(): Flow<List<ReminderEntity>>
    suspend fun reschedule(
        reminder: ReminderEntity,
        newDate: String,
        reminderDate: String,
        reason: String,
        performedBy: String
    )
    suspend fun dismissReminder(reminder: ReminderEntity, reason: String, performedBy: String)
    suspend fun restoreReminder(reminder: ReminderEntity, performedBy: String)
    suspend fun cancelNextVaccinationVaccine(
        reminder: ReminderEntity,
        vaccineId: String,
        reason: String,
        performedBy: String
    )
}