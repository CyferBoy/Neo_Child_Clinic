package com.neochildclinic.features.statistics

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
        assertEquals("20veral", StatisticsUtils.displayFilterMode("FY veral"))
    }
}