package com.neochildclinic.qafix

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neochildclinic.domain.model.InventoryTransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NCC-021: deductStockFromBatch is a read-modify-write. Without a transaction, parallel
 * deductions read the same remainingQuantity and the last write discards the earlier ones -
 * stock drops by one dose while the ledger and queue record every deduction. This launches
 * the real repository against a real Room DB and asserts every deduction landed everywhere.
 */
@RunWith(AndroidJUnit4::class)
class InventoryConcurrentDeductionTest {

    @Test
    fun parallelDeductionsAllReflectInStockLedgerAndQueue() = runBlocking {
        val rig = InventoryRoomTestRig(ApplicationProvider.getApplicationContext())
        try {
            val count = 20
            rig.seedBatch(remaining = 50)

            kotlinx.coroutines.coroutineScope {
                repeat(count) { i ->
                    launch(Dispatchers.IO) {
                        rig.repository.deductStockFromBatch(
                            batchId = "batch-101",
                            quantity = 1,
                            user = "nurse-$i",
                            transactionType = InventoryTransactionType.VACCINATION,
                            visitId = "visit-$i",
                            patientId = "p-$i",
                            notes = null,
                            givenDate = null,
                            transactionGroupId = null
                        )
                    }
                }
            }

            assertEquals("every deduction must decrement stock", 50 - count, rig.remaining())
            assertEquals("every deduction must write one ledger row", count, rig.ledgerCount())
            assertEquals("every deduction must enqueue transaction + batch", count * 2, rig.queueCount())
        } finally {
            rig.close()
        }
    }
}
