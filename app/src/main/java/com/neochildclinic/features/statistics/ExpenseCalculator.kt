package com.neochildclinic.features.statistics

import com.neochildclinic.domain.model.Expense

/**
 * Additive financial-integration helper for the Expenses feature (task section 7/8).
 * Deliberately separate from FinanceCalculator, which is left completely untouched -
 * this reuses FinanceCalculator's existing financial-year window resolution
 * (StatisticsUtils.isDateInFilter, the same function FinanceTab already uses to filter
 * finance_transactions/vaccinations) applied to the new, separate `expenses` table
 * instead. vaccinations/consultations income (FinanceStatsData.totalRevenue) and
 * expenses.amountPaise are never merged into one table - only combined arithmetically
 * here, at read time, for the Total Income / Total Expenses / Net Income figures.
 */
object ExpenseCalculator {

    /** Sum of amountPaise for expenses whose expense_date falls in the given FY window. */
    fun totalExpensesPaiseInPeriod(
        expenses: List<Expense>,
        filterMode: String,
        fyQuarter: Int,
        selectedMonth: Int
    ): Long = filterExpensesByPeriod(expenses, filterMode, fyQuarter, selectedMonth)
        .sumOf { it.amountPaise }

    fun filterExpensesByPeriod(
        expenses: List<Expense>,
        filterMode: String,
        fyQuarter: Int,
        selectedMonth: Int
    ): List<Expense> = expenses.filter {
        StatisticsUtils.isDateInFilter(it.expenseDate, filterMode, fyQuarter, selectedMonth)
    }

    /**
     * Net Income = Total Income (vaccination + consultation revenue, already computed by
     * FinanceCalculator.calculateFinanceStats as totalRevenue) - Total Expenses (this
     * table). Task section 7 explicitly defines Net Income this way - independent of
     * FinanceStatsData.netProfit, which additionally subtracts vaccine COGS and is left
     * unchanged.
     */
    fun netIncomeRupees(totalIncomeRupees: Double, totalExpensesPaise: Long): Double {
        return totalIncomeRupees - (totalExpensesPaise / 100.0)
    }
}
