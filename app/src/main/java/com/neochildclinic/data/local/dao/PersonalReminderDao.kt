package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalReminderDao {

    // Active = still needs attention (Pending or Ready, regardless of date).
    // Overdue/Today/Upcoming grouping is derived in the UI layer from reminder_date.
    @Query("SELECT * FROM personal_vaccine_reminders WHERE status IN ('PENDING', 'READY') AND is_deleted = 0 ORDER BY CASE WHEN reminder_date IS NULL OR reminder_date = '' THEN 1 ELSE 0 END, reminder_date ASC")
    fun getActiveReminders(): Flow<List<PersonalReminderEntity>>

    @Query("SELECT * FROM personal_vaccine_reminders WHERE status = 'COMPLETED' AND is_deleted = 0 ORDER BY completed_at DESC")
    fun getCompletedReminders(): Flow<List<PersonalReminderEntity>>

    @Query("SELECT * FROM personal_vaccine_reminders WHERE status = 'CANCELLED' AND is_deleted = 0 ORDER BY cancelled_at DESC")
    fun getCancelledReminders(): Flow<List<PersonalReminderEntity>>

    @Query("SELECT * FROM personal_vaccine_reminders WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getById(id: String): PersonalReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: PersonalReminderEntity)

    @Query("UPDATE personal_vaccine_reminders SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, is_synced = 0, updated_at = :deletedAt, updated_by = :deletedBy WHERE id = :id AND is_deleted = 0")
    suspend fun delete(id: String, deletedAt: String, deletedBy: String?)
}
