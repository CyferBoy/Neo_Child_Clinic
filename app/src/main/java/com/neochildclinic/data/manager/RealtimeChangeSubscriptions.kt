package com.neochildclinic.data.manager

import android.util.Log
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
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
                        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                            this.table = table
                        }.onEach {
                            trySend(Unit)
                        }.catch { e ->
                            Log.e("Realtime", "Error in $table change flow", e)
                        }.collect()
                    }
                }

                channel.subscribe()
            } catch (e: Exception) {
                Log.e("Realtime", "Error setting up realtime changes for $channelName", e)
            }
        }
        awaitClose {
            changeScope.launch {
                runCatching {
                    val channel = realtime.subscriptions["realtime:$channelName"]
                    if (channel != null) realtime.removeChannel(channel)
                }
            }
            changeScope.cancel()
        }
    }
}