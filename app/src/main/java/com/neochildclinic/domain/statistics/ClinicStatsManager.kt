package com.neochildclinic.domain.statistics

import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.common.DateClassifier
import com.neochildclinic.core.common.DateCategory
import com.neochildclinic.core.common.toLocalDate
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.domain.model.ClinicStats
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        val todayIST = StatisticsDateUtils.todayIST()
        val todayStr = StatisticsDateUtils.formatDateIST(todayIST.toLocalDate())
        val monthLabel = StatisticsDateUtils.formatDateIST(todayIST.toLocalDate().withDayOfMonth(1))

        return combine(
            financeRepository.getAllTransactions(),
            reminderRepository.getDueList(),
            inventoryRepository.getInventoryItems(),
            vaccinationRepository.allVaccinations
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val transactions = (args[0] as List<com.neochildclinic.data.local.entity.FinanceEntity>).map { it.toDomain() }
            @Suppress("UNCHECKED_CAST")
            val dueVaccinations = args[1] as List<com.neochildclinic.domain.model.Vaccination>
            @Suppress("UNCHECKED_CAST")
            val inventory = args[2] as List<InventoryItem>
            @Suppress("UNCHECKED_CAST")
            val allVaccinations = args[3] as List<com.neochildclinic.domain.model.Vaccination>
            val validVaccinations = StatisticsUtils.filterValidVaccinations(allVaccinations)
            val todayCount = validVaccinations.count { it.dateGiven == todayStr }
            val monthlyCount = validVaccinations.count {
                StatisticsDateUtils.monthKeyIST(it.dateGiven) != null && StatisticsDateUtils.formatDateIST(java.time.LocalDate.parse(it.dateGiven, java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))).startsWith(monthLabel)
            }

            val todayTransactions = transactions.filter { tx ->
                StatisticsDateUtils.parseToISTLocalDate(tx.timestamp)?.toString() == todayStr
            }
            val todayFinance = FinanceCalculator.calculateFinanceStats(todayTransactions, allVaccinations, transactions)
            val todayRevenue = todayFinance.totalRevenue
            val todayCash = todayFinance.cashTotal
            val todayOnline = todayFinance.onlineTotal
            val monthlyTransactions = transactions.filter { tx ->
                val txDate = StatisticsDateUtils.parseToISTLocalDate(tx.timestamp)
                txDate != null && StatisticsDateUtils.formatDateIST(txDate.withDayOfMonth(1)) == monthLabel
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

            val topVaccines = calculateTopVaccines(allVaccinations, monthLabel)

            ClinicStats(
                todayVaccinations = todayCount,
                todayRevenue = todayRevenue,
                todayCash = todayCash,
                todayOnline = todayOnline,
                monthlyVaccinations = monthlyCount,
                monthlyRevenue = monthlyRevenue,
                dueToday = dueToday,
                overdue = overdue,
                lowStockCount = inventory.count { it.isLowStock && !it.hasOutofStock },
                topVaccines = topVaccines
            )
        }
    }

    private fun calculateTopVaccines(
        vaccinations: List<com.neochildclinic.domain.model.Vaccination>,
        monthLabel: String
    ): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        vaccinations
            .filter { it.status == com.neochildclinic.domain.model.ReminderStatus.COMPLETED || it.status == com.neochildclinic.domain.model.ReminderStatus.EXTERNAL || it.source.equals("EXTERNAL", true) }
            .filter { vaccination ->
                val date = StatisticsDateUtils.parseToISTLocalDate(vaccination.dateGiven)
                date != null && StatisticsDateUtils.formatDateIST(date.withDayOfMonth(1)) == monthLabel
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


