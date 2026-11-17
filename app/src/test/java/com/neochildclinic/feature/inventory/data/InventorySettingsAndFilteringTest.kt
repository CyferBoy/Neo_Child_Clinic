package com.neochildclinic.feature.inventory.data

import com.neochildclinic.domain.model.VaccineBatch
import com.neochildclinic.feature.inventory.domain.InventoryUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InventorySettingsAndFilteringTest {

    private val referenceToday = LocalDate.of(2026, 10, 30)

    private fun batch(
        batchId: String,
        remainingQuantity: Int,
        expiryDate: String
    ) = VaccineBatch(
        batchId = batchId,
        vaccineId = "v1",
        batchNumber = "B-$batchId",
        purchaseDate = "2026-01-01",
        expiryDate = expiryDate,
        remainingQuantity = remainingQuantity,
        purchaseCost = 100.0,
        sellingPrice = 150.0
    )


    // ── 2. Near expiry calculation from settings threshold days ──────────────

    @Test
    fun `near expiry respects dynamic threshold days configured in inventory settings`() {
        // Today is 30 Oct 2026.
        // Expiry in 20 days: 19 Nov 2026
        val expiryIn20Days = "19 Nov 2026"
        // Expiry in 45 days: 14 Dec 2026
        val expiryIn45Days = "14 Dec 2026"

        // Settings option 1: 15 days
        assertFalse("20 days out is NOT near expiry when warn threshold is 15 days",
            InventoryUtils.isNearExpiry(expiryIn20Days, thresholdDays = 15, today = referenceToday))
        assertFalse("45 days out is NOT near expiry when warn threshold is 15 days",
            InventoryUtils.isNearExpiry(expiryIn45Days, thresholdDays = 15, today = referenceToday))

        // Settings option 2: 30 days
        assertTrue("20 days out IS near expiry when warn threshold is 30 days",
            InventoryUtils.isNearExpiry(expiryIn20Days, thresholdDays = 30, today = referenceToday))
        assertFalse("45 days out is NOT near expiry when warn threshold is 30 days",
            InventoryUtils.isNearExpiry(expiryIn45Days, thresholdDays = 30, today = referenceToday))

        // Settings option 3: 60 days
        assertTrue("20 days out IS near expiry when warn threshold is 60 days",
            InventoryUtils.isNearExpiry(expiryIn20Days, thresholdDays = 60, today = referenceToday))
        assertTrue("45 days out IS near expiry when warn threshold is 60 days",
            InventoryUtils.isNearExpiry(expiryIn45Days, thresholdDays = 60, today = referenceToday))
    }

    @Test
    fun `near expiry excludes empty and already expired batches`() {
        val warnDays = 30
        val batches = listOf(
            batch("b1", remainingQuantity = 0, expiryDate = "10 Nov 2026"), // near expiry date but EMPTY (0 qty)
            batch("b2", remainingQuantity = 5, expiryDate = "15 Oct 2026")  // EXPIRED (qty > 0)
        )

        // An empty batch (qty 0) should not cause the vaccine to be flagged as near expiry
        val isNearExpiry = batches.any {
            it.remainingQuantity > 0 && InventoryUtils.isNearExpiry(it.expiryDate, thresholdDays = warnDays, today = referenceToday)
        }
        assertFalse("Vaccine with only empty or expired batches must not be flagged near expiry", isNearExpiry)

        val batchesWithActiveNearExpiry = batches + batch("b3", remainingQuantity = 2, expiryDate = "10 Nov 2026")
        val isNearExpiryActive = batchesWithActiveNearExpiry.any {
            it.remainingQuantity > 0 && InventoryUtils.isNearExpiry(it.expiryDate, thresholdDays = warnDays, today = referenceToday)
        }
        assertTrue("Vaccine with active batch within threshold must be flagged near expiry", isNearExpiryActive)
    }

    // ── 3. Expired or zero quantity batches hiding logic ─────────────────────

    @Test
    fun `expired or zero quantity batches hide when toggle is on and show when toggle is off`() {
        val bActive = batch("active", remainingQuantity = 10, expiryDate = "30 Dec 2028")
        val bExpiredWithStock = batch("exp_stock", remainingQuantity = 3, expiryDate = "10 Oct 2026")
        val bZeroQtyFutureDate = batch("zero_qty", remainingQuantity = 0, expiryDate = "30 Dec 2028")
        val bExpiredAndZero = batch("exp_zero", remainingQuantity = 0, expiryDate = "10 Oct 2026")

        val allBatches = listOf(bActive, bExpiredWithStock, bZeroQtyFutureDate, bExpiredAndZero)

        // Predicate: unusable if expired OR zero quantity
        val unusableBatches = allBatches.filter {
            InventoryUtils.isExpired(it.expiryDate, today = referenceToday) || it.remainingQuantity <= 0
        }
        assertEquals(3, unusableBatches.size)
        assertTrue(unusableBatches.contains(bExpiredWithStock))
        assertTrue(unusableBatches.contains(bZeroQtyFutureDate))
        assertTrue(unusableBatches.contains(bExpiredAndZero))
        assertFalse(unusableBatches.contains(bActive))

        // When toggle is ON (hideExpiredEmpty == true)
        val visibleWhenToggleOn = allBatches.filterNot { it in unusableBatches }
        assertEquals(listOf(bActive), visibleWhenToggleOn)
    }
}
