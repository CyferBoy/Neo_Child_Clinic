package com.neochildclinic.core.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sync pipeline coalesces concurrent callers onto one run. Two properties matter and both
 * used to be wrong:
 *
 *  - a waiting caller must receive the OWNER's outcome. It used to get a default `false`,
 *    which SyncRepositoryImpl returned as "nothing failed", so a manual "Sync now" pressed
 *    while a worker run was failing took the success branch and RESET failure_count,
 *    cancelling the "Some changes could not be synced" alert that was about to fire.
 *  - a failed run must still release the gate, so the next caller can run.
 *
 * Uses runBlocking with UNDISPATCHED starts (no virtual time needed: the owner is held on an
 * explicit CompletableDeferred) so the interleaving is deterministic and costs no wall clock.
 */
class SingleFlightResultTest {

    @Test
    fun `a waiting caller receives the owner's result`() = runBlocking {
        val flight = SingleFlight()
        val release = CompletableDeferred<Unit>()
        var ownerEnteredBlock = false
        val owner = async(start = CoroutineStart.UNDISPATCHED) {
            flight.run {
                ownerEnteredBlock = true
                release.await()
                true
            }
        }
        assertTrue("the owner must enter its block eagerly (UNDISPATCHED start)", ownerEnteredBlock)
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            // Explicit <Boolean>: a bare error(...) lambda infers Nothing, which makes the
            // caller's await() compile into a deliberate throw regardless of the result.
            flight.run<Boolean> { error("a waiting caller must not re-run the block") }
        }

        release.complete(Unit)
        assertTrue("the owner must see its own success", owner.await())
        assertTrue("a waiting caller must inherit the owner's result", waiter.await())
    }

    @Test
    fun `a waiting caller inherits a false result`() = runBlocking {
        val flight = SingleFlight()
        val release = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) {
            flight.run {
                release.await()
                false
            }
        }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            // Explicit <Boolean>: a bare error(...) lambda infers Nothing, which makes the
            // caller's await() compile into a deliberate throw regardless of the result.
            flight.run<Boolean> { error("a waiting caller must not re-run the block") }
        }

        release.complete(Unit)
        assertFalse(owner.await())
        assertFalse("a waiting caller must not report success when the run failed", waiter.await())
    }

    @Test
    fun `the owner sees the failure and the gate is released`() = runBlocking {
        val flight = SingleFlight()
        var threw = false
        try {
            flight.run { throw IllegalStateException("boom") }
        } catch (expected: IllegalStateException) {
            threw = true
        }
        assertTrue("the owner must see the failure", threw)

        // Gate released: the next caller runs its own block.
        var ran = false
        flight.run { ran = true }
        assertTrue("the gate must be released after a failure", ran)
    }

    @Test
    fun `sequential callers each run their own block`() = runBlocking {
        val flight = SingleFlight()
        assertTrue(flight.run { true })
        assertTrue(flight.run { true })
    }
}