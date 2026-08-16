package com.neochildclinic.domain.manager

import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.core.utils.DateClassifier
import com.neochildclinic.core.utils.DateCategory
import com.neochildclinic.domain.model.ClinicStats
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.repository.*
import com.neochildclinic.features.statistics.FinanceCalculator
import com.neochildclinic.features.statistics.StatisticsUtils
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
    private val inventoryRepository: InventoryRepository,
    private val financeRepository: FinanceRepository
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
            vaccinationRepository.getMonthlyCount(monthPattern),
            financeRepository.getAllTransactions(),
            reminderRepository.getDueList(), // Use full list to re-calculate stats consistently
            inventoryRepository.getInventoryItems(),
            vaccinationRepository.allVaccinations
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val transactions = args[2] as List<com.neochildclinic.data.local.entity.FinanceEntity>
            @Suppress("UNCHECKED_CAST")
            val dueVaccinations = args[3] as List<com.neochildclinic.domain.model.Vaccination>
            @Suppress("UNCHECKED_CAST")
            val inventory = args[4] as List<InventoryItem>
            @Suppress("UNCHECKED_CAST")
            val allVaccinations = args[5] as List<com.neochildclinic.domain.model.Vaccination>
            val validVaccinations = StatisticsUtils.filterValidVaccinations(allVaccinations)
            val todayCount = validVaccinations.count { it.dateGiven == todayStr }
            val monthLabel = SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(today.time)
            val monthlyCount = validVaccinations.count {
                val date = PatientUtils.parseDate(it.dateGiven)
                date != null && SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(date) == monthLabel
            }

            val todayTransactions = transactions.filter { tx ->
                PatientUtils.parseDate(tx.timestamp)?.let { PatientUtils.formatDate(it) == todayStr } == true
            }
            val todayFinance = FinanceCalculator.calculateFinanceStats(todayTransactions, allVaccinations, transactions)
            val todayRevenue = todayFinance.totalRevenue
            val todayCash = todayFinance.cashTotal
            val todayOnline = todayFinance.onlineTotal
            val monthlyTransactions = transactions.filter { tx ->
                PatientUtils.parseDate(tx.timestamp)?.let { d -> SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(d) == monthLabel } == true
            }
            val monthlyFinance = FinanceCalculator.calculateFinanceStats(monthlyTransactions, allVaccinations, transactions)
            val monthlyRevenue = monthlyFinance.totalRevenue

            val todayCal = DateClassifier.getTodayStart()
            val dueToday = dueVaccinations.count { 
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Today
            }
            val overdue = dueVaccinations.count { 
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Overdue
            }

            val topVaccines = calculateTopVaccines(allVaccinations, monthPattern)

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

    private fun calculateTopVaccines(
        vaccinations: List<com.neochildclinic.domain.model.Vaccination>,
        monthPattern: String
    ): List<Pair<String, Int>> {
        val monthLabel = monthPattern.removePrefix("% ").trim()
        val counts = mutableMapOf<String, Int>()
        vaccinations
            .filter { it.status == com.neochildclinic.domain.model.ReminderStatus.COMPLETED || it.status == com.neochildclinic.domain.model.ReminderStatus.EXTERNAL || it.source.equals("EXTERNAL", true) }
            .filter { vaccination ->
                val date = PatientUtils.parseDate(vaccination.dateGiven)
                date != null && SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(date) == monthLabel
            }
            .forEach { vaccination ->
                vaccination.items.forEachIndexed { index, item ->
                    val rawName = item.vaccineName.ifBlank { vaccination.vaccineNames.getOrNull(index).orEmpty() }
                    val name = PatientUtils.cleanVaccineName(rawName)
                    if (name.isNotBlank()) counts[name] = (counts[name] ?: 0) + item.quantity.coerceAtLeast(0)
                }
            }
        return counts.toList().sortedByDescending { it.second }.take(5)
    }
}
