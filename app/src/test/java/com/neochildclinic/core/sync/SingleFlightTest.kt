package com.neochildclinic.core.sync

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
        var executions = 0

        val a = async { flight.run { executions++; delay(50) } }
        val b = async { flight.run { executions++; delay(50) } }
        a.await()
        b.await()

        assertEquals(1, executions)
    }

    @Test
    fun `second caller only returns after the first run finished`() = runBlocking {
        val flight = SingleFlight()
        var firstDone = false
        var observedFirstDoneOnReturn = false

        val first = async {
            flight.run { delay(80); firstDone = true }
        }
        delay(10) // make sure first is inside the block
        val second = async {
            flight.run { /* join only: the block is not re-run */ }
            observedFirstDoneOnReturn = firstDone
        }
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
}
