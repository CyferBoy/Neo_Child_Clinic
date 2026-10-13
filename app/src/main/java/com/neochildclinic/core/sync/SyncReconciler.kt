package com.neochildclinic.core.sync

import android.util.Log
import com.neochildclinic.core.network.NetworkMonitor
import com.neochildclinic.feature.sync.domain.RefreshDataUseCase
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
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
 */
@Singleton
class SyncReconciler @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val auth: Auth,
    private val refreshDataUseCase: RefreshDataUseCase,
    private val syncRepository: SyncRepository
) {
    private val flight = SingleFlight()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

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
        runCatching { refreshDataUseCase() }
            .onFailure { Log.w(TAG, "Inbound refresh pass failed", it) }
    }

    private companion object {
        const val TAG = "SyncReconciler"
    }
}
