package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.DoctorWeeklySlot
import com.neochildclinic.domain.model.TimeRange
import kotlinx.coroutines.flow.Flow

interface DoctorAvailabilityRepository {

    /** Active weekly slots for a doctor, all days, for the Weekly Doctor Slots screen. */
    fun getWeeklySlots(doctorId: String): Flow<List<DoctorWeeklySlot>>

    /** Non-deleted date exceptions for a doctor, for the Edit Slot exceptions list. */
    fun getExceptions(doctorId: String): Flow<List<DoctorSlotException>>

    /** Weekly slots active for the given doctor/day, used by the availability calculation. */
    suspend fun getActiveWeeklySlotsForDay(doctorId: String, dayOfWeek: Int): List<DoctorWeeklySlot>

    /** Non-deleted exceptions for the given doctor/date, used by the availability calculation. */
    suspend fun getExceptionsForDate(doctorId: String, date: String): List<DoctorSlotException>

    suspend fun getWeeklySlotById(id: String): DoctorWeeklySlot?

    /**
     * Toggles one predefined weekly range on/off for a doctor+day (checkbox semantics for
     * req. 4). Reuses the existing row (soft-activate) if this exact doctor/day/range was
     * toggled off before, rather than creating a duplicate.
     */
    suspend fun setWeeklySlotEnabled(doctorId: String, dayOfWeek: Int, range: TimeRange, enabled: Boolean, actor: String?)

    suspend fun addException(exception: DoctorSlotException, actor: String?)
    suspend fun deleteException(id: String, actor: String?)

    suspend fun refresh()
}
