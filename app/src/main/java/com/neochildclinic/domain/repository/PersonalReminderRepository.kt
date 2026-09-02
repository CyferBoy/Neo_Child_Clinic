package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.PersonalReminderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Personal Vaccine Reminders - standalone personal follow-up
 * reminders about a vaccine requirement for a patient. See [PersonalReminderEntity]
 * for the independence guarantees this feature must uphold (no automatic links
 * to Next Vaccination, vaccination records, or financial records).
 */
interface PersonalReminderRepository {
    fun getActiveReminders(): Flow<List<PersonalReminderEntity>>
    fun getCompletedReminders(): Flow<List<PersonalReminderEntity>>
    fun getCancelledReminders(): Flow<List<PersonalReminderEntity>>
    fun getRemindersForPatient(patientId: String): Flow<List<PersonalReminderEntity>>
    fun observeById(id: String): Flow<PersonalReminderEntity?>
    suspend fun getById(id: String): PersonalReminderEntity?

    suspend fun createReminder(reminder: PersonalReminderEntity)
    suspend fun updateReminder(reminder: PersonalReminderEntity)

    // Manual-only status transitions - each is only ever invoked from an explicit
    // user action in the UI. The current user is stamped into updated_by internally.
    suspend fun markReady(id: String)
    suspend fun markPending(id: String)
    suspend fun markCompleted(id: String)
    suspend fun cancel(id: String)

    suspend fun deleteReminder(id: String)

    suspend fun refresh()
}
