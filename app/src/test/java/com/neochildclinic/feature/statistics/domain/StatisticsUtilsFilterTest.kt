package com.neochildclinic.feature.statistics.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsUtilsFilterTest {

    @Test
    fun `Overall filter matches every date and displays as Overall`() {
        assertEquals("Overall", StatisticsUtils.displayFilterMode("Overall"))
        assertTrue(StatisticsUtils.isDateInFilter("2026-09-25", "Overall"))
        assertTrue(StatisticsUtils.isDateInFilter("2024-01-01", "Overall"))
    }

    @Test
    fun `mangled year label is rejected instead of silently matching nothing useful`() {
        // Regression: "FY veral" was produced when "Overall" went through takeLast(5).
        assertFalse(StatisticsUtils.isDateInFilter("2026-09-25", "FY veral"))
        assertEquals("FY veral", StatisticsUtils.displayFilterMode("FY veral"))
    }

    @Test
    fun `canonical short FY filter works and formats for display`() {
        assertTrue(StatisticsUtils.isDateInFilter("2026-09-25", "26-27"))
        assertFalse(StatisticsUtils.isDateInFilter("2026-09-25", "25-26"))
        assertEquals("2026-27", StatisticsUtils.displayFilterMode("26-27"))
        assertEquals(Triple("25-26", 0, -1), StatisticsUtils.getPreviousPeriodFilter("26-27", 0, -1))
    }
}