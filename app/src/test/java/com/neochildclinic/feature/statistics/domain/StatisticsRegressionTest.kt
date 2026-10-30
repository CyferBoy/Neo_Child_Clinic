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

    @Test
    fun `deleted or cancelled transactions and expenses are excluded from monthly stats calculation`() {
        val completedVisit = Vaccination(
            id = "v1",
            patientId = "p1",
            dateGiven = "2026-10-01",
            status = ReminderStatus.COMPLETED,
            visitType = "VACCINATION",
            items = listOf(VaccinationItem(id = "i1", vaccinationId = "v1", vaccineId = "vac1", vaccineName = "Polio", batchId = "b1", batchNumber = "123", quantity = 1))
        )
        val validVisits = StatisticsUtils.filterValidVaccinations(listOf(completedVisit))
        val validVisitIds = validVisits.map { it.id }.toSet()
        val existingPatientIds = setOf("p1")

        val txValid = com.neochildclinic.domain.model.FinanceTransaction(
            id = "tx1",
            timestamp = "2026-10-01 10:00:00",
            transactionDate = "2026-10-01",
            type = "INCOME",
            category = "VACCINATION",
            amount = 1500.0,
            paymentMethod = "CASH",
            patientId = "p1",
            visitId = "v1"
        )
        val txDeletedVisit = com.neochildclinic.domain.model.FinanceTransaction(
            id = "tx2",
            timestamp = "2026-10-01 11:00:00",
            transactionDate = "2026-10-01",
            type = "INCOME",
            category = "VACCINATION",
            amount = 2000.0,
            paymentMethod = "ONLINE",
            patientId = "p1",
            visitId = "deleted-visit" // Not in validVisitIds
        )
        val txDeletedPatient = com.neochildclinic.domain.model.FinanceTransaction(
            id = "tx3",
            timestamp = "2026-10-01 12:00:00",
            transactionDate = "2026-10-01",
            type = "INCOME",
            category = "CONSULTATION",
            amount = 500.0,
            paymentMethod = "CASH",
            patientId = "deleted-patient" // Not in existingPatientIds
        )
        val txMarkedDeleted = com.neochildclinic.domain.model.FinanceTransaction(
            id = "tx4",
            timestamp = "2026-10-01 13:00:00",
            transactionDate = "2026-10-01",
            type = "INCOME",
            category = "CONSULTATION",
            amount = 800.0,
            paymentMethod = "CASH",
            remarks = "Cancelled visit [DELETED]"
        )

        val allTxns = listOf(txValid, txDeletedVisit, txDeletedPatient, txMarkedDeleted)

        val cleanTxns = allTxns.filter { tx ->
            if (tx.amount <= 0) return@filter false
            if (tx.remarks?.contains("[DELETED]", ignoreCase = true) == true) return@filter false
            if (tx.remarks?.contains("is_deleted", ignoreCase = true) == true) return@filter false
            if (!tx.patientId.isNullOrBlank() && tx.patientId !in existingPatientIds) return@filter false
            if (tx.category.equals("VACCINATION", true) && !tx.visitId.isNullOrBlank()) {
                if (tx.visitId !in validVisitIds) return@filter false
            }
            true
        }

        assertEquals(1, cleanTxns.size)
        assertEquals("tx1", cleanTxns[0].id)
    }
}
