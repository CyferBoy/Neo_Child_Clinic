package com.neochildclinic.features.statistics

import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.model.Vaccination
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Single source of truth for financial statistics. */
data class FinanceSummaryItem(
    val label: String,
    val revenue: Double,
    val expenses: Double,
    val vaccineCost: Double,
    val netProfit: Double,
    val key: String,
    val cash: Double = 0.0,
    val online: Double = 0.0,
    val isProfitComplete: Boolean = true,
    val missingCogsSnapshotCount: Int = 0
)

data class FinanceStatsData(
    val totalRevenue: Double,
    val cashTotal: Double,
    val onlineTotal: Double,
    val totalExpenses: Double,
    val vaccineCost: Double,
    val grossProfit: Double,
    val netProfit: Double,
    val invalidTransactionDateCount: Int = 0,
    val unmatchedVaccinationIncomeCount: Int = 0,
    val missingCogsSnapshotCount: Int = 0,
    val unrecordedVaccinationPaymentCount: Int = 0,
    val isProfitComplete: Boolean = true
)

object FinanceCalculator {
    private const val INCOME = "INCOME"
    private const val EXPENSE = "EXPENSE"
    private const val VACCINATION = "VACCINATION"
    private const val COGS_MARKER = "[COGS_SNAPSHOT:"

    /**
     * The authoritative reporting date for a finance transaction.
     * transaction_date represents when the financial activity occurred; timestamp
     * remains the technical record creation/update timestamp.
     *
     * If transaction_date is missing but a visitId is present, the visit's date
     * can be used as a fallback if provided in the optional visitDates map.
     */
    fun resolveReportingDate(transaction: FinanceEntity, visitDates: Map<String, String>? = null): String {
        val transactionDate = transaction.transactionDate?.takeIf { it.isNotBlank() }
        if (transactionDate != null) return transactionDate

        val visitId = transaction.visitId
        if (!visitId.isNullOrBlank() && visitDates != null) {
            val visitDate = visitDates[visitId]
            if (!visitDate.isNullOrBlank()) return visitDate
        }

        return transaction.timestamp
    }

    fun calculateFinanceStats(
        transactions: List<FinanceEntity>,
        vaccinationsForCogs: List<Vaccination>,
        allTransactionsForReconciliation: List<FinanceEntity> = transactions,
        vaccinationsForReconciliation: List<Vaccination> = vaccinationsForCogs,
        visitDatesById: Map<String, String>? = null
    ): FinanceStatsData {
        val income = transactions.filter { it.type.equals(INCOME, true) }
        val expenses = transactions.filter { it.type.equals(EXPENSE, true) }
        
        // Cache valid vaccinations once
        val validVaccinationsForCogs = StatisticsUtils.filterValidVaccinations(vaccinationsForCogs)
        val vaccinationById = validVaccinationsForCogs.associateBy { it.id }

        val effectiveVaccinationIncome = deduplicateVaccinationIncome(income)
        val effectiveIncome = income.filterNot { it.category.equals(VACCINATION, true) } + effectiveVaccinationIncome
        val revenue = effectiveIncome.sumOf { it.amount.coerceAtLeast(0.0) }
        val totalExpenses = expenses.sumOf { it.amount.coerceAtLeast(0.0) }
        val cash = paymentAmount(effectiveIncome, true)
        val online = paymentAmount(effectiveIncome, false)

        var unmatched = 0
        var missingCogsSnapshot = 0
        val countedVisitIds = mutableSetOf<String>()
        var vaccineCost = 0.0
        effectiveVaccinationIncome.forEach { transaction ->
            val visitId = transaction.visitId
            if (visitId.isNullOrBlank()) {
                unmatched++
                return@forEach
            }
            if (!countedVisitIds.add(visitId)) return@forEach
            val snapshot = parseCogsSnapshot(transaction.remarks)
            if (snapshot != null) {
                vaccineCost += snapshot
            } else {
                if (vaccinationById[visitId] == null) {
                    unmatched++
                } else {
                    missingCogsSnapshot++
                }
            }
        }

        val isProfitComplete = missingCogsSnapshot == 0 && unmatched == 0
        val grossProfit = if (isProfitComplete) revenue - vaccineCost else 0.0
        val netProfit = if (isProfitComplete) grossProfit - totalExpenses else 0.0
        
        // Use pre-calculated reporting dates for invalid check
        val invalidTransactionDateCount = transactions.count { 
            val date = resolveReportingDate(it, visitDatesById)
            PatientUtils.parseDate(date) == null 
        }
        
        val allRecordedVaccinationVisitIds = deduplicateVaccinationIncome(
            allTransactionsForReconciliation.filter { it.type.equals(INCOME, true) }
        ).mapNotNull { it.visitId }.toSet()
        
        val validReconciliationVaccinations = StatisticsUtils.filterValidVaccinations(vaccinationsForReconciliation)
        val unrecordedVaccinationPaymentCount = validReconciliationVaccinations.count {
            it.totalPaid > 0.0 && it.id !in allRecordedVaccinationVisitIds
        }

        return FinanceStatsData(
            totalRevenue = revenue,
            cashTotal = cash,
            onlineTotal = online,
            totalExpenses = totalExpenses,
            vaccineCost = vaccineCost,
            grossProfit = grossProfit,
            netProfit = netProfit,
            invalidTransactionDateCount = invalidTransactionDateCount,
            unmatchedVaccinationIncomeCount = unmatched,
            missingCogsSnapshotCount = missingCogsSnapshot,
            unrecordedVaccinationPaymentCount = unrecordedVaccinationPaymentCount,
            isProfitComplete = isProfitComplete
        )
    }

