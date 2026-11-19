package com.neochildclinic.uifix2

import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.feature.patient.presentation.patientLoadErrorState
import com.neochildclinic.feature.reminder.data.ReminderDueListProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Pins the two fixes that are easy to regress: shared id lookup on the due list, and a failing patient flow leaving the screen in the error state instead of an endless spinner. */
class DueLookupAndPatientErrorTest {

    @Test
    fun `active and terminal reminder groups both link to the visit by id`() {
        val visit = VisitEntity(id = "visit-1", patientId = "p-1", dateGiven = "12 Oct 2026", doctor = "Dr A")
        val active = ReminderEntity(
            id = "r-active", patientId = "p-1", originalVisitId = "visit-1",
            vaccineName = "BCG", dueDate = "2026-11-01", status = "ACTIVE"
        )
        val completed = ReminderEntity(
            id = "r-done", patientId = "p-1", originalVisitId = "visit-1",
            vaccineName = "OPV", dueDate = "2026-10-20", status = "COMPLETED",
            reminderEnabled = false, completionDate = "2026-10-20T10:00:00.000Z"
        )
        val orphan = ReminderEntity(
            id = "r-orphan", patientId = "p-1", originalVisitId = "no-such-visit",
            vaccineName = "DPT", dueDate = "2026-10-21", status = "COMPLETED",
            reminderEnabled = false
        )

        val processed = ReminderDueListProcessor.process(
            vaccEntities = listOf(visit),
            reminderEntities = listOf(active, completed, orphan)
        )

        val activeRow = processed.first { it.status == ReminderStatus.ACTIVE }
        assertEquals("visit-1", activeRow.id)
        assertEquals(visit.dateGiven, activeRow.dateGiven)

        val terminalRows = processed.filter { it.status == ReminderStatus.COMPLETED }
        assertEquals(2, terminalRows.size)
        assertEquals(visit.dateGiven, terminalRows.first { it.id == "visit-1" }.originalDateGiven)
        assertEquals("p-1", terminalRows.first { it.id != "visit-1" }.patientId)
    }

    @Test
    fun `patient error state clears loading and keeps the failure message`() {
        val state = patientLoadErrorState(IllegalStateException("room failed"))
        assertFalse("A failed load must not leave the spinner running", state.isLoading)
        assertEquals("room failed", state.error)

        val fallback = patientLoadErrorState(IllegalStateException())
        assertFalse(fallback.isLoading)
        assertEquals("Could not load patients", fallback.error)
    }
}
