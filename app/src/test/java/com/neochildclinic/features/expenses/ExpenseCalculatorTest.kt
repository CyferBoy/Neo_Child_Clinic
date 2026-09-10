package com.neochildclinic.features.expenses

import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.ExpenseCategory
import com.neochildclinic.domain.model.ExpensePaymentMethod
import com.neochildclinic.features.statistics.ExpenseCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseCalculatorTest {

    private fun expense(dateIso: String, rupees: Long, category: ExpenseCategory = ExpenseCategory.RENT) = Expense(
        id = "e-$dateIso-$rupees",
        expenseDate = dateIso,
        category = category,
        title = "Test expense",
        amountPaise = rupees * 100,
        paymentMethod = ExpensePaymentMethod.CASH
    )

    // Task section 8 example: 1 April 2026 - 31 March 2027 = Financial Year 26/27.
    private val fy2627 = listOf(
        expense("2026-04-01", 1000),  // first day of FY 26/27
        expense("2026-12-25", 2000),  // mid FY 26/27
        expense("2027-03-31", 500),   // last day of FY 26/27
        expense("2027-04-01", 9999),  // first day of the *next* FY - must be excluded
        expense("2026-03-31", 9999)   // last day of the *previous* FY - must be excluded
    )

    @Test
    fun `financial year window is 1 April to 31 March`() {
        val total = ExpenseCalculator.totalExpensesPaiseInPeriod(fy2627, "FY 26-27", fyQuarter = 0, selectedMonth = -1)
        assertEquals(350000L, total) // (1000 + 2000 + 500) rupees, in paise
    }

    @Test
    fun `overall filter includes everything regardless of date`() {
        val total = ExpenseCalculator.totalExpensesPaiseInPeriod(fy2627, "Overall", fyQuarter = 0, selectedMonth = -1)
        assertEquals(fy2627.sumOf { it.amountPaise }, total)
    }

    @Test
    fun `quarter filter narrows to that FY quarter only`() {
        // Q1 of FY 26-27 = Apr-Jun 2026.
        val total = ExpenseCalculator.totalExpensesPaiseInPeriod(fy2627, "FY 26-27", fyQuarter = 1, selectedMonth = -1)
        assertEquals(100000L, total) // only the 1 April 2026 entry
    }

    @Test
    fun `filterExpensesByPeriod excludes out-of-window rows entirely`() {
        val filtered = ExpenseCalculator.filterExpensesByPeriod(fy2627, "FY 26-27", fyQuarter = 0, selectedMonth = -1)
        assertEquals(3, filtered.size)
        assert(filtered.none { it.expenseDate == "2027-04-01" || it.expenseDate == "2026-03-31" })
    }

    @Test
    fun `net income is total income minus total expenses`() {
        // Total Income = Vaccination Income + Consultation Income (task section 7),
        // represented here as a plain rupee figure the way FinanceCalculator already
        // produces it (FinanceStatsData.totalRevenue).
        val netIncome = ExpenseCalculator.netIncomeRupees(totalIncomeRupees = 10000.0, totalExpensesPaise = 350000L)
        assertEquals(6500.0, netIncome, 0.001) // 10000 - 3500
    }

    @Test
    fun `net income can go negative when expenses exceed income`() {
        val netIncome = ExpenseCalculator.netIncomeRupees(totalIncomeRupees = 1000.0, totalExpensesPaise = 200000L)
        assertEquals(-1000.0, netIncome, 0.001) // 1000 - 2000
    }

    @Test
    fun `amount precision survives paise round-trip without floating point drift`() {
        // A classic float-precision trap: 19.99 rupees repeated many times. Money is
        // carried as Long paise throughout, so summing never accumulates binary-float
        // rounding error the way summing 0.1-style Doubles would.
        val expenses = (1..100).map { expense("2026-05-0${(it % 9) + 1}", rupees = 0) }
            .mapIndexed { i, e -> e.copy(amountPaise = 1999L, id = "p-$i") }
        val total = expenses.sumOf { it.amountPaise }
        assertEquals(199900L, total) // exactly 100 * 1999, no drift
    }
}