    fun getMonthlyGroupedData(
        transactions: List<FinanceEntity>,
        vaccinations: List<Vaccination>,
        filterMode: String = "Overall",
        selectedQuarter: Int = 0,
        selectedMonth: Int = -1,
        visitDatesById: Map<String, String>? = null
    ): List<FinanceSummaryItem> {
        val vaccinationById = StatisticsUtils.filterValidVaccinations(vaccinations).associateBy { it.id }
        
        // Safety: Ignore transactions before the year 2000 to prevent infinite loops 
        // if dates are corrupted (e.g., year 0202).
        val safetyBoundary = Calendar.getInstance().apply {
            set(2000, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        // Cache reporting dates to avoid redundant parsing in the loop
        val parsed = transactions.mapNotNull { transaction ->
            val reportingDate = resolveReportingDate(transaction, visitDatesById)
            val date = PatientUtils.parseDate(reportingDate) ?: return@mapNotNull null
            if (date.before(safetyBoundary)) return@mapNotNull null
            transaction to date
        }
        if (parsed.isEmpty()) return emptyList()

        val (first, last) = if (filterMode.startsWith("FY ")) {
            val short = filterMode.substringAfter("FY ").substringBefore("-").toIntOrNull()
            val fyStart = if ((short ?: 0) > 80) 1900 + (short ?: 0) else 2000 + (short ?: 0)
            val start = Calendar.getInstance().apply {
                set(fyStart, Calendar.APRIL, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val offset = when {
                selectedMonth in 0..11 -> ((selectedMonth - Calendar.APRIL) + 12) % 12
                selectedQuarter in 1..4 -> (selectedQuarter - 1) * 3
                else -> 0
            }
            if (selectedMonth in 0..11) {
                start.add(Calendar.MONTH, offset)
                start to (start.clone() as Calendar)
            } else if (selectedQuarter in 1..4) {
                start.add(Calendar.MONTH, offset)
                val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 2) }
                start to end
            } else {
                val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 11) }
                start to end
            }
        } else {
            parsed.minOf { monthStart(it.second) } to parsed.maxOf { monthStart(it.second) }
        }

        val result = mutableListOf<FinanceSummaryItem>()
        
        // Optimization: Pre-group transactions by month key once
        val transactionsByMonth = parsed.groupBy { (_, date) ->
            val cal = Calendar.getInstance().apply { time = date }
            String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        }

        var cursor = first.clone() as Calendar
        var iterationCount = 0
        val maxIterations = 600 // Safety: limit to 50 years to prevent app hanging

        while (!cursor.after(last) && iterationCount < maxIterations) {
            iterationCount++
            val year = cursor.get(Calendar.YEAR)
            val month = cursor.get(Calendar.MONTH)
            val key = String.format(Locale.US, "%04d-%02d", year, month)
            
            // Fast lookup from the pre-grouped map
            val monthTransactions = transactionsByMonth[key]?.map { it.first } ?: emptyList()

            val monthIncome = monthTransactions.filter { it.type.equals(INCOME, true) }
            val effectiveMonthVaccinationIncome = deduplicateVaccinationIncome(monthIncome)
            val effectiveMonthIncome = monthIncome.filterNot { it.category.equals(VACCINATION, true) } + effectiveMonthVaccinationIncome
            val revenue = effectiveMonthIncome.sumOf { it.amount.coerceAtLeast(0.0) }
            val cash = paymentAmount(effectiveMonthIncome, true)
            val online = paymentAmount(effectiveMonthIncome, false)
            val expenses = monthTransactions.filter { it.type.equals(EXPENSE, true) }.sumOf { it.amount.coerceAtLeast(0.0) }
            val countedVisitIds = mutableSetOf<String>()
            var vaccineCost = 0.0
            var missingCogsSnapshotCount = 0
            var unmatchedVaccinationCount = 0
            effectiveMonthVaccinationIncome.forEach { tx ->
                val visitId = tx.visitId
                if (visitId.isNullOrBlank()) {
                    unmatchedVaccinationCount++
                    return@forEach
                }
                if (!countedVisitIds.add(visitId)) return@forEach
                val snapshot = parseCogsSnapshot(tx.remarks)
                if (snapshot != null) {
                    vaccineCost += snapshot
                } else if (vaccinationById[visitId] == null) {
                    unmatchedVaccinationCount++
                } else {
                    missingCogsSnapshotCount++
                }
            }
            val isProfitComplete = missingCogsSnapshotCount == 0 && unmatchedVaccinationCount == 0
            val netProfit = if (isProfitComplete) revenue - expenses - vaccineCost else 0.0

            result += FinanceSummaryItem(
                label = "${MONTH_NAMES[month]} $year",
                revenue = revenue,
                expenses = expenses,
                vaccineCost = vaccineCost,
                netProfit = netProfit,
                key = key,
                cash = cash,
                online = online,
                isProfitComplete = isProfitComplete,
                missingCogsSnapshotCount = missingCogsSnapshotCount + unmatchedVaccinationCount
            )
            cursor.add(Calendar.MONTH, 1)
        }
        return result
    }

