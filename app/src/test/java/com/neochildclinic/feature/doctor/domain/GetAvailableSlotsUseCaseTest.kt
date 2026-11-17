package com.neochildclinic.feature.doctor.domain

import com.neochildclinic.domain.model.DoctorAvailabilityResult
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.DoctorWeeklySlot
import com.neochildclinic.domain.model.SlotExceptionType
import com.neochildclinic.feature.doctor.domain.repository.DoctorAvailabilityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetAvailableSlotsUseCaseTest {

    private class FakeDoctorAvailabilityRepository : DoctorAvailabilityRepository {
        var weeklySlots: List<DoctorWeeklySlot> = emptyList()
        var exceptions: List<DoctorSlotException> = emptyList()
        var bookedSlotIds: Set<String> = emptySet()

        override fun getWeeklySlots(doctorId: String): Flow<List<DoctorWeeklySlot>> = flowOf(weeklySlots)
        override fun getExceptions(doctorId: String): Flow<List<DoctorSlotException>> = flowOf(exceptions)
        override suspend fun getWeeklySlotById(id: String): DoctorWeeklySlot? = weeklySlots.find { it.id == id }
        override suspend fun getWeeklySlotsByIds(ids: List<String>): List<DoctorWeeklySlot> = weeklySlots.filter { it.id in ids }
        override suspend fun getBookedSlotIds(doctorId: String, dates: List<String>): Set<String> = bookedSlotIds
        override suspend fun getActiveWeeklySlotsForDay(doctorId: String, dayOfWeek: Int): List<DoctorWeeklySlot> =
            weeklySlots.filter { it.doctorId == doctorId && it.dayOfWeek == dayOfWeek && it.isActive }
        override suspend fun getExceptionsForDate(doctorId: String, date: String): List<DoctorSlotException> =
            exceptions.filter { it.doctorId == doctorId && it.exceptionDate == date }
        override suspend fun addWeeklySlot(doctorId: String, dayOfWeek: Int, startMinute: Int, endMinute: Int, actor: String?) {}
        override suspend fun removeWeeklySlot(slotId: String, actor: String?) {}
        override suspend fun addException(exception: DoctorSlotException, actor: String?) {}
        override suspend fun deleteException(id: String, actor: String?) {}
        override suspend fun refresh() {}
    }

    private val fakeRepo = FakeDoctorAvailabilityRepository()
    private val useCase = GetAvailableSlotsUseCase(fakeRepo)

    private val doctorId = "doctor-uuid-123"
    // 2026-10-12 is a Monday (dayOfWeek = 2 in Calendar / dayOfWeekFor calculation)
    private val date = "2026-10-12"

    private val slot1 = DoctorWeeklySlot(id = "slot-1", doctorId = doctorId, dayOfWeek = 2, startMinute = 540, endMinute = 600) // 9:00 - 10:00
    private val slot2 = DoctorWeeklySlot(id = "slot-2", doctorId = doctorId, dayOfWeek = 2, startMinute = 600, endMinute = 660) // 10:00 - 11:00
    private val slot3 = DoctorWeeklySlot(id = "slot-3", doctorId = doctorId, dayOfWeek = 2, startMinute = 660, endMinute = 720) // 11:00 - 12:00

    @Test
    fun `all active weekly slots returned when no bookings or exceptions`() = runBlocking {
        fakeRepo.weeklySlots = listOf(slot1, slot2, slot3)
        fakeRepo.bookedSlotIds = emptySet()

        val result = useCase(doctorId, date)
        assertTrue(result is DoctorAvailabilityResult.Available)
        val available = (result as DoctorAvailabilityResult.Available).slots
        assertEquals(3, available.size)
        assertEquals(listOf("slot-1", "slot-2", "slot-3"), available.map { it.weeklySlotId })
    }

    @Test
    fun `booked slot is excluded from available slots`() = runBlocking {
        fakeRepo.weeklySlots = listOf(slot1, slot2, slot3)
        fakeRepo.bookedSlotIds = setOf("slot-2")

        val result = useCase(doctorId, date)
        assertTrue(result is DoctorAvailabilityResult.Available)
        val available = (result as DoctorAvailabilityResult.Available).slots
        assertEquals(2, available.size)
        assertEquals(listOf("slot-1", "slot-3"), available.map { it.weeklySlotId })
    }

    @Test
    fun `multiple booked slots are excluded`() = runBlocking {
        fakeRepo.weeklySlots = listOf(slot1, slot2, slot3)
        fakeRepo.bookedSlotIds = setOf("slot-1", "slot-3")

        val result = useCase(doctorId, date)
        assertTrue(result is DoctorAvailabilityResult.Available)
        val available = (result as DoctorAvailabilityResult.Available).slots
        assertEquals(1, available.size)
        assertEquals("slot-2", available.first().weeklySlotId)
    }

    @Test
    fun `isSlotStillAvailable returns false for booked slot and true for available slot`() = runBlocking {
        fakeRepo.weeklySlots = listOf(slot1, slot2, slot3)
        fakeRepo.bookedSlotIds = setOf("slot-2")

        assertFalse(useCase.isSlotStillAvailable(doctorId, date, "slot-2"))
        assertTrue(useCase.isSlotStillAvailable(doctorId, date, "slot-1"))
        assertTrue(useCase.isSlotStillAvailable(doctorId, date, "slot-3"))
    }

    @Test
    fun `full day exception returns FullDayUnavailable`() = runBlocking {
        fakeRepo.weeklySlots = listOf(slot1, slot2, slot3)
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = date,
                exceptionType = SlotExceptionType.FULL_DAY,
                reason = "Annual Conference"
            )
        )

        val result = useCase(doctorId, date)
        assertTrue(result is DoctorAvailabilityResult.FullDayUnavailable)
        assertEquals("Annual Conference", (result as DoctorAvailabilityResult.FullDayUnavailable).reason)
    }

    @Test
    fun `slot exception excludes target slot`() = runBlocking {
        fakeRepo.weeklySlots = listOf(slot1, slot2, slot3)
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = date,
                exceptionType = SlotExceptionType.SLOT,
                weeklySlotId = "slot-1"
            )
        )

        val result = useCase(doctorId, date)
        assertTrue(result is DoctorAvailabilityResult.Available)
        val available = (result as DoctorAvailabilityResult.Available).slots
        assertEquals(listOf("slot-2", "slot-3"), available.map { it.weeklySlotId })
    }

    @Test
    fun `no schedule configured returns NoScheduleConfigured`() = runBlocking {
        fakeRepo.weeklySlots = emptyList()

        val result = useCase(doctorId, date)
        assertEquals(DoctorAvailabilityResult.NoScheduleConfigured, result)
    }

    @Test
    fun `full day exception honored when display date format is passed`() = runBlocking {
        fakeRepo.weeklySlots = listOf(slot1, slot2, slot3)
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = "2026-10-12",
                exceptionType = SlotExceptionType.FULL_DAY,
                reason = "Attending Conference"
            )
        )

        val result = useCase(doctorId, "12 Oct 2026")
        assertTrue(result is DoctorAvailabilityResult.FullDayUnavailable)
        assertEquals("Attending Conference", (result as DoctorAvailabilityResult.FullDayUnavailable).reason)
    }

    @Test
    fun `slot exception honored when display date format is passed`() = runBlocking {
        fakeRepo.weeklySlots = listOf(slot1, slot2, slot3)
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = "2026-10-12",
                exceptionType = SlotExceptionType.SLOT,
                weeklySlotId = "slot-2"
            )
        )

        val result = useCase(doctorId, "12 Oct 2026")
        assertTrue(result is DoctorAvailabilityResult.Available)
        val available = (result as DoctorAvailabilityResult.Available).slots
        assertEquals(listOf("slot-1", "slot-3"), available.map { it.weeklySlotId })
    }
}
