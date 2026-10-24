package com.neochildclinic.feature.statistics.domain

import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.VaccinationItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsRegressionTest {

    @Test
    fun `dismissed or active visits are excluded from valid vaccinations and statistics`() {
        val completedVisit = Vaccination(
            id = "v1",
            patientId = "p1",
            dateGiven = "2026-10-01",
            status = ReminderStatus.COMPLETED,
            visitType = "VACCINATION",
            items = listOf(VaccinationItem(id = "i1", vaccinationId = "v1", vaccineId = "vac1", vaccineName = "Polio", batchId = "b1", batchNumber = "123", quantity = 1))
        )
        val activeVisit = Vaccination(
            id = "v2",
            patientId = "p2",
            dateGiven = "2026-10-02",
            status = ReminderStatus.ACTIVE,
            visitType = "VACCINATION",
            items = listOf(VaccinationItem(id = "i2", vaccinationId = "v2", vaccineId = "vac1", vaccineName = "Polio", batchId = "b1", batchNumber = "123", quantity = 1))
        )
        val dismissedVisit = Vaccination(
            id = "v3",
            patientId = "p3",
            dateGiven = "2026-10-03",
            status = ReminderStatus.DISMISSED,
            visitType = "CONSULTATION",
            items = emptyList()
        )

        val allVisits = listOf(completedVisit, activeVisit, dismissedVisit)
        val validVisits = StatisticsUtils.filterValidVaccinations(allVisits)

        // Only the completed visit should remain valid (Active visits are pending/uncompleted)
        assertEquals(1, validVisits.size)
        assertEquals("v1", validVisits[0].id)
    }
}
