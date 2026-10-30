package com.neochildclinic.feature.statistics.domain

import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.ExpenseCategory
import com.neochildclinic.domain.model.ExpensePaymentMethod
import com.neochildclinic.domain.model.FinanceTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceCalculatorTest {

    private fun income(
        id: String,
        transactionDate: String?,
        timestamp: String,
        visitId: String? = null
    ) = FinanceTransaction(
        id = id,
        timestamp = timestamp,
        transactionDate = transactionDate,
        type = "INCOME",
        category = "CONSULTATION",
        amount = 10000.0,
        paymentMethod = "CASH",
        visitId = visitId
    )

    private fun expense(date: String, rupees: Long) = Expense(
        id = "e-$date-$rupees",
        expenseDate = date,
        category = ExpenseCategory.RENT,
        title = "Rent",
        amountPaise = rupees * 100,
        paymentMethod = ExpensePaymentMethod.CASH
    )

    @Test
    fun `monthly table Expense column and net profit are fed from the expenses table`() {
        val transactions = listOf(
            income("t1", transactionDate = "2026-05-10", timestamp = "2026-05-10 11:00:00"),
            income("t2", transactionDate = "2026-06-12", timestamp = "2026-06-12 11:00:00")
        )
        val expenses = listOf(
            expense("2026-05-05", 500),
            expense("2026-05-20", 250),
            expense("2026-06-10", 9999) // June bucket, not May
        )

        val rows = FinanceCalculator.getMonthlyGroupedData(
            transactions = transactions,
            vaccinations = emptyList(),
            filterMode = "FY 26-27",
            expenses = expenses
        )

        // Keys are 1-based ISO months (e.g. "2026-05" for May), matching YearMonth and
        // the monthKey passed from FinanceTable to MonthlyFinanceDetailsScreen.
        val may = rows.single { it.key == "2026-05" }
        assertEquals(750.0, may.expenses, 0.001)
        assertEquals(10000.0 - 750.0, may.netProfit, 0.001)

        val june = rows.single { it.key == "2026-06" }
        assertEquals(9999.0, june.expenses, 0.001)
    }

    @Test
    fun `month bucket uses the reporting date, not the record creation timestamp`() {
        // transaction_date says May, timestamp says July - the row must land in May.
        val tx = income("t1", transactionDate = "2026-05-10", timestamp = "2026-07-01 09:00:00")

        val rows = FinanceCalculator.getMonthlyGroupedData(
            transactions = listOf(tx),
            vaccinations = emptyList(),
            filterMode = "FY 26-27"
        )

        assertEquals(10000.0, rows.single { it.key == "2026-05" }.revenue, 0.001)
        assertEquals(0.0, rows.single { it.key == "2026-07" }.revenue, 0.001)
    }

    private fun vaccinationIncome(id: String, visitId: String?, amount: Double, timestamp: String) =
        income(id, transactionDate = null, timestamp = timestamp, visitId = visitId).copy(
            category = "VACCINATION",
            amount = amount
        )

    @Test
    fun `duplicate vaccination income for one visit is counted once`() {
        // A visit that wrote two VACCINATION income rows is a real historical failure mode
        // (see FinanceRepositoryImpl). Financial Statistics collapsed these; Full Report and
        // Overview summed both, so the clinic saw two revenue totals for the same month.
        val rows = listOf(
            vaccinationIncome("t1", "visit-1", 500.0, "2026-05-10T09:00:00+00:00"),
            vaccinationIncome("t2", "visit-1", 500.0, "2026-05-10T09:05:00+00:00"),
            vaccinationIncome("t3", "visit-2", 300.0, "2026-05-11T09:00:00+00:00")
        )
        val effective = FinanceCalculator.effectiveIncome(rows)
        assertEquals(2, effective.size)
        assertEquals(800.0, effective.sumOf { it.amount }, 0.001)
        // the newest row of the duplicated visit wins
        assertEquals(setOf("t2", "t3"), effective.map { it.id }.toSet())
    }

    @Test
    fun `non-vaccination income is never collapsed`() {
        val rows = listOf(
            income("a", "2026-05-10", "2026-05-10T09:00:00+00:00", visitId = "visit-1").copy(amount = 100.0),
            income("b", "2026-05-10", "2026-05-10T10:00:00+00:00", visitId = "visit-1").copy(amount = 250.0)
        )
        assertEquals(2, FinanceCalculator.effectiveIncome(rows).size)
    }

    @Test
    fun `vaccination income without a visit id is kept as its own row`() {
        val rows = listOf(
            vaccinationIncome("t1", null, 100.0, "2026-05-10T09:00:00+00:00"),
            vaccinationIncome("t2", null, 200.0, "2026-05-10T10:00:00+00:00")
        )
        assertEquals(2, FinanceCalculator.effectiveIncome(rows).size)
    }

    @Test
    fun `resolveReportingDate prefers transaction_date then visit date then timestamp`() {
        val withDate = income("t1", transactionDate = "2026-05-10", timestamp = "2026-06-15T10:00:00+00:00")
        assertEquals("2026-05-10", FinanceCalculator.resolveReportingDate(withDate))

        val withoutDate = income("t2", transactionDate = null, timestamp = "2026-06-15T10:00:00+00:00", visitId = "v1")
        assertEquals(
            "2026-05-10",
            FinanceCalculator.resolveReportingDate(withoutDate, mapOf("v1" to "2026-05-10"))
        )
        assertEquals("2026-06-15T10:00:00+00:00", FinanceCalculator.resolveReportingDate(withoutDate))
    }
}
