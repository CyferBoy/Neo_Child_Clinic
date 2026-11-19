package com.neochildclinic.clinicalfix

import com.neochildclinic.domain.model.NextVaccinationSummary
import com.neochildclinic.domain.model.Reminder
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.borrowed.data.requireValidReturnBatchExpiry
import com.neochildclinic.domain.repository.NewBatchInfo
import com.neochildclinic.feature.reminder.presentation.resolveDueReminders
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CL-03 (shell due reminders) and CL-05 (expired new batch on return). */
class ClinicalFixRegressionTest {

    private fun shellReminder(id: String) = Reminder(
        id = id,
        patientId = "p1",
        originalVisitId = "visit-missing-locally",
        vaccineName = "BCG",
        dueDate = "2026-12-01",
        status = "ACTIVE"
    )

    private fun dueRow(reminderId: String, visitId: String = "synthetic-random-uuid") = Vaccination(
        id = visitId,
        patientId = "p1"
    ).copy(
        nextVaccinations = listOf(
            NextVaccinationSummary(reminderId = reminderId, type = "VACCINATION",
                vaccineNames = listOf("BCG"), dueDate = "2026-12-01")
        )
    )

    @Test
    fun `CL-03 shell row resolves the real reminder by id when the visit lookup finds nothing`() = runBlocking {
        val reminder = shellReminder("real-r1")
        var lookupCount = 0

        val resolved = resolveDueReminders(dueRow("real-r1"), byVisit = emptyList()) { id ->
            lookupCount++
            if (id == reminder.id) reminder else null
        }

        assertEquals(listOf(reminder), resolved)
        assertEquals(1, lookupCount)
    }

    @Test
    fun `CL-03 normal row keeps the visit lookup and never falls back to id lookup`() = runBlocking {
        val inVisit = shellReminder("r1").copy(originalVisitId = "visit-1")
        val other = shellReminder("r2").copy(originalVisitId = "visit-1")
        var lookupCount = 0

        val resolved = resolveDueReminders(dueRow("r1", visitId = "visit-1"), byVisit = listOf(inVisit, other)) {
            lookupCount++
            null
        }

        assertEquals(listOf(inVisit), resolved)
        assertEquals(0, lookupCount)
    }

    @Test
    fun `CL-05 an expired new batch is rejected on return`() {
        val error = runCatching {
            requireValidReturnBatchExpiry(NewBatchInfo(batchNumber = "RET-1", expiryDate = "01 Jan 2020"))
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("expired"))
    }

    @Test
    fun `CL-05 an unexpired new batch is accepted on return`() {
        requireValidReturnBatchExpiry(NewBatchInfo(batchNumber = "RET-2", expiryDate = "01 Jan 2099"))
    }
}
