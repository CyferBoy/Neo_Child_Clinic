package com.neochildclinic.core.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coalesces concurrent callers onto one in-flight run and hands every caller the owner's
 * outcome.
 *
 * The first caller executes [block]; callers that arrive while it runs wait for it and get its
 * result instead of starting a second run. Used by both the download pipeline
 * (RefreshDataUseCase) and the upload pipeline (SyncRepositoryImpl.processNextItems) so two
 * screens, a reconnect and a worker can never run the same work concurrently.
 *
 * Returning the owner's value matters: the old version returned Unit, so a coalesced caller
 * read a defaulted "nothing failed" out of its own variable while someone else's run was
 * failing. SyncWorker then took the success branch and reset failure_count, cancelling the
 * "Some changes could not be synced" alert. Failures propagate to waiters for the same reason.
 */
class SingleFlight {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Any?>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> run(key: String = "", block: suspend () -> T): T {
        val mine = CompletableDeferred<Any?>()

        mutex.lock()
        val running = inFlight[key]
        if (running != null) {
            // Someone else is running this key: wait for their outcome rather than re-running the work.
            mutex.unlock()
            return running.await() as T
        }
        inFlight[key] = mine
        mutex.unlock()

        try {
            val value = block()
            mine.complete(value)
            return value
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    inFlight.remove(key)
                }
            }
        }
    }
}