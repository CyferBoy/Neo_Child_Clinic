package com.neochildclinic.features.statistics

import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import java.util.Calendar

object StatisticsUtils {
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    val fyQuarters = listOf(
        "Q1 (Apr-Jun)" to listOf(3, 4, 5),
        "Q2 (Jul-Sep)" to listOf(6, 7, 8),
        "Q3 (Oct-Dec)" to listOf(9, 10, 11),
        "Q4 (Jan-Mar)" to listOf(0, 1, 2)
    )

    // The 11 upcoming-age-milestone cards (Statistics -> Patients), in display order.
    // Keys are stable route-safe identifiers; labels must match PatientUtils.getNextAgeMilestone()'s
    // AgeMilestone.label values exactly, since both the summary cards and the milestone detail
    // screen key off this single shared list.
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

    /** A visit is statistically valid only after it has been administered/completed.
     * Legacy EXTERNAL records are also accepted through the source field.
     */
    fun isCountedVaccination(vaccination: Vaccination): Boolean =
        vaccination.status == ReminderStatus.COMPLETED || vaccination.status == ReminderStatus.EXTERNAL || vaccination.source.equals("EXTERNAL", true)

    fun filterValidVaccinations(vaccinations: List<Vaccination>): List<Vaccination> =
        vaccinations.filter(::isCountedVaccination)

    fun getAvailableFinancialYears(dates: List<String>): List<String> {
        val allDates = dates.mapNotNull { PatientUtils.parseDate(it) }
        val today = Calendar.getInstance()
        return if (allDates.isEmpty()) {
            val curYear = today.get(Calendar.YEAR)
            val curMonth = today.get(Calendar.MONTH)
            val fyStart = if (curMonth >= Calendar.APRIL) curYear else curYear - 1
            listOf("${fyStart % 100}-${(fyStart + 1) % 100}")
        } else {
            val years = allDates.map {
                val cal = Calendar.getInstance().apply { time = it }
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH)
                if (m >= Calendar.APRIL) y else y - 1
            }.distinct().sorted()
            years.map { "${it % 100}-${(it + 1) % 100}" }
        }
    }

    fun vaccineName(vaccination: Vaccination, itemIndex: Int): String {
        val itemName = vaccination.items.getOrNull(itemIndex)?.vaccineName.orEmpty()
        return itemName.ifBlank { vaccination.vaccineNames.getOrNull(itemIndex).orEmpty() }
    }

    fun monthCountForFilter(dates: List<String>, filterMode: String, fyQuarter: Int, selectedMonth: Int): Int {
        if (selectedMonth != -1) return 1
        if (filterMode.startsWith("FY ")) return if (fyQuarter == 0) 12 else 3
        val parsed = dates.mapNotNull { PatientUtils.parseDate(it) }
        if (parsed.isEmpty()) return 0
        val months = parsed.map { date ->
            val c = Calendar.getInstance().apply { time = date }
            c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
        }
        return (months.maxOrNull()!! - months.minOrNull()!! + 1).coerceAtLeast(1)
    }

    fun isDateInFilter(dateStr: String, filterMode: String, fyQuarter: Int = 0, selectedMonth: Int = -1): Boolean {
        val date = PatientUtils.parseDate(dateStr) ?: return false
        val cal = Calendar.getInstance().apply { time = date }
        val m = cal.get(Calendar.MONTH)
        val y = cal.get(Calendar.YEAR)
        if (filterMode == "Overall") return true

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
                // Previous Month.
                // The FY here runs April→March, so April (Calendar.APRIL) is the first
                // month of the FY - stepping back from it crosses into the *previous* FY
                // label (April's previous month is March of the previous FY). Every other
                // month, including January, stays within the same FY label when stepping
                // back a month (e.g. January's previous month, December, is still inside
                // the same Apr-Mar financial year as the following Jan-Mar).
                val prevMonth = if (selectedMonth == 0) 11 else selectedMonth - 1
                val prevYearShort = if (selectedMonth == Calendar.APRIL) startYearShort - 1 else startYearShort
                val prevFY = "${prevYearShort % 100}-${(prevYearShort + 1) % 100}"
                Triple("FY $prevFY", if (prevMonth in 0..2) 4 else if (prevMonth in 3..5) 1 else if (prevMonth in 6..8) 2 else 3, prevMonth)
            }
            fyQuarter != 0 -> {
                // Previous Quarter
                val prevQuarter = if (fyQuarter == 1) 4 else fyQuarter - 1
                val prevYearShort = if (fyQuarter == 1) startYearShort - 1 else startYearShort
                val prevFY = "${prevYearShort % 100}-${(prevYearShort + 1) % 100}"
                Triple("FY $prevFY", prevQuarter, -1)
            }
            else -> {
                // Previous FY
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
}
