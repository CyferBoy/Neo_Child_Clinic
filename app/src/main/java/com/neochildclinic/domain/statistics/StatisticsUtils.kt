package com.neochildclinic.domain.statistics

import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

object StatisticsUtils {
    fun formatRupees(amount: Double): String = String.format(Locale.US, "\u20b9%,.0f", amount)

    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

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
        if (filterMode == "Overall") "Overall" else "20" + filterMode.substringAfter("FY ")

    fun isCountedVaccination(vaccination: Vaccination): Boolean =
        vaccination.status == ReminderStatus.COMPLETED || vaccination.status == ReminderStatus.EXTERNAL || vaccination.source.equals("EXTERNAL", true)

    fun filterValidVaccinations(vaccinations: List<Vaccination>): List<Vaccination> =
        vaccinations.filter(::isCountedVaccination)

    fun getAvailableFinancialYears(dates: List<String>): List<String> {
        if (dates.isEmpty()) {
            val (curYear, curMonth) = StatisticsDateUtils.currentISTYearMonth()
            val fyStart = if (curMonth >= Calendar.APRIL) curYear else curYear - 1
            return listOf("${fyStart % 100}-${(fyStart + 1) % 100}")
        }

        val uniqueDates = dates.distinct()
        val years = uniqueDates.mapNotNull { StatisticsDateUtils.parseToISTLocalDate(it) }
            .map { if (it.monthValue >= 4) it.year else it.year - 1 }
            .distinct().sorted()
        return years.map { "${it % 100}-${(it + 1) % 100}" }
    }

    fun vaccineName(vaccination: Vaccination, itemIndex: Int): String {
        val itemName = vaccination.items.getOrNull(itemIndex)?.vaccineName.orEmpty()
        return itemName.ifBlank { vaccination.vaccineNames.getOrNull(itemIndex).orEmpty() }
    }

    fun monthCountForFilter(dates: List<String>, filterMode: String, fyQuarter: Int, selectedMonth: Int): Int {
        if (selectedMonth != -1) return 1
        if (filterMode.startsWith("FY ")) return if (fyQuarter == 0) 12 else 3
        val parsed = dates.mapNotNull { StatisticsDateUtils.parseToISTLocalDate(it) }
        if (parsed.isEmpty()) return 0
        val months = parsed.map { it.year * 12 + (it.monthValue - 1) }
        return (months.maxOrNull()!! - months.minOrNull()!! + 1).coerceAtLeast(1)
    }

    fun isDateInFilter(dateStr: String, filterMode: String, fyQuarter: Int = 0, selectedMonth: Int = -1): Boolean {
        if (filterMode == "Overall") return true

        val localDate = StatisticsDateUtils.parseToISTLocalDate(dateStr) ?: return false
        val m = localDate.monthValue - 1
        val y = localDate.year

        val startYearShort = filterMode.substringAfter("FY ").substringBefore("-").toIntOrNull() ?: return false
        val fyStartYear = if (startYearShort > 80) 1900 + startYearShort else 2000 + startYearShort
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
        val startYearShort = filterMode.substringAfter("FY ").substringBefore("-").toIntOrNull() ?: return false
        val fyStartYear = if (startYearShort > 80) 1900 + startYearShort else 2000 + startYearShort
        val recordFY = if (m >= Calendar.APRIL) y else y - 1
        if (recordFY != fyStartYear) return false
        if (fyQuarter == 0) return true
        val quarterMonths = fyQuarters[fyQuarter - 1].second
        if (m !in quarterMonths) return false
        return selectedMonth == -1 || m == selectedMonth
    }

    fun getPreviousPeriodFilter(filterMode: String, fyQuarter: Int, selectedMonth: Int): Triple<String, Int, Int> {
        if (filterMode == "Overall") return Triple("Overall", 0, -1)

        val startYearShort = filterMode.substringAfter("FY ").substringBefore("-").toIntOrNull() ?: return Triple("Overall", 0, -1)

        return when {
            selectedMonth != -1 -> {
                val prevMonth = if (selectedMonth == 0) 11 else selectedMonth - 1
                val prevYearShort = if (selectedMonth == Calendar.APRIL) startYearShort - 1 else startYearShort
                val prevFY = "${prevYearShort % 100}-${(prevYearShort + 1) % 100}"
                Triple("FY $prevFY", if (prevMonth in 0..2) 4 else if (prevMonth in 3..5) 1 else if (prevMonth in 6..8) 2 else 3, prevMonth)
            }
            fyQuarter != 0 -> {
                val prevQuarter = if (fyQuarter == 1) 4 else fyQuarter - 1
                val prevYearShort = if (fyQuarter == 1) startYearShort - 1 else startYearShort
                val prevFY = "${prevYearShort % 100}-${(prevYearShort + 1) % 100}"
                Triple("FY $prevFY", prevQuarter, -1)
            }
            else -> {
                val prevYearShort = startYearShort - 1
                val prevFY = "${prevYearShort % 100}-${(prevYearShort + 1) % 100}"
                Triple("FY $prevFY", 0, -1)
            }
        }
    }

    fun calculateGrowth(current: Double, previous: Double): Double? {
        if (previous == 0.0) return null
        return ((current - previous) / previous) * 100.0
    }

    /**
     * Visit-type counts for Overview cards. Counts every non-deleted visit (the caller's list is
     * already is_deleted=0 filtered), by visit_type — deliberately NOT gated by [filterValidVaccinations],
     * because visits are recorded with ACTIVE status and must still count ([VisitTypeStats.consultedPatients]
     * is unique patients, the other two are per-visit counts).
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
}

data class VisitTypeStats(
    val consultedPatients: Int,
    val totalConsultation: Int,
    val totalVaccination: Int
)

