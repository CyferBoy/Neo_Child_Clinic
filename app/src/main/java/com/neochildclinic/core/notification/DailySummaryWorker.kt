package com.neochildclinic.core.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neochildclinic.core.preferences.NotificationSettingsManager
import com.neochildclinic.feature.inventory.data.InventoryRepositoryImpl
import com.neochildclinic.feature.statistics.domain.ClinicStatsManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val statsManager: ClinicStatsManager,
    private val inventoryRepository: InventoryRepositoryImpl,
    private val settingsManager: NotificationSettingsManager,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsManager.settingsFlow.first()
        val todayStr = LocalDate.now().toString()

        // 1. Daily Summary Logic
        if (settings.dailySummaryEnabled && settings.lastSummarySentDate != todayStr) {
            val stats = statsManager.getClinicStats().first()
            
            if (stats.dueToday > 0 || stats.overdue > 0 || stats.lowStockCount > 0) {
                notificationHelper.showDailySummary(stats.dueToday, stats.overdue, stats.lowStockCount)
                settingsManager.markSummarySent(todayStr)
            }
        }

        // 2. Individual Low Stock Logic
        if (settings.lowStockEnabled) {
            val inventory = inventoryRepository.getInventoryItems().first()
            for (item in inventory) {
                val isBelowThreshold = item.isLowStock
                val alreadyNotified = settings.notifiedLowStockVaccines.contains(item.id)

                if (isBelowThreshold && !alreadyNotified) {
                    notificationHelper.showLowStockAlert(item.brandName, item.stock)
                    settingsManager.markLowStockNotified(item.id)
                } else if (!isBelowThreshold && alreadyNotified) {
                    settingsManager.clearLowStockNotification(item.id)
                }
            }
        }

        return Result.success()
    }
}

