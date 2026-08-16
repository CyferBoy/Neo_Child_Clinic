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
}
