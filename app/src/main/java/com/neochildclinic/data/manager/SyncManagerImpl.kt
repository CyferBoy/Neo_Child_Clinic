package com.neochildclinic.data.manager

import android.content.Context
import androidx.work.*
import com.neochildclinic.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager = WorkManager.getInstance(context)

    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("SYNC_JOB")
            .build()

        workManager.enqueueUniqueWork(
            "AUTOMATIC_SYNC",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }

    fun scheduleImmediateSync() {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("IMMEDIATE_SYNC")
            .build()

        workManager.enqueueUniqueWork(
            "IMMEDIATE_SYNC",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
