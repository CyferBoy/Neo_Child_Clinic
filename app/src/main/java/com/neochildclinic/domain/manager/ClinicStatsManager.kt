package com.neochildclinic.domain.manager

import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.core.utils.DateClassifier
import com.neochildclinic.core.utils.DateCategory
import com.neochildclinic.domain.model.ClinicStats
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates statistics from multiple repositories to provide a unified clinic performance view.
 * Adheres to the Manager layer in Clean Architecture to isolate complex aggregation logic.
 */
@Singleton
class ClinicStatsManager @Inject constructor(
    private val vaccinationRepository: VaccinationRepository,
    private val reminderRepository: ReminderRepository,
    private val inventoryRepository: InventoryRepository
) {
    /**
     * Returns a combined flow of all high-level clinic metrics.
     * Uses optimized database queries via repositories.
     */
    fun getClinicStats(): Flow<ClinicStats> {
        val today = Calendar.getInstance()
        val todayStr = PatientUtils.formatDate(today.time)
        
        // Month pattern for SQLite LIKE: "% May 2026"
        val monthPattern = "% ${SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(today.time)}"

        return combine(
            vaccinationRepository.getTodayCount(todayStr),
            vaccinationRepository.getTodayRevenue(todayStr),
            vaccinationRepository.getTodayCash(todayStr),
            vaccinationRepository.getTodayOnline(todayStr),
            vaccinationRepository.getMonthlyCount(monthPattern),
            vaccinationRepository.getMonthlyRevenue(monthPattern),
            reminderRepository.getDueList(), // Use full list to re-calculate stats consistently
            inventoryRepository.getInventoryItems(),
            vaccinationRepository.getVaccineNamesForMonth(monthPattern)
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val todayCount = args[0] as Int
            @Suppress("UNCHECKED_CAST")
            val todayRevenue = args[1] as? Double ?: 0.0
            @Suppress("UNCHECKED_CAST")
            val todayCash = args[2] as? Double ?: 0.0
            @Suppress("UNCHECKED_CAST")
            val todayOnline = args[3] as? Double ?: 0.0
            @Suppress("UNCHECKED_CAST")
            val monthlyCount = args[4] as Int
            @Suppress("UNCHECKED_CAST")
            val monthlyRevenue = args[5] as? Double ?: 0.0
            @Suppress("UNCHECKED_CAST")
            val dueVaccinations = args[6] as List<com.neochildclinic.domain.model.Vaccination>
            @Suppress("UNCHECKED_CAST")
            val inventory = args[7] as List<InventoryItem>
            @Suppress("UNCHECKED_CAST")
            val vaccineNamesList = args[8] as List<String>

            val todayCal = DateClassifier.getTodayStart()
            val dueToday = dueVaccinations.count { 
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Today
            }
            val overdue = dueVaccinations.count { 
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Overdue
            }

            val topVaccines = calculateTopVaccines(vaccineNamesList)

            ClinicStats(
                todayVaccinations = todayCount,
                todayRevenue = todayRevenue,
                todayCash = todayCash,
                todayOnline = todayOnline,
                monthlyVaccinations = monthlyCount,
                monthlyRevenue = monthlyRevenue,
                dueToday = dueToday,
                overdue = overdue,
                lowStockCount = inventory.count { it.isLowStock },
                topVaccines = topVaccines
            )
        }
    }

    private fun calculateTopVaccines(vaccineNamesList: List<String>): List<Pair<String, Int>> {
        return vaccineNamesList.flatMap { names -> 
            names.split(",").map { it.trim() } 
        }
        .filter { it.isNotEmpty() }
        .groupBy { it }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
        .take(5)
    }
}
