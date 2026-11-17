package com.neochildclinic.feature.doctor.domain

import com.neochildclinic.domain.model.AvailableSlot
import com.neochildclinic.domain.model.DoctorAvailabilityResult
import com.neochildclinic.domain.model.SlotExceptionType
import com.neochildclinic.feature.doctor.domain.repository.DoctorAvailabilityRepository
import com.neochildclinic.core.common.DateUtils
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Single, centralized implementation of the availability calculation (req. 11):
 *
 *   Selected Doctor -> Selected Date -> weekly slots for that weekday -> apply
 *   date-specific exceptions -> final available slots.
 *
 * Add Consultation, Add Vaccination and the Today's Patient quick-add dialog all call
 * this same use case rather than each re-implementing the weekly/exception merge logic.
 */
class GetAvailableSlotsUseCase @Inject constructor(
    private val repository: DoctorAvailabilityRepository
) {
    suspend operator fun invoke(doctorId: String, date: String): DoctorAvailabilityResult {
        if (doctorId.isBlank() || date.isBlank()) return DoctorAvailabilityResult.Available(emptyList())

        val dayOfWeek = dayOfWeekFor(date) ?: return DoctorAvailabilityResult.Available(emptyList())

        val weeklySlots = repository.getActiveWeeklySlotsForDay(doctorId, dayOfWeek)
        if (weeklySlots.isEmpty()) return DoctorAvailabilityResult.NoScheduleConfigured

        val isoDate = DateUtils.toIsoDate(date)
        val exceptions = repository.getExceptionsForDate(doctorId, isoDate)

        val fullDayException = exceptions.firstOrNull { it.exceptionType == SlotExceptionType.FULL_DAY }
        if (fullDayException != null) {
            return DoctorAvailabilityResult.FullDayUnavailable(fullDayException.reason)
        }

        val blockedSlotIds = exceptions
            .filter { it.exceptionType == SlotExceptionType.SLOT }
            .mapNotNull { it.weeklySlotId }
            .toSet()

        val customTimeExceptions = exceptions
            .filter { it.exceptionType == SlotExceptionType.SLOT && it.weeklySlotId == null && it.startMinute != null && it.endMinute != null }

        val dates = normalizeDates(date)
        val bookedSlotIds = repository.getBookedSlotIds(doctorId, dates)

        val available = weeklySlots
            .filterNot { it.id in blockedSlotIds }
            .filterNot { it.id in bookedSlotIds }
            .filterNot { slot -> customTimeExceptions.any { exc -> rangesOverlap(slot.startMinute, slot.endMinute, exc.startMinute!!, exc.endMinute!!) } }
            .sortedBy { it.startMinute }
            .map { AvailableSlot(it.id, it.doctorId, it.startMinute, it.endMinute) }

        return DoctorAvailabilityResult.Available(available)
    }

    /**
     * Whether [slotId] is still bookable for [doctorId] on [date] right now - used to
     * revalidate a previously selected slot at save time (req. 17), since another device
     * may have changed the doctor's availability while this form was open.
     */
    suspend fun isSlotStillAvailable(doctorId: String, date: String, slotId: String): Boolean {
        val result = invoke(doctorId, date)
        return result is DoctorAvailabilityResult.Available && result.slots.any { it.weeklySlotId == slotId }
    }

    // Callers don't agree on date string format: Add Consultation and Add Vaccination pass
    // the display format, the Today's Patient dialog passes ISO. DateUtils is the single
    // parser/formatter for both; only the Calendar-day and date-variant mappings stay here.
    private fun dayOfWeekFor(date: String): Int? =
        DateUtils.parseFlexibleDate(date)?.dayOfWeek?.value?.let { it % 7 + 1 } // Calendar.SUNDAY(1)..SATURDAY(7)

    private fun normalizeDates(date: String): List<String> {
        val parsed = DateUtils.parseFlexibleDate(date) ?: return listOf(date)
        return listOf(
            parsed.format(DateTimeFormatter.ISO_LOCAL_DATE),
            parsed.format(DateUtils.APP_DATE_FORMATTER)
        ).distinct()
    }

    private fun rangesOverlap(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
        return aStart < bEnd && bStart < aEnd
    }
}

