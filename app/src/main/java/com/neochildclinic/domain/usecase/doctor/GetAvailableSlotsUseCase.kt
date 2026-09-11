package com.neochildclinic.domain.usecase.doctor

import com.neochildclinic.domain.model.AvailableSlot
import com.neochildclinic.domain.model.DoctorAvailabilityResult
import com.neochildclinic.domain.model.SlotExceptionType
import com.neochildclinic.domain.repository.DoctorAvailabilityRepository
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

        val available = weeklySlots
            .filterNot { it.id in blockedSlotIds }
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

    private fun dayOfWeekFor(date: String): Int? {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
            sdf.isLenient = false
            val parsed = sdf.parse(date) ?: return null
            val cal = java.util.Calendar.getInstance()
            cal.time = parsed
            cal.get(java.util.Calendar.DAY_OF_WEEK) // Calendar.SUNDAY(1) .. Calendar.SATURDAY(7)
        } catch (e: Exception) {
            null
        }
    }
}
