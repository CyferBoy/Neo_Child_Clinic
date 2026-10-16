package com.neochildclinic.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/**
 * nextDueDate drives DateClassifier and every "Due Today"/"Overdue" count, so it must pick
 * the chronologically earliest due date. The stored format ("d MMM yyyy", Constants.DATE_FORMAT)
 * is not zero-padded, so a plain string comparison is wrong: "16 Oct 2026" sorts before
 * "9 Oct 2026" as text while being a month later as a date.
 */
class VaccinationNextDueDateTest {

    private val originalZone = TimeZone.getDefault()

    @org.junit.After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    private fun summary(dueDate: String) = NextVaccinationSummary(
        reminderId = "r-$dueDate",
        type = "VACCINATION",
        vaccineNames = listOf("Pentavalent"),
        dueDate = dueDate
    )

    @Test
    fun `earliest due date wins even when its day of month is not zero padded`() {
        val visit = Vaccination(
            id = "v1",
            nextVaccinations = listOf(summary("16 Oct 2026"), summary("9 Oct 2026"))
        )
        assertEquals("9 Oct 2026", visit.nextDueDate)
    }

    @Test
    fun `earliest due date wins across month boundaries`() {
        val visit = Vaccination(
            id = "v1",
            nextVaccinations = listOf(summary("2 Jan 2027"), summary("30 Dec 2026"))
        )
        assertEquals("30 Dec 2026", visit.nextDueDate)
    }

    @Test
    fun `single reminder is returned as is`() {
        val visit = Vaccination(id = "v1", nextVaccinations = listOf(summary("5 Nov 2026")))
        assertEquals("5 Nov 2026", visit.nextDueDate)
    }

    @Test
    fun `no reminders yields an empty due date`() {
        assertEquals("", Vaccination(id = "v1").nextDueDate)
    }

    @Test
    fun `an unparseable due date does not mask a parseable earlier one`() {
        val visit = Vaccination(
            id = "v1",
            nextVaccinations = listOf(summary("not-a-date"), summary("9 Oct 2026"))
        )
        assertEquals("9 Oct 2026", visit.nextDueDate)
    }

    @Test
    fun `ordering holds on a device in a timezone ahead of the clinic`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        val visit = Vaccination(
            id = "v1",
            nextVaccinations = listOf(summary("16 Oct 2033"), summary("9 Oct 2033"))
        )
        assertEquals("9 Oct 2033", visit.nextDueDate)
    }
}