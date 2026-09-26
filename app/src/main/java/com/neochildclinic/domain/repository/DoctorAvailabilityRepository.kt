package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.DoctorWeeklySlot

interface DoctorAvailabilityRepository {
    suspend fun getActiveWeeklySlotsForDay(doctorId: String, dayOfWeek: Int): List<DoctorWeeklySlot>
    suspend fun getExceptionsForDate(doctorId: String, date: String): List<DoctorSlotException>
    suspend fun refresh()
}