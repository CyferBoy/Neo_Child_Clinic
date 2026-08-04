package com.neochildclinic.features.statistics

import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Vaccine
import java.util.*

data class FinanceSummaryItem(val label: String, val revenue: Double, val profit: Double, val key: String)

data class FinanceStatsData(
    val totalRevenue: Double,
    val cashTotal: Double,
    val onlineTotal: Double,
    val totalProfit: Double
)

object FinanceCalculator {
    fun calculateFinanceStats(vaccinations: List<Vaccination>, inventory: List<Vaccine>): FinanceStatsData {
        val revenue = vaccinations.sumOf { it.totalPaid }
        val cash = vaccinations.sumOf { it.cashAmount }
        val online = vaccinations.sumOf { it.onlineAmount }
        val netRate = vaccinations.sumOf { v ->
            v.vaccineNames.sumOf { name -> inventory.find { it.brandName == name }?.netRate ?: 0.0 }
        }
        return FinanceStatsData(revenue, cash, online, revenue - netRate)
    }

    fun getMonthlyGroupedData(vaccinations: List<Vaccination>, inventory: List<Vaccine>): List<FinanceSummaryItem> {
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val grouped = vaccinations.groupBy {
            val cal = Calendar.getInstance().apply { time = PatientUtils.parseDate(it.dateGiven) ?: Date(0) }
            String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        }.toList().sortedBy { it.first }

        return grouped.map { (key, list) ->
            val revenue = list.sumOf { it.totalPaid }
            val netRate = list.sumOf { v -> v.vaccineNames.sumOf { name -> inventory.find { it.brandName == name }?.netRate ?: 0.0 } }
            val monthIdx = key.substringAfter("-").toInt()
            val year = key.substringBefore("-")
            FinanceSummaryItem("${monthNames[monthIdx]} $year", revenue, revenue - netRate, key)
        }
    }

    fun calculateImprovement(currentProfit: Double, previousProfit: Double): Double {
        return if (previousProfit > 0) ((currentProfit - previousProfit) / previousProfit) * 100 else 0.0
    }
}
