package com.neochildclinic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A broad availability time range (e.g. "3 PM - 5 PM"), stored as structured start/end
 * minute-of-day values rather than a text label - the label is always derived, never the
 * source of truth (req. 12 in the doctor-slots spec).
 */
data class TimeRange(val startMinute: Int, val endMinute: Int) {
    fun label(): String = "${formatMinuteOfDay(startMinute)} \u2013 ${formatMinuteOfDay(endMinute)}"

    companion object {
        fun formatMinuteOfDay(minute: Int): String {
            val h24 = minute / 60
            val m = minute % 60
            val amPm = if (h24 < 12) "AM" else "PM"
            var h12 = h24 % 12
            if (h12 == 0) h12 = 12
            return if (m == 0) "$h12 $amPm" else "$h12:${m.toString().padStart(2, '0')} $amPm"
        }
    }
}

/**
 * A doctor's normal recurring weekly availability block.
 * dayOfWeek follows java.util.Calendar.DAY_OF_WEEK: Sunday=1 ... Saturday=7.
 */
typealias DoctorWeeklySlot = com.neochildclinic.data.local.entity.DoctorWeeklySlotEntity

enum class SlotExceptionType { FULL_DAY, SLOT }

/**
 * A date-specific override of a doctor's normal weekly availability - either the entire
 * day is unavailable, or a specific time period is unavailable on that date.
 */
@Serializable
data class DoctorSlotException(
    val id: String = java.util.UUID.randomUUID().toString(),
    @SerialName("doctor_id") val doctorId: String = "",
    @SerialName("exception_date") val exceptionDate: String = "", // yyyy-MM-dd
    @SerialName("exception_type") val exceptionType: SlotExceptionType = SlotExceptionType.FULL_DAY,
    @SerialName("weekly_slot_id") val weeklySlotId: String? = null,
    @SerialName("start_minute") val startMinute: Int? = null,
    @SerialName("end_minute") val endMinute: Int? = null,
    val reason: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null
) {
    /** Human-readable label for the exception time range, if custom times are set. */
    val timeRangeLabel: String?
        get() = if (startMinute != null && endMinute != null) {
            TimeRange(startMinute, endMinute).label()
        } else null
}

/** A weekly slot resolved as actually bookable for one specific date. */
data class AvailableSlot(
    val weeklySlotId: String,
    val doctorId: String,
    val startMinute: Int,
    val endMinute: Int
) {
    val label: String get() = TimeRange(startMinute, endMinute).label()
}

/** Result of the centralized availability calculation (req. 11). */
sealed class DoctorAvailabilityResult {
    data class Available(val slots: List<AvailableSlot>) : DoctorAvailabilityResult()
    data class FullDayUnavailable(val reason: String?) : DoctorAvailabilityResult()
    object NoScheduleConfigured : DoctorAvailabilityResult()
}
