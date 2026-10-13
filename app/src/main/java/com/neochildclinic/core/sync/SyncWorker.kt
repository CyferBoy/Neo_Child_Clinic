package com.neochildclinic.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neochildclinic.core.preferences.NotificationSettingsManager
import com.neochildclinic.core.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepositoryImpl,
    private val settingsManager: NotificationSettingsManager,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    private val sharedPrefs = appContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        return try {
            // Check for network before starting? WorkManager usually handles this via constraints
            // Process entire queue in a loop
            if (syncRepository.processNextItems()) {
                // At least one queue item ended FAILED (permanent) instead of uploading.
                // Previously this still returned success(), so failure_count never grew
                // and the sync-alert notification below could never fire. failure() stops
                // this request retrying (FAILED rows are retried manually from the Sync
                // screen); the periodic worker keeps running on its own schedule.
                countAndMaybeAlert("Some changes could not be synced")
                Result.failure()
            } else {
                sharedPrefs.edit().putInt("failure_count", 0).apply()
                Result.success()
            }
        } catch (e: CancellationException) {
            // WorkManager cancellation is not a sync failure: never count/alert it.
            throw e
        } catch (e: Exception) {
            countAndMaybeAlert(e.message ?: "Unknown error")
            Result.retry()
        }
    }

    // Shared failure-count/alert path: grows the consecutive-failure counter and raises
    // the sync alert once it keeps happening (>= 5) and the user hasn't turned alerts off.
    private suspend fun countAndMaybeAlert(message: String) {
        val count = sharedPrefs.getInt("failure_count", 0) + 1
        sharedPrefs.edit().putInt("failure_count", count).apply()

        val settings = settingsManager.settingsFlow.first()
        if (count >= 5 && settings.syncAlertsEnabled) {
            notificationHelper.showSyncAlert(message)
        }
    }
}
