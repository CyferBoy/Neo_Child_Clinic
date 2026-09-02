package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalReminderDao {

    // Active = still needs attention (Pending or Ready, regardless of date).
    // Overdue/Today/Upcoming grouping is derived in the UI layer from reminder_date.
    @Query("SELECT * FROM personal_vaccine_reminders WHERE status IN ('PENDING', 'READY') ORDER BY reminder_date ASC")
    fun getActiveReminders(): Flow<List<PersonalReminderEntity>>

    @Query("SELECT * FROM personal_vaccine_reminders WHERE status = 'COMPLETED' ORDER BY completed_at DESC")
    fun getCompletedReminders(): Flow<List<PersonalReminderEntity>>

    @Query("SELECT * FROM personal_vaccine_reminders WHERE status = 'CANCELLED' ORDER BY cancelled_at DESC")
    fun getCancelledReminders(): Flow<List<PersonalReminderEntity>>

    @Query("SELECT * FROM personal_vaccine_reminders WHERE patient_id = :patientId ORDER BY reminder_date DESC")
    fun getRemindersForPatient(patientId: String): Flow<List<PersonalReminderEntity>>

    @Query("SELECT * FROM personal_vaccine_reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PersonalReminderEntity?

    @Query("SELECT * FROM personal_vaccine_reminders WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PersonalReminderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: PersonalReminderEntity)

    @Query("DELETE FROM personal_vaccine_reminders WHERE id = :id")
    suspend fun delete(id: String)
}
