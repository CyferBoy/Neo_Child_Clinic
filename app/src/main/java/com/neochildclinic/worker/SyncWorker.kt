package com.neochildclinic.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neochildclinic.features.settings.NotificationSettingsManager
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val settingsManager: NotificationSettingsManager,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    private val sharedPrefs = appContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        return try {
            syncRepository.processNextItems()
            sharedPrefs.edit().putInt("failure_count", 0).apply()
            Result.success()
        } catch (e: Exception) {
            val count = sharedPrefs.getInt("failure_count", 0) + 1
            sharedPrefs.edit().putInt("failure_count", count).apply()
            
            val settings = settingsManager.settingsFlow.first()
            if (count >= 5 && settings.syncAlertsEnabled) {
                notificationHelper.showSyncAlert(e.message ?: "Unknown error")
            }
            Result.retry()
        }
    }
}
