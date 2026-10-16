package com.neochildclinic.feature.inventory.data

import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.domain.model.VaccineBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the inventory list sort keys (NCC-017, NCC-040).
 *
 * Constants.DATE_FORMAT is "d MMM yyyy" and is NOT zero-padded. Comparing those strings
 * as text misorders any date whose day-of-month has a different digit count:
 *
 *   "16 Oct 2025" < "9 Oct 2025"    (same month - '1' < '9')
 *   "2 Jan 2026"  < "28 Dec 2025"   (across a boundary)
 *
 * Both are backwards, so the textual max picked the OLDEST batch and the MRP/net-rate
 * fallback used stale pricing.
 *
 * Pure logic, no Room, no Android.
 */
class InventorySortKeysTest {

    private fun batch(purchase: String = "1 Jan 2026", expiry: String = "1 Jan 2027") =
        VaccineBatch(
            batchId = "b", vaccineId = "v", batchNumber = "LOT",
            purchaseDate = purchase, expiryDate = expiry,
            remainingQuantity = 5, purchaseCost = 0.0, sellingPrice = 0.0,
        )

    // ── the string-compare trap, made explicit ───────────────────────────────

    @Test
    fun `the stored format really does misorder across a month boundary`() {
        // Guards the premise of every other test here. If Constants.DATE_FORMAT ever gains
        // zero padding, the remaining tests stop proving anything.
        assertTrue("2 Jan 2026".compareTo("28 Dec 2025") < 0)
    }

    @Test
    fun `the stored format misorders same-month dates too`() {
        // '1' (0x31) < '9' (0x39), so a two-digit day sorts BEFORE a one-digit day.
        // This is why EXPIRY was wrong for most of a year, not just at month boundaries.
        assertTrue("16 Oct 2025".compareTo("9 Oct 2025") < 0)
        assertTrue("20 Oct 2025".compareTo("3 Oct 2025") < 0)
    }

    // ── expiry key ───────────────────────────────────────────────────────────

    @Test
    fun `expiry key orders 2 Jan after 28 Dec`() {
        assertTrue(
            listOf(batch(expiry = "28 Dec 2025")).batchExpiryMillis()
                < listOf(batch(expiry = "2 Jan 2026")).batchExpiryMillis()
        )
    }

    @Test
    fun `expiry key handles a day-of-month digit boundary`() {
        assertTrue(
            listOf(batch(expiry = "9 Oct 2026")).batchExpiryMillis()
                < listOf(batch(expiry = "16 Oct 2026")).batchExpiryMillis()
        )
    }

    @Test
    fun `expiry key across a year boundary`() {
        assertTrue(
            listOf(batch(expiry = "28 Dec 2026")).batchExpiryMillis()
                < listOf(batch(expiry = "2 Jan 2027")).batchExpiryMillis()
        )
    }

    @Test
    fun `blank expiry sorts last`() {
        assertTrue(
            listOf(batch(expiry = "1 Jan 2030")).batchExpiryMillis()
                < listOf(batch(expiry = "")).batchExpiryMillis()
        )
    }

    @Test
    fun `empty batch list sorts last on expiry`() {
        assertEquals(Long.MAX_VALUE, emptyList<VaccineBatch>().batchExpiryMillis())
    }

    // ── newest / oldest purchase ─────────────────────────────────────────────

    /**
     * The MRP fallback regression: a textual maxByOrNull over these two returns
     * "28 Dec 2025" - the older batch.
     */
    @Test
    fun `newestBatch picks the later date across a month boundary`() {
        val batches = listOf(
            batch(purchase = "2 Jan 2026"),
            batch(purchase = "28 Dec 2025"),
        )
        // Premise: the old string-based lookup got this backwards.
        assertEquals(
            "28 Dec 2025",
            batches.maxByOrNull { it.purchaseDate }?.purchaseDate
        )
        assertEquals("2 Jan 2026", batches.newestBatch()?.purchaseDate)
    }

    @Test
    fun `latest purchase is the later date`() {
        val batches = listOf(batch(purchase = "28 Dec 2025"), batch(purchase = "2 Jan 2026"))
        assertEquals("2 Jan 2026", batches.newestBatch()?.purchaseDate)
        assertTrue(batches.latestPurchaseMillis() > batches.earliestPurchaseMillis())
    }

    @Test
    fun `earliest purchase is the earlier date`() {
        val batches = listOf(batch(purchase = "2 Jan 2026"), batch(purchase = "28 Dec 2025"))
        assertTrue(batches.earliestPurchaseMillis() < batches.latestPurchaseMillis())
        assertEquals("28 Dec 2025", batches.minByOrNull {
            PatientUtils.parseDate(it.purchaseDate)?.time ?: Long.MAX_VALUE
        }?.purchaseDate)
    }

    @Test
    fun `latest and earliest are inverses across a year boundary`() {
        val batches = listOf(
            batch(purchase = "28 Dec 2025"),
            batch(purchase = "2 Jan 2026"),
            batch(purchase = "16 Jan 2026"),
        )
        assertTrue(batches.earliestPurchaseMillis() < batches.latestPurchaseMillis())
        assertEquals("16 Jan 2026", batches.newestBatch()?.purchaseDate)
    }

    @Test
    fun `unparseable purchase dates do not win the newest slot`() {
        val batches = listOf(batch(purchase = "not-a-date"), batch(purchase = "5 May 2025"))
        assertEquals("5 May 2025", batches.newestBatch()?.purchaseDate)
    }

    @Test
    fun `empty list yields null newest batch`() {
        assertEquals(null, emptyList<VaccineBatch>().newestBatch())
        assertEquals(Long.MIN_VALUE, emptyList<VaccineBatch>().latestPurchaseMillis())
        assertEquals(Long.MAX_VALUE, emptyList<VaccineBatch>().earliestPurchaseMillis())
    }
}
