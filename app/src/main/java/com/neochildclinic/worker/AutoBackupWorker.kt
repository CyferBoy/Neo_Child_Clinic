package com.neochildclinic.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neochildclinic.data.repository.BackupRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs Automatic Backup on the schedule set in Settings -> Backup & Restore
 * (see BackupAutoScheduler for how this is enqueued).
 *
 * Retry-safe by construction: BackupRepositoryImpl.performAutomaticBackup() reads current
 * settings/password fresh every run and each destination (local file / cloud upload) is
 * idempotent to re-run (a retried local write overwrites the same-named file harmlessly; a
 * retried cloud upload is a fresh backupId, so a WorkManager retry never doubles up in a way
 * that corrupts anything - at most it produces one extra rotated-away backup).
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupRepository: BackupRepositoryImpl
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val result = backupRepository.performAutomaticBackup()
            if (result.success) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "auto_backup_work"
    }
}
