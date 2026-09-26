package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.PersonalReminderEntity
import kotlinx.coroutines.flow.Flow

// ponytail: the personal reminder IS the persisted row and its transitions are only ever
// invoked from explicit UI actions; it passes the Room DTO straight through, no parallel domain
// model today.
interface PersonalReminderRepository {
    fun getActiveReminders(): Flow<List<PersonalReminderEntity>>
    fun getCompletedReminders(): Flow<List<PersonalReminderEntity>>
    fun getCancelledReminders(): Flow<List<PersonalReminderEntity>>
    suspend fun getById(id: String): PersonalReminderEntity?
    suspend fun createReminder(reminder: PersonalReminderEntity)
    suspend fun updateReminder(reminder: PersonalReminderEntity)
    suspend fun markReady(id: String)
    suspend fun markPending(id: String)
    suspend fun markCompleted(id: String)
    suspend fun cancel(id: String)
    suspend fun deleteReminder(id: String)
    suspend fun refresh()
}