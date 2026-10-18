package com.neochildclinic.core.sync

import android.util.Log
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges Supabase Realtime into a Flow<Unit> so feature layers never touch the channel
 * API. This is only ever a "something changed remotely, re-pull from local" trigger - the
 * existing sync_queue/SyncWorker path remains the only write path to the cloud.
 *
 * Channel lifecycle is tied to the collector: when the collecting scope is cancelled the
 * channel is removed, so callers no longer need their own onCleared() teardown.
 */
/** Exponential backoff bounds for resubscribing a realtime channel. */
private const val INITIAL_RETRY_DELAY_MS = 1_000L
private const val MAX_RETRY_DELAY_MS = 60_000L

@Singleton
class RealtimeChangeSubscriptions @Inject constructor(
    private val realtime: Realtime
) {
    fun tableChanges(channelName: String, vararg tables: String): Flow<Unit> = callbackFlow {
        val changeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        changeScope.launch {
            try {
                // Ensure any previous subscription with the same name is removed to avoid "already joined" error
                realtime.subscriptions["realtime:$channelName"]?.let { old ->
                    runCatching { realtime.removeChannel(old) }
                }

                val channel = realtime.channel(channelName)

                // postgresChangeFlow MUST be called BEFORE channel.subscribe()
                tables.forEach { table ->
                    changeScope.launch {
                        // Retry instead of terminating. A dropped socket, a failed token
                        // refresh or a server-side channel error is routine in the field, and
                        // .catch { log } used to complete the flow on the first one - ending
                        // this coroutine for good. The channel stayed joined, so nothing looked
                        // broken and no further change ever arrived until the process restarted
                        // (NCC-024). Retry until the collecting scope is cancelled.
                        var backoffMs = INITIAL_RETRY_DELAY_MS
                        while (isActive) {
                            try {
                                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                                    this.table = table
                                }.collect {
                                    trySend(Unit)
                                }
                                // Reached only if the stream completes on its own, which for a
                                // live subscription means it will never deliver again. Treat it
                                // as a failure so the loop re-subscribes rather than exiting.
                                error("realtime change flow for $table completed")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(
                                    "Realtime",
                                    "Error in $table change flow; retrying in ${backoffMs}ms",
                                    e
                                )
                                delay(backoffMs)
                                backoffMs = (backoffMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                            }
                        }
                    }
                }

                channel.subscribe()
            } catch (e: Exception) {
                Log.e("Realtime", "Error setting up realtime changes for $channelName", e)
            }
        }
        awaitClose {
            // Remove the channel BEFORE cancelling the scope. The previous code launched
            // the removal and then cancelled the scope in the next statement, which could
            // cancel the removal coroutine before it ever ran - leaving a joined channel
            // behind until some later subscription with the same name cleaned it up.
            changeScope.launch {
                try {
                    realtime.subscriptions["realtime:$channelName"]?.let { old ->
                        realtime.removeChannel(old)
                    }
                } catch (e: Exception) {
                    Log.e("Realtime", "Error removing channel $channelName", e)
                } finally {
                    changeScope.cancel()
                }
            }
        }
    }
}