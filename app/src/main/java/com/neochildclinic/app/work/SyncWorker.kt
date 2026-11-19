package com.neochildclinic.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.neochildclinic.core.preferences.NotificationSettingsManager
import com.neochildclinic.core.notification.NotificationHelper
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.SyncRunResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal enum class SyncFailureAction { INCREMENT, RESET }

internal data class SyncWorkOutcome(
    val result: ListenableWorker.Result,
    val failureAction: SyncFailureAction,
    val alertMessage: String? = null
)

// Pure decision logic, kept out of doWork() so it can be unit-tested without a WorkManager
// runtime (which needs a real Context/worker params that JVM tests cannot build).
internal fun classifySyncWork(
    permanentFailure: Boolean,
    hasFailedItems: Boolean,
    error: Throwable?
): SyncWorkOutcome = when {
    error != null -> SyncWorkOutcome(
        ListenableWorker.Result.retry(), SyncFailureAction.INCREMENT, error.message ?: "Unknown error"
    )
    permanentFailure -> SyncWorkOutcome(
        ListenableWorker.Result.failure(), SyncFailureAction.INCREMENT, "Some changes could not be synced"
    )
    hasFailedItems -> SyncWorkOutcome(
        ListenableWorker.Result.success(), SyncFailureAction.INCREMENT, "Some changes could not be synced"
    )
    else -> SyncWorkOutcome(ListenableWorker.Result.success(), SyncFailureAction.RESET)
}

internal fun nextFailureCount(current: Int, action: SyncFailureAction): Int =
    if (action == SyncFailureAction.INCREMENT) current + 1 else 0

internal fun shouldRaiseSyncAlert(failureCount: Int, syncAlertsEnabled: Boolean): Boolean =
    failureCount >= 5 && syncAlertsEnabled

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
        // Several work names (automatic, immediate, periodic) trigger this worker, but their
        // processNextItems calls coalesce onto one single-flight run. Only that run's owner may
        // count/alert its outcome - counting per worker added +1 for each coalesced caller and
        // fired false alerts for a single failure.
        val run = try {
            syncRepository.processNextItemsWithOwnership()
        } catch (e: CancellationException) {
            // WorkManager cancellation is not a sync failure: never count/alert it.
            throw e
        } catch (e: Exception) {
            SyncRunResult(false, false, e, isOwner = true)
        }
        val outcome = classifySyncWork(run.permanentFailure, run.hasFailedItems, run.error)

        if (run.isOwner) {
            val count = nextFailureCount(sharedPrefs.getInt("failure_count", 0), outcome.failureAction)
            sharedPrefs.edit().putInt("failure_count", count).apply()
            // Alerts are only consulted on the failure path, once the consecutive count hits 5
            // and the user has not turned sync alerts off.
            if (outcome.failureAction == SyncFailureAction.INCREMENT) {
                val settings = settingsManager.settingsFlow.first()
                if (shouldRaiseSyncAlert(count, settings.syncAlertsEnabled)) {
                    notificationHelper.showSyncAlert(outcome.alertMessage ?: "Unknown error")
                }
            }
        }
        return outcome.result
    }
}