    fun calculateImprovement(current: Double, previous: Double): Double? {
        if (previous == 0.0) return null
        return ((current - previous) / kotlin.math.abs(previous)) * 100.0
    }

    fun buildVaccinationRemarks(vaccinationNames: String, vaccineCost: Double): String =
        "Vaccination: $vaccinationNames $COGS_MARKER${"%.2f".format(Locale.US, vaccineCost)}]"

    private fun parseCogsSnapshot(remarks: String?): Double? {
        val value = remarks?.substringAfter(COGS_MARKER, missingDelimiterValue = "")?.substringBefore("]")?.toDoubleOrNull()
        return value?.takeIf { it >= 0.0 }
    }

    private fun deduplicateVaccinationIncome(income: List<FinanceEntity>): List<FinanceEntity> {
        return income
            .filter { it.category.equals(VACCINATION, true) }
            .groupBy { it.visitId?.takeIf(String::isNotBlank) ?: it.id }
            .values
            .map { group -> group.maxByOrNull { it.timestamp }!! }
    }

    private fun paymentAmount(transactions: List<FinanceEntity>, cash: Boolean): Double =
        transactions.sumOf { tx ->
            val explicit = if (cash) tx.cashAmount else tx.onlineAmount
            if (explicit > 0.0) explicit else when {
                cash && tx.paymentMethod.equals("CASH", true) -> tx.amount
                !cash && tx.paymentMethod.equals("ONLINE", true) -> tx.amount
                else -> 0.0
            }
        }

    private fun monthStart(date: Date): Calendar = Calendar.getInstance().apply {
        time = date
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private val MONTH_NAMES = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
}
