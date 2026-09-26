package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.DoctorWeeklySlot
import kotlinx.coroutines.flow.Flow

interface DoctorAvailabilityRepository {
    fun getWeeklySlots(doctorId: String): Flow<List<DoctorWeeklySlot>>
    fun getExceptions(doctorId: String): Flow<List<DoctorSlotException>>
    suspend fun getWeeklySlotById(id: String): DoctorWeeklySlot?
    suspend fun getActiveWeeklySlotsForDay(doctorId: String, dayOfWeek: Int): List<DoctorWeeklySlot>
    suspend fun getExceptionsForDate(doctorId: String, date: String): List<DoctorSlotException>
    suspend fun addWeeklySlot(
        doctorId: String,
        dayOfWeek: Int,
        startMinute: Int,
        endMinute: Int,
        actor: String?
    )
    suspend fun removeWeeklySlot(slotId: String, actor: String?)
    suspend fun addException(exception: DoctorSlotException, actor: String?)
    suspend fun deleteException(id: String, actor: String?)
    suspend fun refresh()
}