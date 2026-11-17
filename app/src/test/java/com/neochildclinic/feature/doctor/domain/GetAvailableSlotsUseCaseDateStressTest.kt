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

/**
 * Empirical adversarial stress tests for P1-02: Doctor availability date query normalization.
 * Stress-tests GetAvailableSlotsUseCase with varied date formats:
 * - "16 Nov 2026" (Display format, 2-digit day)
 * - "2026-11-16" (ISO format)
 * - "1 Nov 2026" (Display format, 1-digit day)
 * - "01 Nov 2026" (Display format, zero-padded 2-digit day)
 *
 * Verifies that exceptions stored in ISO format correctly block slots or declare FullDayUnavailable.
 */
class GetAvailableSlotsUseCaseDateStressTest {

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

        // Simulates DAO matching where exceptions are stored in ISO format (yyyy-MM-dd)
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
    private val doctorId = "doc-empirical-challenge"

    // 2026-11-16 is a Monday (dayOfWeek = 2 in Calendar / dayOfWeekFor)
    private val monSlot1 = DoctorWeeklySlot(id = "mon-slot-1", doctorId = doctorId, dayOfWeek = 2, startMinute = 540, endMinute = 600) // 09:00 - 10:00
    private val monSlot2 = DoctorWeeklySlot(id = "mon-slot-2", doctorId = doctorId, dayOfWeek = 2, startMinute = 600, endMinute = 660) // 10:00 - 11:00

    // 2026-11-01 is a Sunday (dayOfWeek = 1 in Calendar / dayOfWeekFor)
    private val sunSlot1 = DoctorWeeklySlot(id = "sun-slot-1", doctorId = doctorId, dayOfWeek = 1, startMinute = 600, endMinute = 660) // 10:00 - 11:00
    private val sunSlot2 = DoctorWeeklySlot(id = "sun-slot-2", doctorId = doctorId, dayOfWeek = 1, startMinute = 660, endMinute = 720) // 11:00 - 12:00

    init {
        fakeRepo.weeklySlots = listOf(monSlot1, monSlot2, sunSlot1, sunSlot2)
    }

    // =========================================================================
    // 1. Full Day Exception with Varied Date Formats
    // =========================================================================

