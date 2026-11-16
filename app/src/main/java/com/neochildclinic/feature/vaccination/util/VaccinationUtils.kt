package com.neochildclinic.feature.vaccination.util

import com.neochildclinic.core.common.DateClassifier
import com.neochildclinic.core.common.DateUtils
import com.neochildclinic.core.common.toLocalDate
import com.neochildclinic.domain.model.Vaccination
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

object VaccinationUtils {
    /**
     * Filters pending vaccinations based on a string filter (e.g., "Overdue", "Today").
     * All date comparisons use the clinic's timezone (IST).
     */
    fun filterVaccinationsByPeriod(
        pendingVaccinations: List<Vaccination>,
        filter: String,
    ): List<Vaccination> {
        val today = Instant.now().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
        val tomorrow = today.plusDays(1)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
        val weekEnd = weekStart.plusDays(6)
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())

        return pendingVaccinations.filter { v ->
            val date = DateUtils.parseDate(v.nextDueDate)?.toLocalDate()
            if (date == null) {
                filter == "All"
            } else {
                when (filter) {
                    "Overdue" -> date.isBefore(today)
                    "Today" -> date == today
                    "Tomorrow" -> date == tomorrow
                    "This Week" -> !date.isBefore(weekStart) && !date.isAfter(weekEnd)
                    "Month" -> !date.isBefore(monthStart) && !date.isAfter(monthEnd)
                    "Upcoming" -> date.isAfter(weekEnd)
                    "All" -> true
                    else -> true
                }
            }
        }.sortedBy { DateClassifier.getSortWeight(it.nextDueDate) }
    }
}
