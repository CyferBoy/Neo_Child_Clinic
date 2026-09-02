package com.neochildclinic.notification

import android.content.Context
import androidx.work.*
import com.neochildclinic.features.settings.NotificationSettingsManager
import com.neochildclinic.worker.DailySummaryWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: NotificationSettingsManager
) {
    companion object {
        private const val SUMMARY_WORK_NAME = "com.neochildclinic.DAILY_SUMMARY"
        private const val PERSONAL_REMINDER_WORK_NAME = "com.neochildclinic.PERSONAL_REMINDERS"
    }

    suspend fun scheduleDailySummary() {
        val settings = settingsManager.settingsFlow.first()
        if (!settings.dailySummaryEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(SUMMARY_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Calculate delay until the configured time (e.g., 08:00 AM)
        val timeParts = settings.reminderTime.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = calendar.timeInMillis - System.currentTimeMillis()

        // We run it every hour internally to check for low stock transitions, 
        // but the Summary logic inside the worker will only trigger once per day.
        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(
            1, TimeUnit.HOURS
        )
        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
        .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SUMMARY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }


    suspend fun schedulePersonalReminderNotifications() {
        val settings = settingsManager.settingsFlow.first()
        val timeParts = settings.reminderTime.split(":")
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(Calendar.DAY_OF_YEAR, 1)
        val request = PeriodicWorkRequestBuilder<com.neochildclinic.worker.PersonalReminderNotificationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(calendar.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERSONAL_REMINDER_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun runNow() {
        val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
            
        WorkManager.getInstance(context).enqueue(request)
    }
}
