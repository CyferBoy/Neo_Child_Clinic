package com.neochildclinic.core.sync

import kotlinx.coroutines.sync.Mutex

/**
 * Coalesces concurrent callers onto one in-flight run.
 *
 * The first caller executes [block]; callers that arrive while it runs wait for it to
 * finish instead of starting a second run (and only then return). Used by both the
 * download pipeline (RefreshDataUseCase) and the upload pipeline
 * (SyncRepositoryImpl.processNextItems) so two screens, a reconnect and a worker can
 * never run the same work concurrently.
 */
class SingleFlight {
    private val mutex = Mutex()

    suspend fun run(block: suspend () -> Unit) {
        if (mutex.tryLock()) {
            try {
                block()
            } finally {
                mutex.unlock()
            }
        } else {
            // Someone else is running: wait for their run to finish so this caller
            // returns with the work done, then release without re-running.
            mutex.lock()
            mutex.unlock()
        }
    }
}
