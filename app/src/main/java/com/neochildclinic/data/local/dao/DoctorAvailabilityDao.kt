package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.DoctorSlotExceptionEntity
import com.neochildclinic.data.local.entity.DoctorWeeklySlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorAvailabilityDao {

    // ---- Weekly slots ----

    @Query("SELECT * FROM doctor_weekly_slots WHERE doctorId = :doctorId AND is_active = 1 AND is_deleted = 0 ORDER BY dayOfWeek, startMinute")
    fun getActiveWeeklySlotsForDoctor(doctorId: String): Flow<List<DoctorWeeklySlotEntity>>

    @Query("SELECT * FROM doctor_weekly_slots WHERE doctorId = :doctorId AND dayOfWeek = :dayOfWeek AND is_active = 1 AND is_deleted = 0 ORDER BY startMinute")
    suspend fun getActiveWeeklySlotsForDay(doctorId: String, dayOfWeek: Int): List<DoctorWeeklySlotEntity>

    @Query("SELECT * FROM doctor_weekly_slots WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getWeeklySlotById(id: String): DoctorWeeklySlotEntity?

    @Query("SELECT * FROM doctor_weekly_slots WHERE doctorId = :doctorId AND dayOfWeek = :dayOfWeek AND startMinute = :startMinute AND endMinute = :endMinute AND is_deleted = 0 LIMIT 1")
    suspend fun findWeeklySlot(doctorId: String, dayOfWeek: Int, startMinute: Int, endMinute: Int): DoctorWeeklySlotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeeklySlot(slot: DoctorWeeklySlotEntity)

    @Query("UPDATE doctor_weekly_slots SET is_active = :isActive, updated_at = :updatedAt, is_synced = 0, updated_by = :updatedBy WHERE id = :id")
    suspend fun setWeeklySlotActive(id: String, isActive: Boolean, updatedAt: String, updatedBy: String?)

    // ---- Date exceptions ----

    @Query("SELECT * FROM doctor_slot_exceptions WHERE doctorId = :doctorId AND is_deleted = 0 ORDER BY exceptionDate DESC")
    fun getExceptionsForDoctor(doctorId: String): Flow<List<DoctorSlotExceptionEntity>>

    @Query("SELECT * FROM doctor_slot_exceptions WHERE doctorId = :doctorId AND exceptionDate = :date AND is_deleted = 0")
    suspend fun getExceptionsForDate(doctorId: String, date: String): List<DoctorSlotExceptionEntity>

    @Query("SELECT * FROM doctor_slot_exceptions WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getExceptionById(id: String): DoctorSlotExceptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertException(exception: DoctorSlotExceptionEntity)

    @Query("UPDATE doctor_slot_exceptions SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, is_synced = 0, updated_at = :deletedAt, updated_by = :deletedBy WHERE id = :id AND is_deleted = 0")
    suspend fun deleteException(id: String, deletedAt: String, deletedBy: String?)
}
