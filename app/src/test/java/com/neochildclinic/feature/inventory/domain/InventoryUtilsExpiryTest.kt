package com.neochildclinic.feature.inventory.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Expiry decides which batches count as usable stock, and a vaccination may only deduct from
 * an unexpired batch. "Today" must mean today IN THE CLINIC (Asia/Kolkata): the old code
 * compared an IST expiry date against LocalDate.now() in the DEVICE timezone, so a device east
 * of IST ran a day ahead and treated a batch expiring today as already expired.
 *
 * The reference date is injectable (same pattern as DateClassifier.todayStart) so these
 * assertions are deterministic instead of depending on the wall clock.
 */
class InventoryUtilsExpiryTest {

    private val expiry = "16 Oct 2030"
    private val dayBefore = LocalDate.of(2030, 10, 15)
    private val expiryDay = LocalDate.of(2030, 10, 16)
    private val dayAfter = LocalDate.of(2030, 10, 17)

    @Test
    fun `the expiry day itself is still valid`() {
        assertFalse(InventoryUtils.isExpired(expiry, today = expiryDay))
    }

    @Test
    fun `the day after expiry is expired`() {
        assertTrue(InventoryUtils.isExpired(expiry, today = dayAfter))
    }

    @Test
    fun `the day before expiry is valid`() {
        assertFalse(InventoryUtils.isExpired(expiry, today = dayBefore))
    }

    @Test
    fun `a batch that expired yesterday is expired`() {
        assertTrue(InventoryUtils.isExpired(expiry, today = dayAfter))
        assertFalse(InventoryUtils.isExpired(expiry, today = expiryDay))
    }

    @Test
    fun `expiring today is reported exactly on the expiry day`() {
        assertTrue(InventoryUtils.isExpiringToday(expiry, today = expiryDay))
        assertFalse(InventoryUtils.isExpiringToday(expiry, today = dayAfter))
        assertFalse(InventoryUtils.isExpiringToday(expiry, today = dayBefore))
    }

    @Test
    fun `near expiry covers the window before expiry but not after`() {
        assertTrue(InventoryUtils.isNearExpiry(expiry, thresholdDays = 30, today = dayAfter.minusDays(10)))
        assertFalse(InventoryUtils.isNearExpiry(expiry, thresholdDays = 30, today = dayAfter))
    }

    @Test
    fun `an unparseable expiry date is never treated as expired`() {
        assertFalse(InventoryUtils.isExpired("not-a-date", today = dayAfter))
        assertFalse(InventoryUtils.isExpired("", today = dayAfter))
    }

    @Test
    fun `the default reference date is today in the clinic timezone`() {
        // The default must be the clinic's today, not the device's. A device east of IST runs a
        // day ahead, so comparing an IST expiry date against the device's today treated a batch
        // expiring *today* as already expired - hiding usable doses and blocking a same-day
        // vaccination. (Wall-clock based, so it can only assert the "today" half of the
        // boundary; the "yesterday" half is pinned by the injected-date cases above.)
        val clinicToday = LocalDate.now(ZoneId.of("Asia/Kolkata"))
        val todayString = clinicToday.toString()
        assertFalse("a batch expiring today in the clinic is still usable", InventoryUtils.isExpired(todayString))
        assertTrue(InventoryUtils.isExpiringToday(todayString))
    }

    @Test
    fun `expiry as of a given date is judged against that date not today`() {
        assertFalse(InventoryUtils.isExpiredAsOf(expiry, dayBefore.toString()))
        assertFalse(InventoryUtils.isExpiredAsOf(expiry, expiryDay.toString()))
        assertTrue(InventoryUtils.isExpiredAsOf(expiry, dayAfter.toString()))
    }
}