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
    val invalidTimestampCount: Int = 0,
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
     * The date Financial Statistics must report a transaction under - "when did this
     * revenue actually happen", not "when was this record entered". A transaction linked
     * to a visit (vaccination OR consultation - both live in patient_visits, keyed by the
     * same visitId) is reported under that visit's actual dateGiven. Everything else
     * (expenses, or a transaction with no matching visit) falls back to the technical
     * finance_transactions.timestamp, which is left untouched in the database either way -
     * this only changes what date Statistics groups/filters by.
     */
    fun resolveReportingDate(transaction: FinanceEntity, visitDatesById: Map<String, String>): String {
        val visitId = transaction.visitId
        if (!visitId.isNullOrBlank()) {
            val visitDate = visitDatesById[visitId]
            if (!visitDate.isNullOrBlank()) return visitDate
        }
        return transaction.timestamp
    }

    fun calculateFinanceStats(
        transactions: List<FinanceEntity>,
        vaccinationsForCogs: List<Vaccination>,
        allTransactionsForReconciliation: List<FinanceEntity> = transactions,
        vaccinationsForReconciliation: List<Vaccination> = vaccinationsForCogs
    ): FinanceStatsData {
        val income = transactions.filter { it.type.equals(INCOME, true) }
        val expenses = transactions.filter { it.type.equals(EXPENSE, true) }
        val vaccinationById = StatisticsUtils.filterValidVaccinations(vaccinationsForCogs).associateBy { it.id }

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
                    // Do not recalculate historical COGS from mutable clinical data.
                    // Legacy records are snapshotted by the application startup migration.
                    missingCogsSnapshot++
                }
            }
        }

        val isProfitComplete = missingCogsSnapshot == 0 && unmatched == 0
        // Never present an incomplete profit calculation as a valid accounting result.
        // Revenue/expense/cash/online remain usable, while profit is marked unavailable.
        val grossProfit = if (isProfitComplete) revenue - vaccineCost else 0.0
        val netProfit = if (isProfitComplete) grossProfit - totalExpenses else 0.0
        val invalidTimestampCount = transactions.count { PatientUtils.parseDate(it.timestamp) == null }
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
            invalidTimestampCount = invalidTimestampCount,
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
        selectedMonth: Int = -1
    ): List<FinanceSummaryItem> {
        val vaccinationById = StatisticsUtils.filterValidVaccinations(vaccinations).associateBy { it.id }
        // Raw (status-unfiltered) map for date resolution: a linked visit's actual date is
        // valid for reporting purposes regardless of that visit's clinical/admin status -
        // e.g. a CONSULTATION visit's status has no bearing on whether its date is trustworthy.
        val visitDatesById = vaccinations.associate { it.id to it.dateGiven }
        val parsed = transactions.mapNotNull { transaction ->
            val date = PatientUtils.parseDate(resolveReportingDate(transaction, visitDatesById)) ?: return@mapNotNull null
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
        var cursor = first.clone() as Calendar
        while (!cursor.after(last)) {
            val year = cursor.get(Calendar.YEAR)
            val month = cursor.get(Calendar.MONTH)
            val key = String.format(Locale.US, "%04d-%02d", year, month)
            val monthTransactions = parsed.filter { (_, date) ->
                val cal = Calendar.getInstance().apply { time = date }
                cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
            }.map { it.first }

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
