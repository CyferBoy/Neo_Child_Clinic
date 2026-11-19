package com.neochildclinic.qafix

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QA-03: updateBatch() must commit all-or-nothing against a REAL database. The original
 * unit test used a fake runner that recreated "rolled back" state by hand, so it could not
 * fail if production stopped using a transaction. Here a forced duplicate PK raises a real
 * SQLite constraint error mid-transaction and the real Room transaction must undo everything.
 *
 * Runs in androidTest because a real Room DB needs an instrumented process (the unit-test
 * source set has no SQLite/Robolectric dependency).
 */
@RunWith(AndroidJUnit4::class)
class InventoryAtomicRollbackTest {

    private lateinit var rig: InventoryRoomTestRig

    @After
    fun tearDown() {
        if (::rig.isInitialized) rig.close()
    }

    @Test
    fun duplicatePkInLedger_rollsBackBatchQuantityLedgerAndQueue() = runBlocking {
        rig = InventoryRoomTestRig(ApplicationProvider.getApplicationContext(), duplicatePkInLedger = true)
        rig.seedBatch()

        val failure = runCatching {
            rig.repository.updateBatch(rig.updatedBatch(4), "dr_user", "Faulty adjustment")
        }.exceptionOrNull()

        assertTrue("expected a real SQLite UNIQUE violation, got $failure", failure is SQLiteConstraintException)
        assertEquals("batch quantity must roll back to 10", 10, rig.remaining())
        assertEquals("no ledger row may survive", 0, rig.ledgerCount())
        assertEquals("no sync_queue row may survive", 0, rig.queueCount())
    }

    @Test
    fun duplicatePkDuringQueueWrite_rollsBackTheAlreadyWrittenQueueRow() = runBlocking {
        // The ledger insert and the first enqueue succeed; the second enqueue fails with a
        // duplicate PK. All three writes share one transaction, so all three must disappear.
        rig = InventoryRoomTestRig(ApplicationProvider.getApplicationContext(), failOnEnqueueNumber = 2)
        rig.seedBatch()

        val failure = runCatching {
            rig.repository.updateBatch(rig.updatedBatch(2), "dr_user", "Failing enqueue")
        }.exceptionOrNull()

        assertTrue("expected a forced duplicate-PK failure, got $failure", failure is SQLiteConstraintException)
        assertEquals("batch quantity must roll back to 10", 10, rig.remaining())
        assertEquals("ledger row must roll back", 0, rig.ledgerCount())
        assertEquals("first enqueue must roll back too", 0, rig.queueCount())
        assertEquals("two enqueue attempts were made", 2, rig.enqueued.size)
    }
}
