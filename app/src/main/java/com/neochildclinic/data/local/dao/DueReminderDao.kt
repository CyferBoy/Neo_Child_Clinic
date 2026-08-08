package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DueReminderDao {
    
    // Unified Reminder Queries
    
    @Query("SELECT * FROM reminders WHERE status IN ('ACTIVE', 'RESCHEDULED') ORDER BY dueDate ASC")
    fun getAllDueReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'COMPLETED' ORDER BY completionDate DESC")
    fun getAllCompletedReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'DISMISSED' ORDER BY dismissalDate DESC")
    fun getAllDismissedReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'EXTERNAL' ORDER BY dueDate ASC")
    fun getAllExternalReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName AND type = :type LIMIT 1")
    suspend fun getDueReminder(patientId: String, visitId: String, vaccineName: String, type: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE patientId = :pId AND originalVisitId = :vId AND vaccineName = :name AND type = :type LIMIT 1")
    suspend fun getReminderByStableId(pId: String, vId: String, name: String, type: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName AND type = :type")
    suspend fun deleteReminder(patientId: String, visitId: String, vaccineName: String, type: String)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun softDeleteReminder(id: String)

    @Query("DELETE FROM reminders WHERE patientId = :patientId")
    suspend fun softDeleteRemindersForPatient(patientId: String)

    // Legacy Support Mappings (redirected to unified table)
    
    suspend fun insertDueReminder(reminder: DueReminderEntity) { insertReminder(reminder) }
    suspend fun insertCompletedReminder(reminder: CompletedReminderEntity) { insertReminder(reminder) }
    suspend fun insertDismissedReminder(reminder: DismissedReminderEntity) { insertReminder(reminder) }
    suspend fun insertExternalReminder(reminder: ExternalReminderEntity) { insertReminder(reminder) }
    
    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun softDeleteDueReminder(id: String) = softDeleteReminder(id)

    @Query("UPDATE reminders SET patientId = :masterId, isSynced = 0 WHERE patientId = :duplicateId")
    suspend fun updatePatientId(duplicateId: String, masterId: String)

    @Query("SELECT * FROM reminders")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE patientId = :patientId")
    fun getDueRemindersForPatient(patientId: String): Flow<List<ReminderEntity>>

    @Transaction
    suspend fun getLocalPriority(pId: String, vId: String, name: String, type: String): Int {
        val reminder = getReminderByStableId(pId, vId, name, type) ?: return 0
        return when (reminder.status) {
            "EXTERNAL" -> 4
            "COMPLETED" -> 3
            "DISMISSED" -> 2
            "ACTIVE", "RESCHEDULED" -> 1
            else -> 0
        }
    }

    @Transaction
    suspend fun isLocalUnsynced(pId: String, vId: String, name: String, type: String): Boolean {
        return getReminderByStableId(pId, vId, name, type)?.isSynced == false
    }

    @Transaction
    suspend fun clearAllStates(patientId: String, visitId: String, vaccineName: String, type: String) {
        deleteReminder(patientId, visitId, vaccineName, type)
    }

    @Transaction
    suspend fun moveDueToCompleted(reminder: ReminderEntity, completedBy: String, notes: String? = null) {
        val updated = reminder.copy(
            status = "COMPLETED",
            completionDate = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
            performedBy = completedBy,
            notes = notes ?: reminder.notes,
            updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
            isSynced = false
        )
        insertReminder(updated)
    }

    @Transaction
    suspend fun moveDueToDismissed(reminder: ReminderEntity, dismissedBy: String, reason: String? = null) {
        val updated = reminder.copy(
            status = "DISMISSED",
            dismissalDate = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
            performedBy = dismissedBy,
            dismissalReason = reason,
            updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
            isSynced = false
        )
        insertReminder(updated)
    }

    @Transaction
    suspend fun moveDueToExternal(reminder: ReminderEntity, source: String, externalDate: String, recordedBy: String, notes: String? = null) {
        val updated = reminder.copy(
            status = "EXTERNAL",
            performedBy = recordedBy,
            notes = (notes ?: reminder.notes) + "\nSource: $source\nDate: $externalDate",
            updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
            isSynced = false
        )
        insertReminder(updated)
    }
}
