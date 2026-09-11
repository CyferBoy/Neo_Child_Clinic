package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.DoctorWeeklySlot
import com.neochildclinic.domain.model.SlotExceptionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A doctor's normal recurring weekly availability block (e.g. "Monday 3 PM - 5 PM").
 * Source of truth for the Weekly Doctor Slots screen. Soft-delete only, per project
 * convention - "removing" a slot from the weekly checklist sets isActive = false rather
 * than deleting the row, so historical consultations/vaccinations that reference it via
 * availabilitySlotId keep a resolvable slot.
 */
@Serializable
@Entity(
    tableName = "doctor_weekly_slots",
    indices = [Index("doctorId"), Index("dayOfWeek"), Index("is_active")]
)
data class DoctorWeeklySlotEntity(
    @PrimaryKey val id: String,
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("start_minute") val startMinute: Int,
    @SerialName("end_minute") val endMinute: Int,
    @SerialName("is_active") @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: String = "",
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: String = "",
    @SerialName("is_synced") @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
)

fun DoctorWeeklySlotEntity.toDomain() = DoctorWeeklySlot(
    id = id,
    doctorId = doctorId,
    dayOfWeek = dayOfWeek,
    startMinute = startMinute,
    endMinute = endMinute,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    updatedBy = updatedBy
)

fun DoctorWeeklySlot.toEntity(isSynced: Boolean = false) = DoctorWeeklySlotEntity(
    id = id,
    doctorId = doctorId,
    dayOfWeek = dayOfWeek,
    startMinute = startMinute,
    endMinute = endMinute,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isSynced = isSynced,
    createdBy = createdBy,
    updatedBy = updatedBy
)

/**
 * A date-specific override of a doctor's normal weekly availability. Either the whole
 * day is blocked (exceptionType = FULL_DAY, weeklySlotId null) or one specific weekly
 * slot is blocked on that date (exceptionType = SLOT, weeklySlotId required).
 */
@Serializable
@Entity(
    tableName = "doctor_slot_exceptions",
    indices = [Index("doctorId"), Index("exceptionDate"), Index("weeklySlotId"), Index("is_deleted")]
)
data class DoctorSlotExceptionEntity(
    @PrimaryKey val id: String,
    @SerialName("doctor_id") val doctorId: String,
    @SerialName("exception_date") val exceptionDate: String,
    @SerialName("exception_type") val exceptionType: String,
    @SerialName("weekly_slot_id") val weeklySlotId: String? = null,
    val reason: String? = null,
    @SerialName("is_deleted") @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: String = "",
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: String = "",
    @SerialName("is_synced") @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
)

fun DoctorSlotExceptionEntity.toDomain() = DoctorSlotException(
    id = id,
    doctorId = doctorId,
    exceptionDate = exceptionDate,
    exceptionType = runCatching { SlotExceptionType.valueOf(exceptionType) }.getOrDefault(SlotExceptionType.FULL_DAY),
    weeklySlotId = weeklySlotId,
    reason = reason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    updatedBy = updatedBy
)

fun DoctorSlotException.toEntity(isSynced: Boolean = false, isDeleted: Boolean = false) = DoctorSlotExceptionEntity(
    id = id,
    doctorId = doctorId,
    exceptionDate = exceptionDate,
    exceptionType = exceptionType.name,
    weeklySlotId = weeklySlotId,
    reason = reason,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isSynced = isSynced,
    createdBy = createdBy,
    updatedBy = updatedBy
)
