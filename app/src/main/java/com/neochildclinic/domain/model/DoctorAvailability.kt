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
 * Fixed catalog of selectable weekly-availability ranges (req. 4: "predefined time-range
 * options", not free-text/individual appointment times). Two-hour blocks covering a normal
 * clinic day; admins/doctors pick which of these apply to which weekday.
 */
object PredefinedSlots {
    val ALL: List<TimeRange> = listOf(
        TimeRange(9 * 60, 11 * 60),   // 9 AM - 11 AM
        TimeRange(11 * 60, 13 * 60),  // 11 AM - 1 PM
        TimeRange(13 * 60, 15 * 60),  // 1 PM - 3 PM
        TimeRange(15 * 60, 17 * 60),  // 3 PM - 5 PM
        TimeRange(17 * 60, 19 * 60),  // 5 PM - 7 PM
        TimeRange(19 * 60, 21 * 60),  // 7 PM - 9 PM
    )
}

/**
 * A doctor's normal recurring weekly availability block.
 * dayOfWeek follows java.util.Calendar.DAY_OF_WEEK: Sunday=1 ... Saturday=7.
 */
@Serializable
data class DoctorWeeklySlot(
    val id: String = java.util.UUID.randomUUID().toString(),
    @SerialName("doctor_id") val doctorId: String = "",
    @SerialName("day_of_week") val dayOfWeek: Int = 1,
    @SerialName("start_minute") val startMinute: Int = 0,
    @SerialName("end_minute") val endMinute: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null
) {
    val timeRange: TimeRange get() = TimeRange(startMinute, endMinute)
}

enum class SlotExceptionType { FULL_DAY, SLOT }

/**
 * A date-specific override of a doctor's normal weekly availability - either the entire
 * day is unavailable, or one specific weekly slot is unavailable on that date.
 */
@Serializable
data class DoctorSlotException(
    val id: String = java.util.UUID.randomUUID().toString(),
    @SerialName("doctor_id") val doctorId: String = "",
    @SerialName("exception_date") val exceptionDate: String = "", // yyyy-MM-dd
    @SerialName("exception_type") val exceptionType: SlotExceptionType = SlotExceptionType.FULL_DAY,
    @SerialName("weekly_slot_id") val weeklySlotId: String? = null,
    val reason: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null
)

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
