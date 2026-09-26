package com.neochildclinic.domain.usecase.doctor

import com.neochildclinic.domain.model.AvailableSlot
import com.neochildclinic.domain.model.DoctorAvailabilityResult
import com.neochildclinic.domain.model.SlotExceptionType
import com.neochildclinic.domain.repository.DoctorAvailabilityRepository
import java.util.Calendar
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

        val exceptions = repository.getExceptionsForDate(doctorId, date)

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

        val available = weeklySlots
            .filterNot { it.id in blockedSlotIds }
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

    // Callers don't agree on date string format: Add Consultation and Add Vaccination
    // pass the app's display format (Constants.DATE_FORMAT, "d MMM yyyy" - e.g.
    // "17 Sep 2026"), while the Today's Patient quick-add dialog (DashboardViewModel)
    // passes ISO "yyyy-MM-dd". This previously only accepted "yyyy-MM-dd" with strict
    // parsing, so every call from Add Consultation/Add Vaccination silently failed to
    // parse, fell through to `return DoctorAvailabilityResult.Available(emptyList())`,
    // and the Available Slot dropdown always showed "No slots available" regardless of
    // what was actually configured on the Weekly Doctor Slots screen. Accept both
    // formats here instead of touching every call site's date convention.
    private fun dayOfWeekFor(date: String): Int? {
        val formatters = listOf(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.ENGLISH),
            java.time.format.DateTimeFormatter.ofPattern(
                com.neochildclinic.core.constants.Constants.DATE_FORMAT,
                java.util.Locale.ENGLISH
            )
        )
        for (formatter in formatters) {
            try {
                val parsed = java.time.LocalDate.parse(date, formatter)
                return parsed.dayOfWeek.value % 7 + 1 // Calendar.SUNDAY(1) .. Calendar.SATURDAY(7)
            } catch (_: java.time.format.DateTimeParseException) {
            }
        }
        return null
    }

    private fun rangesOverlap(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
        return aStart < bEnd && bStart < aEnd
    }
}

