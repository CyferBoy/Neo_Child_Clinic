package com.neochildclinic.feature.statistics.domain

import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.finance.domain.FinanceCalculator
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

object StatisticsUtils {
    val monthNames get() = FinanceCalculator.MONTH_NAMES

    val fyQuarters = listOf(
        "Q1 (Apr-Jun)" to listOf(3, 4, 5),
        "Q2 (Jul-Sep)" to listOf(6, 7, 8),
        "Q3 (Oct-Dec)" to listOf(9, 10, 11),
        "Q4 (Jan-Mar)" to listOf(0, 1, 2)
    )

    val ageMilestones: List<Pair<String, String>> = listOf(
        "6w" to "6 Weeks",
        "10w" to "10 Weeks",
        "14w" to "14 Weeks",
        "6m" to "6 Months",
        "7m" to "7 Months",
        "9m" to "9 Months",
        "12m" to "12 Months",
        "13m" to "13 Months",
        "15m" to "15 Months",
        "16_17m" to "16–17 Months",
        "18m" to "18 Months",
        "older" to "Older"
    )

    fun milestoneLabelForKey(key: String): String? = ageMilestones.firstOrNull { it.first == key }?.second

    fun displayFilterMode(filterMode: String): String =
        if (filterMode == "Overall") "Overall"
        else FinanceCalculator.parseFinancialYearStart(filterMode)?.let { "$it-${(it + 1) % 100}" } ?: filterMode

    fun isCountedVaccination(vaccination: Vaccination): Boolean = FinanceCalculator.isCountedVaccination(vaccination)

    fun filterValidVaccinations(vaccinations: List<Vaccination>): List<Vaccination> =
        FinanceCalculator.filterValidVaccinations(vaccinations)

    fun vaccineName(vaccination: Vaccination, itemIndex: Int): String {
        val itemName = vaccination.items.getOrNull(itemIndex)?.vaccineName.orEmpty()
        return itemName.ifBlank { vaccination.vaccineNames.getOrNull(itemIndex).orEmpty() }
    }

    fun monthCountForFilter(dates: List<String>, filterMode: String, fyQuarter: Int, selectedMonth: Int): Int {
        if (selectedMonth != -1) return 1
        if (filterMode != "Overall") return if (fyQuarter == 0) 12 else 3
        val parsed = dates.mapNotNull { StatisticsDateUtils.parseToISTLocalDate(it) }
        val min = parsed.minOrNull() ?: return 0
        val max = parsed.maxOrNull() ?: return 0
        return (ChronoUnit.MONTHS.between(YearMonth.from(min), YearMonth.from(max)) + 1).toInt().coerceAtLeast(1)
    }

    fun isDateInFilter(dateStr: String, filterMode: String, fyQuarter: Int = 0, selectedMonth: Int = -1): Boolean {
        if (filterMode == "Overall") return true

        val localDate = StatisticsDateUtils.parseToISTLocalDate(dateStr) ?: return false
        val m = localDate.monthValue - 1
        val y = localDate.year

        val fyStartYear = FinanceCalculator.parseFinancialYearStart(filterMode) ?: return false
        val recordFY = if (m >= Calendar.APRIL) y else y - 1
        if (recordFY != fyStartYear) return false
        if (fyQuarter == 0) return true
        val quarterMonths = fyQuarters[fyQuarter - 1].second
        if (m !in quarterMonths) return false
        return selectedMonth == -1 || m == selectedMonth
    }

    /**
     * Check if an effective registration date (LocalDate) falls within the filter.
     * Used for patient-counting statistics where effective registration date applies.
     */
    fun isEffectiveDateInFilter(date: LocalDate?, filterMode: String, fyQuarter: Int = 0, selectedMonth: Int = -1): Boolean {
        if (filterMode == "Overall") return true
        if (date == null) return false
        val m = date.monthValue - 1
        val y = date.year
        val fyStartYear = FinanceCalculator.parseFinancialYearStart(filterMode) ?: return false
        val recordFY = if (m >= Calendar.APRIL) y else y - 1
        if (recordFY != fyStartYear) return false
        if (fyQuarter == 0) return true
        val quarterMonths = fyQuarters[fyQuarter - 1].second
        if (m !in quarterMonths) return false
        return selectedMonth == -1 || m == selectedMonth
    }

    fun getPreviousPeriodFilter(filterMode: String, fyQuarter: Int, selectedMonth: Int): Triple<String, Int, Int> {
        if (filterMode == "Overall") return Triple("Overall", 0, -1)

        val fyStartYear = FinanceCalculator.parseFinancialYearStart(filterMode) ?: return Triple("Overall", 0, -1)

        return when {
            selectedMonth != -1 -> {
                val prevMonth = if (selectedMonth == 0) 11 else selectedMonth - 1
                val prevFYStart = if (selectedMonth == Calendar.APRIL) fyStartYear - 1 else fyStartYear
                Triple(FinanceCalculator.formatFinancialYear(prevFYStart), if (prevMonth in 0..2) 4 else if (prevMonth in 3..5) 1 else if (prevMonth in 6..8) 2 else 3, prevMonth)
            }
            fyQuarter != 0 -> {
                val prevQuarter = if (fyQuarter == 1) 4 else fyQuarter - 1
                val prevFYStart = if (fyQuarter == 1) fyStartYear - 1 else fyStartYear
                Triple(FinanceCalculator.formatFinancialYear(prevFYStart), prevQuarter, -1)
            }
            else -> Triple(FinanceCalculator.formatFinancialYear(fyStartYear - 1), 0, -1)
        }
    }

    /** The cards' conventional percentage change; shared implementation lives in FinanceCalculator. */
    fun calculateGrowth(current: Double, previous: Double): Double? = FinanceCalculator.calculateGrowth(current, previous)

    /**
     * Visit-type counts for Overview cards. Counts every non-deleted visit (the caller's list is
     * already is_deleted=0 filtered), by visit_type — deliberately NOT gated by [filterValidVaccinations],
     * because consultation visits are recorded with ACTIVE status and must still count
     * ([VisitTypeStats.consultedPatients] is unique patients, the other two are per-visit counts).
     */
    fun visitTypeStats(visits: List<Vaccination>): VisitTypeStats {
        val consultedIds = mutableSetOf<String>()
        var totalConsultation = 0
        var totalVaccination = 0
        visits.forEach { v ->
            when {
                v.visitType.equals("CONSULTATION", ignoreCase = true) -> { totalConsultation++; consultedIds += v.patientId }
                v.visitType.equals("VACCINATION", ignoreCase = true) -> totalVaccination++
            }
        }
        return VisitTypeStats(consultedIds.size, totalConsultation, totalVaccination)
    }

    /**
     * The visit-type card rule for a date window: [visitTypeStats] over the date-filtered raw
     * visit list. Never wrap the input in [filterValidVaccinations] first — that drops the
     * ACTIVE-status consultation visits these cards exist to count (NCC-043).
     */
    fun visitTypeStatsForFilter(
        visits: List<Vaccination>,
        filterMode: String,
        fyQuarter: Int = 0,
        selectedMonth: Int = -1
    ): VisitTypeStats = visitTypeStats(visits.filter { isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) })
}

data class VisitTypeStats(
    val consultedPatients: Int,
    val totalConsultation: Int,
    val totalVaccination: Int
)

