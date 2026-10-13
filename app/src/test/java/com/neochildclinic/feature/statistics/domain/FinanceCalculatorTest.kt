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

        // Keys are 0-based-Calendar months, the same key FinanceTable's onMonthClick
        // hands to MonthlyFinanceDetailsScreen.
        val may = rows.single { it.key == "2026-04" }
        assertEquals(750.0, may.expenses, 0.001)
        assertEquals(10000.0 - 750.0, may.netProfit, 0.001)

        val june = rows.single { it.key == "2026-05" }
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

        assertEquals(10000.0, rows.single { it.key == "2026-04" }.revenue, 0.001)
        assertEquals(0.0, rows.single { it.key == "2026-06" }.revenue, 0.001)
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
