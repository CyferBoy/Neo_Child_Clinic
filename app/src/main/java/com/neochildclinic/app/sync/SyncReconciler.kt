package com.neochildclinic.app.sync

import android.content.Context
import android.util.Log
import com.neochildclinic.core.network.NetworkMonitor
import com.neochildclinic.core.sync.SingleFlight
import com.neochildclinic.core.sync.RefreshDataUseCase
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.feature.widget.data.WidgetUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level reconciliation trigger: the one place that turns "online AND signed in"
 * into a full sync pass.
 *
 * Realtime events can be missed while the device is offline, and this app had no
 * startup/reconnect pull at all before this class (the 15-minute periodic worker only
 * *uploads*), so remote changes made during disconnection were picked up only if the
 * user happened to open a screen with a pull-to-refresh. [start] runs one pass:
 *
 *  - when the app starts already online + authenticated (session restored from disk), and
 *  - on every offline -> online edge while authenticated.
 *
 * Order is outbound first, then inbound: pending local edits upload before the pull
 * compares `updated_at`, so a just-made local edit cannot lose a conflict it would have
 * won. Both legs are single-flight (SyncRepositoryImpl for the queue, RefreshDataUseCase
 * for the pipeline), so a reconciler pass never runs concurrently with a worker or a
 * screen's pull-to-refresh. Local reads never wait on any of this - Room serves cached
 * data throughout.
 *
 * Realtime remains a timely trigger while connected; this is the correctness net for
 * events Realtime never delivered. Neither replaces the sync_queue/WorkManager push path.
 *
 * SY-02: the inbound leg is gated by [shouldRunFullRefresh], so a warm recent full pass
 * (or a flapping connection) does not re-pull all 13 tables. Outbound push still runs on
 * every ready edge, and the first-ever launch (no stored timestamp) always pulls.
 */
@Singleton
class SyncReconciler @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val auth: Auth,
    private val refreshDataUseCase: RefreshDataUseCase,
    private val syncRepository: SyncRepository,
    @ApplicationContext private val context: Context
) {
    private val flight = SingleFlight()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    // Reuse the existing sync preferences file (SyncWorker already stores failure_count
    // there) rather than introducing a new DataStore for one timestamp.
    private val prefs by lazy { context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE) }

    /** Idempotent; call once from Application.onCreate. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(networkMonitor.isOnline, auth.sessionStatus) { online, session ->
                online && session is SessionStatus.Authenticated
            }
                .distinctUntilChanged()
                .collect { ready ->
                    if (ready) reconcile()
                }
        }
    }

    private suspend fun reconcile() = flight.run {
        runCatching { syncRepository.processNextItems() }
            .onFailure { Log.w(TAG, "Outbound sync pass failed", it) }

        if (shouldRunFullRefresh(prefs.getLong(KEY_LAST_FULL_REFRESH_AT, 0L))) {
            // Only a successful pass marks the data fresh - a failed pull must be retried on
            // the next start/reconnect instead of suppressing the safety net.
            runCatching { refreshDataUseCase() }
                .onSuccess {
                    prefs.edit().putLong(KEY_LAST_FULL_REFRESH_AT, System.currentTimeMillis()).apply()
                    WidgetUtils.updateWidget(context)
                }
                .onFailure { Log.w(TAG, "Inbound refresh pass failed", it) }
        } else {
            Log.d(TAG, "Skipping full refresh: last successful pass is still fresh")
        }
    }

    private companion object {
        const val TAG = "SyncReconciler"
        const val SYNC_PREFS = "sync_prefs"
        const val KEY_LAST_FULL_REFRESH_AT = "last_full_refresh_at"
    }
}

/** How long a successful full pull keeps the inbound safety net from running again. */
internal const val FULL_REFRESH_STALENESS_MS = 15 * 60 * 1000L

/**
 * Pure staleness gate (no Android dependencies so it is unit-testable). First-ever launch
 * stores no timestamp (0L) and must always pull; a fresh pass is skipped.
 */
internal fun shouldRunFullRefresh(
    lastFullRefreshAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    stalenessMs: Long = FULL_REFRESH_STALENESS_MS
): Boolean =
    lastFullRefreshAtMillis <= 0L || nowMillis - lastFullRefreshAtMillis >= stalenessMs
