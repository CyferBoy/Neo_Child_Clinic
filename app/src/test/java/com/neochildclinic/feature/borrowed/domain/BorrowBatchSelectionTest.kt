package com.neochildclinic.feature.borrowed.domain

import com.neochildclinic.domain.model.VaccineBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A borrow record stores ONE batchId, but the deduction it used to run spread the quantity
 * across batches earliest-expiry-first. Returning the borrow then credited that single
 * recorded batch with the full quantity, permanently inventing doses on the shelf.
 * A physical borrow must therefore come out of ONE batch - these tests pin the selection
 * rule that keeps the recorded batchId and the deducted batch the same batch.
 */
class BorrowBatchSelectionTest {

    private fun batch(
        id: String,
        remaining: Int,
        expiry: String,
        number: String = id
    ) = VaccineBatch(
        batchId = id,
        vaccineId = "v1",
        batchNumber = number,
        purchaseDate = "2025-01-01",
        expiryDate = expiry,
        purchaseQuantity = remaining,
        remainingQuantity = remaining,
        purchaseCost = 10.0,
        sellingPrice = 20.0
    )

    @Test
    fun `the chosen batch is used when it holds the whole quantity`() {
        val chosen = selectBorrowBatch(
            batches = listOf(batch("b1", remaining = 3, expiry = "2030-01-01"), batch("b2", remaining = 10, expiry = "2029-01-01")),
            quantity = 3,
            preferredBatchId = "b1"
        )
        assertEquals("b1", chosen?.batchId)
    }

    @Test
    fun `a preferred batch too small for the quantity is not used`() {
        // This is the corruption case: b1 holds 3 of the 5 doses, so deducting 5 from b1 alone
        // is impossible and silently spanning batches loses doses on return.
        val chosen = selectBorrowBatch(
            batches = listOf(batch("b1", remaining = 3, expiry = "2030-01-01"), batch("b2", remaining = 10, expiry = "2029-01-01")),
            quantity = 5,
            preferredBatchId = "b1"
        )
        assertEquals("b2", chosen?.batchId)
    }

    @Test
    fun `earliest expiry wins when there is no preference`() {
        val chosen = selectBorrowBatch(
            batches = listOf(
                batch("late", remaining = 10, expiry = "2030-01-01"),
                batch("early", remaining = 10, expiry = "2027-06-30"),
                batch("mid", remaining = 10, expiry = "2028-03-01")
            ),
            quantity = 4,
            preferredBatchId = null
        )
        assertEquals("early", chosen?.batchId)
    }

    @Test
    fun `empty and unusable batches are never chosen`() {
        val chosen = selectBorrowBatch(
            batches = listOf(batch("empty", remaining = 0, expiry = "2027-01-01")),
            quantity = 1,
            preferredBatchId = null
        )
        assertNull(chosen)
    }

    @Test
    fun `null when no single batch can cover the whole quantity`() {
        // The clinic must record two borrows rather than one that cannot be returned correctly.
        val chosen = selectBorrowBatch(
            batches = listOf(batch("b1", remaining = 3, expiry = "2030-01-01"), batch("b2", remaining = 2, expiry = "2029-01-01")),
            quantity = 5,
            preferredBatchId = null
        )
        assertNull(chosen)
    }

    @Test
    fun `an expired preferred batch is refused`() {
        val chosen = selectBorrowBatch(
            batches = listOf(batch("b1", remaining = 50, expiry = "2020-01-01"), batch("b2", remaining = 10, expiry = "2030-01-01")),
            quantity = 5,
            preferredBatchId = "b1"
        )
        assertEquals("b2", chosen?.batchId)
    }

    @Test
    fun `a non-positive quantity never selects a batch`() {
        assertNull(selectBorrowBatch(listOf(batch("b1", 10, "2030-01-01")), quantity = 0, preferredBatchId = "b1"))
        assertNull(selectBorrowBatch(listOf(batch("b1", 10, "2030-01-01")), quantity = -3, preferredBatchId = null))
    }

    @Test
    fun `an unparseable expiry date does not block a usable batch`() {
        val chosen = selectBorrowBatch(
            batches = listOf(batch("weird", remaining = 10, expiry = "not-a-date"), batch("b2", remaining = 10, expiry = "2030-01-01")),
            quantity = 2,
            preferredBatchId = null
        )
        assertEquals("b2", chosen?.batchId)
    }
}