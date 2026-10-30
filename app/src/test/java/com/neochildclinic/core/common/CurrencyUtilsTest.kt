package com.neochildclinic.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyUtilsTest {

    @Test
    fun testSubLakhAmounts() {
        assertEquals("₹0", CurrencyUtils.formatInr(0))
        assertEquals("₹999", CurrencyUtils.formatInr(999))
        assertEquals("₹22,000", CurrencyUtils.formatInr(22000))
        assertEquals("₹86,300", CurrencyUtils.formatInr(86300))
        assertEquals("₹99,999", CurrencyUtils.formatInr(99999))
    }

    @Test
    fun testLakhRange() {
        assertEquals("₹1 L", CurrencyUtils.formatInr(100_000))
        assertEquals("₹1.25 L", CurrencyUtils.formatInr(125_000))
        assertEquals("₹1.5 L", CurrencyUtils.formatInr(150_000))
        assertEquals("₹10 L", CurrencyUtils.formatInr(1_000_000))
        assertEquals("₹99.9 L", CurrencyUtils.formatInr(9_990_000))
    }

    @Test
    fun testCroreRange() {
        assertEquals("₹1 Cr", CurrencyUtils.formatInr(10_000_000))
        assertEquals("₹1.5 Cr", CurrencyUtils.formatInr(15_000_000))
        assertEquals("₹2.5 Cr", CurrencyUtils.formatInr(25_000_000))
        assertEquals("₹10 Cr", CurrencyUtils.formatInr(100_000_000))
    }

    @Test
    fun testNegativeAmounts() {
        assertEquals("−₹5,000", CurrencyUtils.formatInr(-5000))
        assertEquals("−₹1.5 L", CurrencyUtils.formatInr(-150_000))
        assertEquals("−₹1 Cr", CurrencyUtils.formatInr(-10_000_000))
    }

    @Test
    fun testNonFiniteValues() {
        assertEquals("—", CurrencyUtils.formatInr(Double.NaN))
        assertEquals("—", CurrencyUtils.formatInr(Double.POSITIVE_INFINITY))
        assertEquals("—", CurrencyUtils.formatInr(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun testTypeOverloads() {
        assertEquals("₹999", CurrencyUtils.formatInr(999))
        assertEquals("₹1 L", CurrencyUtils.formatInr(100_000L))
        assertEquals("₹1.5 Cr", CurrencyUtils.formatInr(15_000_000.0))
    }
}
