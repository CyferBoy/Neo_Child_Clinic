package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY updatedAt DESC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName LIMIT 1")
    suspend fun getReminderState(patientId: String, visitId: String, vaccineName: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE status NOT IN ('COMPLETED', 'DISMISSED', 'EXTERNAL')")
    fun getActiveOverrides(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(reminder: ReminderEntity)

    @Query("UPDATE reminders SET status = :status, updatedAt = :timestamp, isSynced = 0 WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM reminders WHERE patientId = :patientId ORDER BY dueDate ASC")
    fun getFollowUpsForPatient(patientId: String): Flow<List<ReminderEntity>>

    @Query("UPDATE reminders SET patientId = :masterId, isSynced = 0 WHERE patientId = :duplicateId")
    suspend fun updatePatientId(duplicateId: String, masterId: String)

    @Query("DELETE FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName")
    suspend fun deleteReminder(patientId: String, visitId: String, vaccineName: String)

    @Query("UPDATE reminders SET status = 'COMPLETED', updatedAt = :timestamp WHERE patientId = :patientId")
    suspend fun markAllForPatientCompleted(patientId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM reminders WHERE isSynced = 0")
    suspend fun getUnsyncedReminders(): List<ReminderEntity>

    @Query("UPDATE reminders SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
