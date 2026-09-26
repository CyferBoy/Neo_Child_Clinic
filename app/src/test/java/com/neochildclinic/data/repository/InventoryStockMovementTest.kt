package com.neochildclinic.data.repository

import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.InventoryTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryStockMovementTest {

    private fun batch(remaining: Int = 10, used: Int = 0, wasted: Int = 0, borrowed: Int = 0) = VaccineBatchEntity(
        batchId = "b1",
        vaccineId = "v1",
        batchNumber = "B1",
        manufacturer = "M",
        purchaseDate = "2025-01-01",
        expiryDate = "2030-01-01",
        purchaseQuantity = remaining,
        remainingQuantity = remaining,
        supplier = "S",
        purchaseCost = 10.0,
        sellingPrice = 20.0,
        usedQuantity = used,
        wastedQuantity = wasted,
        borrowedQuantity = borrowed
    )

    @Test
    fun `vaccination deduction routes quantity into the used bucket`() {
        val deducted = batch().deducted(4, InventoryTransactionType.VACCINATION, "doctor")
        assertEquals(6, deducted.remainingQuantity)
        assertEquals(4, deducted.usedQuantity)
        assertEquals("doctor", deducted.updatedBy)
    }

    @Test
    fun `expired damage and cold chain deductions route into the wasted bucket`() {
        listOf(
            InventoryTransactionType.EXPIRED,
            InventoryTransactionType.DAMAGED,
            InventoryTransactionType.COLD_CHAIN_FAILURE,
            InventoryTransactionType.CONTAMINATED,
            InventoryTransactionType.OTHER
        ).forEach { type ->
            val deducted = batch().deducted(2, type, "owner")
            assertEquals(8, deducted.remainingQuantity)
            assertEquals(2, deducted.wastedQuantity)
        }
    }

    @Test
    fun `borrowed deduction routes into the borrowed bucket`() {
        val deducted = batch().deducted(3, InventoryTransactionType.BORROWED, "owner")
        assertEquals(7, deducted.remainingQuantity)
        assertEquals(3, deducted.borrowedQuantity)
    }

    @Test
    fun `stock transaction maps the movement fields and stamps user and timestamp`() {
        val tx = buildStockTransaction(
            vaccineId = "v1",
            batchId = "b1",
            transactionType = InventoryTransactionType.REVERSAL,
            quantity = 2,
            previousQuantity = 5,
            currentQuantity = 7,
            user = "owner",
            notes = "reversal",
            patientId = "p1",
            visitId = "visit-1"
        )
        assertEquals("REVERSAL", tx.transactionType)
        assertEquals(2, tx.quantity)
        assertEquals(5, tx.previousQuantity)
        assertEquals(7, tx.currentQuantity)
        assertEquals("p1", tx.patientId)
        assertEquals("visit-1", tx.visitId)
        assertEquals("owner", tx.createdBy)
        assertEquals("owner", tx.updatedBy)
        assertTrue(tx.transactionId.isNotBlank()) // generated, not fixed
        val tx2 = buildStockTransaction("v1", "b1", InventoryTransactionType.PURCHASE, 5, 0, 5, "owner", null)
        assertEquals("PURCHASE", tx2.transactionType)
        assertNull(tx2.patientId)
    }
}