package com.neochildclinic.core.sync

import android.content.Context
import androidx.work.*
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

        // REPLACE, not APPEND_OR_REPLACE: scheduleSync() fires once per QUEUED ROW (and on
        // every onResume and transient failure), so appending chained a dependent request per
        // row. One deletePatient enqueues dozens inside a single transaction; offline those
        // requests accumulated and, on reconnect, the whole chain ran sequentially against the
        // same now-empty queue. REPLACE collapses any pending chain to the newest request.
        workManager.enqueueUniqueWork(
            "AUTOMATIC_SYNC",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    fun scheduleImmediateSync() {
        // Same CONNECTED constraint as scheduleSync(): without it this expedited request
        // runs immediately while offline, fails every upload, bumps the failure counter
        // and burns WorkManager retry budget for nothing. Connectivity back only releases
        // it, so "immediate" still means "as soon as it can possibly succeed".
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
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
