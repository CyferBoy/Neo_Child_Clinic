package com.neochildclinic.moneyfix

import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.finance.domain.FinanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class MoneyFixRegressionTest {

    private fun freeVaccinationIncome() = FinanceTransaction(
        id = "free-vax",
        timestamp = "2026-05-10T09:00:00+05:30",
        transactionDate = "2026-05-10",
        type = "INCOME",
        category = "VACCINATION",
        amount = 0.0,
        paymentMethod = "CASH",
        visitId = "visit-free",
        remarks = FinanceCalculator.buildVaccinationRemarks("Polio", 450.0)
    )

    @Test
    fun `zero amount vaccination row with cogs marker is kept while zero amount non vaccination row is dropped`() {
        val freeVax = freeVaccinationIncome()
        val zeroConsultation = freeVax.copy(
            id = "zero-consult",
            category = "CONSULTATION",
            visitId = null,
            remarks = null
        )

        assertTrue(FinanceCalculator.isCleanTransaction(freeVax))
        assertFalse(FinanceCalculator.isCleanTransaction(zeroConsultation))
    }

    @Test
    fun `free vaccination cogs cost is counted even though its income amount is zero`() {
        val vaccination = Vaccination(
            id = "visit-free",
            dateGiven = "2026-05-10",
            status = ReminderStatus.COMPLETED
        )

        val stats = FinanceCalculator.calculateFinanceStats(
            transactions = listOf(freeVaccinationIncome()),
            vaccinationsForCogs = listOf(vaccination)
        )

        assertEquals(0.0, stats.totalRevenue, 0.001)
        assertEquals(450.0, stats.vaccineCost, 0.001)
        assertTrue(stats.isProfitComplete)
        assertEquals(-450.0, stats.grossProfit, 0.001)
    }

    @Test
    fun `date only transaction is bucketed into May under a UTC device timezone`() {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val tx = FinanceTransaction(
                id = "may-1",
                timestamp = "2026-05-01T09:00:00+05:30",
                transactionDate = "2026-05-01",
                type = "INCOME",
                category = "CONSULTATION",
                amount = 100.0,
                paymentMethod = "CASH"
            )

            val rows = FinanceCalculator.getMonthlyGroupedData(
                transactions = listOf(tx),
                vaccinations = emptyList(),
                filterMode = "FY 26-27"
            )

            assertEquals(100.0, rows.single { it.key == "2026-05" }.revenue, 0.001)
            assertEquals(0.0, rows.single { it.key == "2026-04" }.revenue, 0.001)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }
}
