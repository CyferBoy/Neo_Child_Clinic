package com.neochildclinic.core.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the coalescing gate used by RefreshDataUseCase and
 * SyncRepositoryImpl.processNextItems: concurrent callers must share one run instead of
 * executing the same refresh/upload pipeline twice at the same time (which caused
 * duplicate uploads and overlapping pulls of the same tables).
 *
 * Contract: the block is executed by exactly ONE caller; concurrent callers wait for
 * that run to finish and then return without re-running it (join, don't duplicate).
 */
class SingleFlightTest {

    @Test
    fun `concurrent callers execute the block exactly once`() = runBlocking {
        val flight = SingleFlight()
        val firstInside = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var executions = 0

        val first = async {
            flight.run {
                executions++
                firstInside.complete(Unit)
                releaseFirst.await()
            }
        }
        firstInside.await() // the first caller now owns the gate

        val second = async { flight.run { executions++ } }
        yield() // let the second caller reach the gate and start waiting
        releaseFirst.complete(Unit)

        first.await()
        second.await()
        assertEquals(1, executions)
    }

    @Test
    fun `second caller only returns after the first run finished`() = runBlocking {
        val flight = SingleFlight()
        val firstInside = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var firstDone = false
        var observedFirstDoneOnReturn = false

        val first = async {
            flight.run {
                firstInside.complete(Unit)
                releaseFirst.await()
                firstDone = true
            }
        }
        firstInside.await()

        val second = async {
            flight.run { /* join only: the block is not re-run */ }
            observedFirstDoneOnReturn = firstDone
        }
        yield() // second is waiting on the first run
        releaseFirst.complete(Unit)

        first.await()
        second.await()
        assertTrue(observedFirstDoneOnReturn)
    }

    @Test
    fun `a failing run releases the gate for the next caller`() = runBlocking {
        val flight = SingleFlight()

        try {
            flight.run { throw IllegalStateException("boom") }
        } catch (expected: IllegalStateException) {
            // expected
        }

        var ranAfterFailure = false
        flight.run { ranAfterFailure = true }
        assertTrue(ranAfterFailure)
    }

    @Test
    fun `sequential callers each run`() = runBlocking {
        val flight = SingleFlight()
        var executions = 0
        flight.run { executions++ }
        flight.run { executions++ }
        assertEquals(2, executions)
    }

    @Test
    fun `cancelled caller releases the gate and does not poison future callers`() = runBlocking {
        val flight = SingleFlight()
        val firstInside = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()
        val job = launch {
            flight.run {
                firstInside.complete(Unit)
                neverCompletes.await()
            }
        }
        firstInside.await()
        job.cancel()
        job.join()

        var executed = false
        flight.run { executed = true }
        assertTrue("gate must not be poisoned", executed)
    }
}
