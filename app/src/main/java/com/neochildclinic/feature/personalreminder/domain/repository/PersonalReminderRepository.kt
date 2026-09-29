package com.neochildclinic.feature.personalreminder.domain.repository

import com.neochildclinic.domain.model.PersonalReminder
import kotlinx.coroutines.flow.Flow

interface PersonalReminderRepository {
    fun getActiveReminders(): Flow<List<PersonalReminder>>
    fun getCompletedReminders(): Flow<List<PersonalReminder>>
    fun getCancelledReminders(): Flow<List<PersonalReminder>>
    suspend fun getById(id: String): PersonalReminder?
    suspend fun createReminder(reminder: PersonalReminder)
    suspend fun updateReminder(reminder: PersonalReminder)
    suspend fun markReady(id: String)
    suspend fun markPending(id: String)
    suspend fun markCompleted(id: String)
    suspend fun cancel(id: String)
    suspend fun deleteReminder(id: String)
    suspend fun refresh()
}