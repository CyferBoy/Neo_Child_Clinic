package com.neochildclinic.feature.dashboard.presentation

import com.neochildclinic.domain.model.ConsultationTodo
import com.neochildclinic.domain.model.VaccinationTodo
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure counting and partition logic test for Today's Patients segments.
 * Tests [calculateTodayPatientCounts] and date utilities to verify:
 * 1. Consultation count = New + Follow-up on all dates
 * 2. Emitted counts match the sum of list items (pending + visited) on both past and current dates
 * 3. Preserves all valid appointments for the same patient without dropping or biasing visit types
 * 4. Safe LocalDate parsing and boundary comparison without raw string comparison bugs
 * 5. Handles walk-in patients (null or blank patientId)
 */
class TodayPatientCountingTest {

    private val referenceToday = LocalDate.of(2026, 11, 3)

    @Test
    fun `today counts match sum of pending and visited visits without dropping appointments`() {
        val pendingConsults = listOf(
            ConsultationTodo(id = "1", patientId = "p1", name = "A", mobile = "", address = "", todoDate = "2026-11-03", visitType = "NEW"),
            ConsultationTodo(id = "2", patientId = "p2", name = "B", mobile = "", address = "", todoDate = "2026-11-03", visitType = "FOLLOW_UP"),
            ConsultationTodo(id = "3", patientId = "p1", name = "A", mobile = "", address = "", todoDate = "2026-11-03", visitType = "NEW")
        )
        val visitedConsults = listOf(
            ConsultationTodo(id = "4", patientId = "p3", name = "C", mobile = "", address = "", todoDate = "2026-11-03", visitType = "NEW", status = "COMPLETED")
        )

        val counts = calculateTodayPatientCounts(
            pendingConsult = pendingConsults,
            visitedConsult = visitedConsults,
            pendingVacc = emptyList(),
            visitedVacc = emptyList(),
            selectedDate = "2026-11-03",
            todayDate = referenceToday
        )

        assertEquals("New visits count", 3, counts.newCount)
        assertEquals("Follow-up visits count", 1, counts.followUpCount)
        assertEquals("Total consult must equal new + follow-up", 4, counts.consultationCount)
        assertEquals("Total consult matches list items size", pendingConsults.size + visitedConsults.size, counts.consultationCount)
    }

    @Test
    fun `past date counts match sum of emitted list items to avoid UI contradictions`() {
        val pendingConsults = listOf(
            ConsultationTodo(id = "1", patientId = "p1", name = "A", mobile = "", address = "", todoDate = "2026-11-01", visitType = "NEW", status = "PENDING")
        )
        val visitedConsults = listOf(
            ConsultationTodo(id = "2", patientId = "p2", name = "B", mobile = "", address = "", todoDate = "2026-11-01", visitType = "NEW", status = "COMPLETED"),
            ConsultationTodo(id = "3", patientId = "p3", name = "C", mobile = "", address = "", todoDate = "2026-11-01", visitType = "FOLLOW_UP", status = "COMPLETED")
        )

        val counts = calculateTodayPatientCounts(
            pendingConsult = pendingConsults,
            visitedConsult = visitedConsults,
            pendingVacc = emptyList(),
            visitedVacc = emptyList(),
            selectedDate = "2026-11-01",
            todayDate = referenceToday
        )

        assertEquals("New count on past date", 2, counts.newCount)
        assertEquals("Follow-up count on past date", 1, counts.followUpCount)
        assertEquals("Consultation count matches list size", pendingConsults.size + visitedConsults.size, counts.consultationCount)
        assertEquals("Consultation count equals new + follow-up", counts.newCount + counts.followUpCount, counts.consultationCount)
    }

    @Test
    fun `vaccination counts match list size on any date`() {
        val vaccs = listOf(
            VaccinationTodo(id = "1", patientId = "p1", name = "A", mobile = "", vaccineNames = "BCG", address = "", todoDate = "2026-11-03"),
            VaccinationTodo(id = "2", patientId = "p1", name = "A", mobile = "", vaccineNames = "Polio", address = "", todoDate = "2026-11-03"),
            VaccinationTodo(id = "3", patientId = "p2", name = "B", mobile = "", vaccineNames = "MMR", address = "", todoDate = "2026-11-03")
        )

        val counts = calculateTodayPatientCounts(
            pendingConsult = emptyList(),
            visitedConsult = emptyList(),
            pendingVacc = vaccs,
            visitedVacc = emptyList(),
            selectedDate = "2026-11-03",
            todayDate = referenceToday
        )

        assertEquals(3, counts.vaccinationCount)
    }

    @Test
    fun `same patient with both NEW and FOLLOW_UP visits on same date preserves both visits`() {
        val consults = listOf(
            ConsultationTodo(id = "1", patientId = "p1", name = "A", mobile = "", address = "", todoDate = "2026-11-03", visitType = "NEW"),
            ConsultationTodo(id = "2", patientId = "p1", name = "A", mobile = "", address = "", todoDate = "2026-11-03", visitType = "FOLLOW_UP")
        )

        val counts = calculateTodayPatientCounts(
            pendingConsult = consults,
            visitedConsult = emptyList(),
            pendingVacc = emptyList(),
            visitedVacc = emptyList(),
            selectedDate = "2026-11-03",
            todayDate = referenceToday
        )

        assertEquals(1, counts.newCount)
        assertEquals(1, counts.followUpCount)
        assertEquals(2, counts.consultationCount)
    }

    @Test
    fun `unregistered walk-in patients without patientId are counted individually by id`() {
        val consults = listOf(
            ConsultationTodo(id = "1", patientId = null, name = "WalkIn 1", mobile = "", address = "", todoDate = "2026-11-03", visitType = "NEW"),
            ConsultationTodo(id = "2", patientId = "", name = "WalkIn 2", mobile = "", address = "", todoDate = "2026-11-03", visitType = "NEW")
        )

        val counts = calculateTodayPatientCounts(
            pendingConsult = consults,
            visitedConsult = emptyList(),
            pendingVacc = emptyList(),
            visitedVacc = emptyList(),
            selectedDate = "2026-11-03",
            todayDate = referenceToday
        )

        assertEquals(2, counts.newCount)
        assertEquals(0, counts.followUpCount)
        assertEquals(2, counts.consultationCount)
    }

    @Test
    fun `safe date parsing and boundary comparison avoids string comparison bugs`() {
        // Standard ISO date
        assertEquals(LocalDate.of(2026, 11, 3), parseDateSafely("2026-11-03", referenceToday))

        // Invalid date string falls back to default safely without crashing
        assertEquals(referenceToday, parseDateSafely("invalid-date", referenceToday))

        // Blank string falls back to default safely
        assertEquals(referenceToday, parseDateSafely("", referenceToday))

        // Boundary comparison
        assertTrue("Today is today or future", isDateTodayOrFuture("2026-11-03", referenceToday))
        assertTrue("Tomorrow is future", isDateTodayOrFuture("2026-11-04", referenceToday))
        assertFalse("Yesterday is not future", isDateTodayOrFuture("2026-11-02", referenceToday))
    }
}
