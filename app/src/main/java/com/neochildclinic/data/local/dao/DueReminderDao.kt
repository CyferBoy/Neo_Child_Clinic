package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DueReminderDao {
    
    // Unified Reminder State Queries
    
    @Query("SELECT * FROM reminder_states WHERE isDeleted = 0 AND status IN ('ACTIVE', 'RESCHEDULED') ORDER BY dueDate ASC")
    fun getAllDueReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder_states WHERE isDeleted = 0 AND status = 'COMPLETED' ORDER BY completionDate DESC")
    fun getAllCompletedReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder_states WHERE isDeleted = 0 AND status = 'DISMISSED' ORDER BY dismissalDate DESC")
    fun getAllDismissedReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder_states WHERE isDeleted = 0 AND status = 'EXTERNAL' ORDER BY dueDate ASC")
    fun getAllExternalReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder_states WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName AND isDeleted = 0 LIMIT 1")
    suspend fun getDueReminder(patientId: String, visitId: String, vaccineName: String): ReminderEntity?

    @Query("SELECT * FROM reminder_states WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminder_states WHERE patientId = :pId AND originalVisitId = :vId AND vaccineName = :name LIMIT 1")
    suspend fun getReminderByStableId(pId: String, vId: String, name: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminder_states WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName")
    suspend fun deleteReminder(patientId: String, visitId: String, vaccineName: String)

    @Query("UPDATE reminder_states SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteReminder(id: Long)

    @Query("UPDATE reminder_states SET isDeleted = 1, isSynced = 0 WHERE patientId = :patientId")
    suspend fun softDeleteRemindersForPatient(patientId: String)

    // Legacy Support Mappings (redirected to unified table)
    
    suspend fun insertDueReminder(reminder: DueReminderEntity) = insertReminder(reminder)
    suspend fun insertCompletedReminder(reminder: CompletedReminderEntity) = insertReminder(reminder)
    suspend fun insertDismissedReminder(reminder: DismissedReminderEntity) = insertReminder(reminder)
    suspend fun insertExternalReminder(reminder: ExternalReminderEntity) = insertReminder(reminder)
    
    @Query("UPDATE reminder_states SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun softDeleteDueReminder(id: Long) = softDeleteReminder(id)

    @Query("UPDATE reminder_states SET patientId = :masterId, isSynced = 0 WHERE patientId = :duplicateId")
    suspend fun updatePatientId(duplicateId: String, masterId: String)

    @Query("SELECT * FROM reminder_states WHERE isDeleted = 0")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder_states WHERE patientId = :patientId AND isDeleted = 0")
    fun getDueRemindersForPatient(patientId: String): Flow<List<ReminderEntity>>

    @Transaction
    suspend fun getLocalPriority(pId: String, vId: String, name: String): Int {
        val reminder = getReminderByStableId(pId, vId, name) ?: return 0
        return when (reminder.status) {
            "EXTERNAL" -> 4
            "COMPLETED" -> 3
            "DISMISSED" -> 2
            "ACTIVE", "RESCHEDULED" -> 1
            else -> 0
        }
    }

    @Transaction
    suspend fun isLocalUnsynced(pId: String, vId: String, name: String): Boolean {
        return getReminderByStableId(pId, vId, name)?.isSynced == false
    }

    @Transaction
    suspend fun clearAllStates(patientId: String, visitId: String, vaccineName: String) {
        deleteReminder(patientId, visitId, vaccineName)
    }

    @Transaction
    suspend fun moveDueToCompleted(reminder: ReminderEntity, completedBy: String, notes: String? = null): Long {
        val updated = reminder.copy(
            status = "COMPLETED",
            completionDate = System.currentTimeMillis(),
            performedBy = completedBy,
            notes = notes ?: reminder.notes,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        return insertReminder(updated)
    }

    @Transaction
    suspend fun moveDueToDismissed(reminder: ReminderEntity, dismissedBy: String, reason: String? = null): Long {
        val updated = reminder.copy(
            status = "DISMISSED",
            dismissalDate = System.currentTimeMillis(),
            performedBy = dismissedBy,
            dismissalReason = reason,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        return insertReminder(updated)
    }

    @Transaction
    suspend fun moveDueToExternal(reminder: ReminderEntity, source: String, externalDate: String, recordedBy: String, notes: String? = null): Long {
        val updated = reminder.copy(
            status = "EXTERNAL",
            externalDate = externalDate,
            source = source,
            performedBy = recordedBy,
            notes = notes ?: reminder.notes,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        return insertReminder(updated)
    }
}
