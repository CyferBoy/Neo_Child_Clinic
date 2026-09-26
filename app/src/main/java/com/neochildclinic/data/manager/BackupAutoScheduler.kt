package com.neochildclinic.data.manager

import android.content.Context
import androidx.work.*
import com.neochildclinic.domain.model.AutoBackupSettings
import com.neochildclinic.domain.model.BackupFrequency
import com.neochildclinic.data.settings.BackupSettingsManager
import com.neochildclinic.worker.AutoBackupWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatic Backup's WorkManager scheduling, mirroring SyncManagerImpl's pattern for
 * SyncWorker. Kept separate from SyncManagerImpl since it schedules a different worker on a
 * user-configurable cadence rather than a fixed one, and must be re-armed from stored
 * settings at app startup (see NeoChildApp.onCreate) the same way SyncManagerImpl's
 * scheduleSync() already is.
 */
@Singleton
class BackupAutoScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupSettingsManager: BackupSettingsManager
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedule(settings: AutoBackupSettings) {
        if (!settings.enabled) {
            cancel()
            return
        }
        val intervalHours = when (settings.frequency) {
            BackupFrequency.DAILY -> 24L
            BackupFrequency.WEEKLY -> 24L * 7
        }
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .apply { if (settings.cloudEnabled) setRequiredNetworkType(NetworkType.CONNECTED) }
            .build()

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AutoBackupWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(AutoBackupWorker.UNIQUE_WORK_NAME)
    }

    /** Called once at app startup so a periodic job already enqueued before a process death
     * / device reboot keeps matching the user's current settings. */
    suspend fun rearmIfEnabled() {
        val settings = backupSettingsManager.getSettings()
        if (settings.enabled) schedule(settings) else cancel()
    }
}

