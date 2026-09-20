package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.DoctorWeeklySlot
import com.neochildclinic.domain.model.TimeRange
import kotlinx.coroutines.flow.Flow

interface DoctorAvailabilityRepository {

    /** Active weekly slots for a doctor, all days, for the Doctor Timings screen. */
    fun getWeeklySlots(doctorId: String): Flow<List<DoctorWeeklySlot>>

    /** Date exceptions for a doctor, for the Edit Slot exceptions list. */
    fun getExceptions(doctorId: String): Flow<List<DoctorSlotException>>

    /** Weekly slots active for the given doctor/day, used by the availability calculation. */
    suspend fun getActiveWeeklySlotsForDay(doctorId: String, dayOfWeek: Int): List<DoctorWeeklySlot>

    /** Exceptions for the given doctor/date, used by the availability calculation. */
    suspend fun getExceptionsForDate(doctorId: String, date: String): List<DoctorSlotException>

    suspend fun getWeeklySlotById(id: String): DoctorWeeklySlot?

    /**
     * Adds a new weekly slot for a doctor+day with the given start/end times.
     * Creates a new row or reactivates a previously soft-deleted matching row.
     */
    suspend fun addWeeklySlot(doctorId: String, dayOfWeek: Int, startMinute: Int, endMinute: Int, actor: String?)

    /**
     * Soft-deletes a weekly slot by deactivating it. Historical records referencing
     * this slot's id via availabilitySlotId keep a resolvable reference.
     */
    suspend fun removeWeeklySlot(slotId: String, actor: String?)

    suspend fun addException(exception: DoctorSlotException, actor: String?)
    suspend fun deleteException(id: String, actor: String?)

    suspend fun refresh()
}