    @Test
    fun `full day exception stored as ISO 2026-11-16 honored when queried as 16 Nov 2026`() = runBlocking {
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = "2026-11-16",
                exceptionType = SlotExceptionType.FULL_DAY,
                reason = "Annual Pediatric Summit"
            )
        )

        val result = useCase(doctorId, "16 Nov 2026")
        assertTrue("Expected FullDayUnavailable but was $result", result is DoctorAvailabilityResult.FullDayUnavailable)
        assertEquals("Annual Pediatric Summit", (result as DoctorAvailabilityResult.FullDayUnavailable).reason)
    }

    @Test
    fun `full day exception stored as ISO 2026-11-16 honored when queried as ISO 2026-11-16`() = runBlocking {
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = "2026-11-16",
                exceptionType = SlotExceptionType.FULL_DAY,
                reason = "Annual Pediatric Summit"
            )
        )

        val result = useCase(doctorId, "2026-11-16")
        assertTrue("Expected FullDayUnavailable but was $result", result is DoctorAvailabilityResult.FullDayUnavailable)
        assertEquals("Annual Pediatric Summit", (result as DoctorAvailabilityResult.FullDayUnavailable).reason)
    }

    @Test
    fun `full day exception stored as ISO 2026-11-01 honored when queried as 1 Nov 2026 single-digit day`() = runBlocking {
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = "2026-11-01",
                exceptionType = SlotExceptionType.FULL_DAY,
                reason = "Emergency Leave"
            )
        )

        val result = useCase(doctorId, "1 Nov 2026")
        assertTrue("Expected FullDayUnavailable for 1 Nov 2026 but was $result", result is DoctorAvailabilityResult.FullDayUnavailable)
        assertEquals("Emergency Leave", (result as DoctorAvailabilityResult.FullDayUnavailable).reason)
    }

    @Test
    fun `full day exception stored as ISO 2026-11-01 honored when queried as 01 Nov 2026 padded day`() = runBlocking {
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = "2026-11-01",
                exceptionType = SlotExceptionType.FULL_DAY,
                reason = "Emergency Leave"
            )
        )

        val result = useCase(doctorId, "01 Nov 2026")
        assertTrue("Expected FullDayUnavailable for 01 Nov 2026 but was $result", result is DoctorAvailabilityResult.FullDayUnavailable)
        assertEquals("Emergency Leave", (result as DoctorAvailabilityResult.FullDayUnavailable).reason)
    }

    // =========================================================================
    // 2. Slot-Specific Exception with Varied Date Formats
    // =========================================================================

    @Test
    fun `slot exception stored as ISO 2026-11-16 blocks target slot when queried as 16 Nov 2026`() = runBlocking {
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = "2026-11-16",
                exceptionType = SlotExceptionType.SLOT,
                weeklySlotId = "mon-slot-1"
            )
        )

        val result = useCase(doctorId, "16 Nov 2026")
        assertTrue(result is DoctorAvailabilityResult.Available)
        val available = (result as DoctorAvailabilityResult.Available).slots
        assertEquals(1, available.size)
        assertEquals("mon-slot-2", available.first().weeklySlotId)

        assertFalse(useCase.isSlotStillAvailable(doctorId, "16 Nov 2026", "mon-slot-1"))
        assertTrue(useCase.isSlotStillAvailable(doctorId, "16 Nov 2026", "mon-slot-2"))
    }

    @Test
    fun `slot exception stored as ISO 2026-11-16 blocks target slot when queried as ISO 2026-11-16`() = runBlocking {
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = "2026-11-16",
                exceptionType = SlotExceptionType.SLOT,
                weeklySlotId = "mon-slot-1"
            )
        )

        val result = useCase(doctorId, "2026-11-16")
        assertTrue(result is DoctorAvailabilityResult.Available)
        val available = (result as DoctorAvailabilityResult.Available).slots
        assertEquals(1, available.size)
        assertEquals("mon-slot-2", available.first().weeklySlotId)

        assertFalse(useCase.isSlotStillAvailable(doctorId, "2026-11-16", "mon-slot-1"))
        assertTrue(useCase.isSlotStillAvailable(doctorId, "2026-11-16", "mon-slot-2"))
    }

    @Test
    fun `slot exception stored as ISO 2026-11-01 blocks target slot when queried as 1 Nov 2026`() = runBlocking {
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = "2026-11-01",
                exceptionType = SlotExceptionType.SLOT,
                weeklySlotId = "sun-slot-1"
            )
        )

        val result = useCase(doctorId, "1 Nov 2026")
        assertTrue(result is DoctorAvailabilityResult.Available)
        val available = (result as DoctorAvailabilityResult.Available).slots
        assertEquals(1, available.size)
        assertEquals("sun-slot-2", available.first().weeklySlotId)

        assertFalse(useCase.isSlotStillAvailable(doctorId, "1 Nov 2026", "sun-slot-1"))
        assertTrue(useCase.isSlotStillAvailable(doctorId, "1 Nov 2026", "sun-slot-2"))
    }

    @Test
    fun `slot exception stored as ISO 2026-11-01 blocks target slot when queried as 01 Nov 2026`() = runBlocking {
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = "2026-11-01",
                exceptionType = SlotExceptionType.SLOT,
                weeklySlotId = "sun-slot-1"
            )
        )

        val result = useCase(doctorId, "01 Nov 2026")
        assertTrue(result is DoctorAvailabilityResult.Available)
        val available = (result as DoctorAvailabilityResult.Available).slots
        assertEquals(1, available.size)
        assertEquals("sun-slot-2", available.first().weeklySlotId)

        assertFalse(useCase.isSlotStillAvailable(doctorId, "01 Nov 2026", "sun-slot-1"))
        assertTrue(useCase.isSlotStillAvailable(doctorId, "01 Nov 2026", "sun-slot-2"))
    }

    // =========================================================================
    // 3. Custom Time Range Exception with Varied Formats
    // =========================================================================

    @Test
    fun `custom time exception on 2026-11-16 blocks overlapping slot when queried with 16 Nov 2026`() = runBlocking {
        fakeRepo.exceptions = listOf(
            DoctorSlotException(
                doctorId = doctorId,
                exceptionDate = "2026-11-16",
                exceptionType = SlotExceptionType.SLOT,
                weeklySlotId = null,
                startMinute = 530,
                endMinute = 570 // overlaps monSlot1 (540..600)
            )
        )

        val result = useCase(doctorId, "16 Nov 2026")
        assertTrue(result is DoctorAvailabilityResult.Available)
        val available = (result as DoctorAvailabilityResult.Available).slots
        assertEquals(1, available.size)
        assertEquals("mon-slot-2", available.first().weeklySlotId)
    }

    // =========================================================================
    // 4. Booked Slot Exclusion with Varied Formats
    // =========================================================================

    @Test
    fun `booked slot excluded across 16 Nov 2026 and 2026-11-16 queries`() = runBlocking {
        fakeRepo.bookedSlotIds = setOf("mon-slot-2")

        val resultDisplay = useCase(doctorId, "16 Nov 2026")
        assertTrue(resultDisplay is DoctorAvailabilityResult.Available)
        assertEquals(listOf("mon-slot-1"), (resultDisplay as DoctorAvailabilityResult.Available).slots.map { it.weeklySlotId })

        val resultIso = useCase(doctorId, "2026-11-16")
        assertTrue(resultIso is DoctorAvailabilityResult.Available)
        assertEquals(listOf("mon-slot-1"), (resultIso as DoctorAvailabilityResult.Available).slots.map { it.weeklySlotId })
    }

    // =========================================================================
    // 5. Invalid Date Handling
    // =========================================================================

    @Test
    fun `unparseable date returns empty available slots instead of throwing`() = runBlocking {
        val result = useCase(doctorId, "not-a-valid-date")
        assertTrue(result is DoctorAvailabilityResult.Available)
        assertTrue((result as DoctorAvailabilityResult.Available).slots.isEmpty())
    }
}
