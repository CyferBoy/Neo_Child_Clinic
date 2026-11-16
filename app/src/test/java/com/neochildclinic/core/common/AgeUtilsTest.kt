package com.neochildclinic.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AgeUtilsTest {

    private val istZone = ZoneId.of(IST_ZONE_ID)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun dobForDaysAgo(days: Long): String {
        val today = LocalDate.now(istZone)
        return today.minusDays(days).format(dateFormatter)
    }

    @Test
    fun `calculateAgeLabel formats zero days accurately for newborns`() {
        val dob = dobForDaysAgo(0)
        assertEquals("0 days", AgeUtils.calculateAgeLabel(dob))
    }

    @Test
    fun `calculateAgeLabel formats one day accurately for newborns`() {
        val dob = dobForDaysAgo(1)
        assertEquals("1 day", AgeUtils.calculateAgeLabel(dob))
    }

    @Test
    fun `calculateAgeLabel formats six days accurately for newborns`() {
        val dob = dobForDaysAgo(6)
        assertEquals("6 days", AgeUtils.calculateAgeLabel(dob))
    }

    @Test
    fun `calculateAgeLabel formats seven days as one week`() {
        val dob = dobForDaysAgo(7)
        assertEquals("1 week", AgeUtils.calculateAgeLabel(dob))
    }

    @Test
    fun `calculateAgeLabel formats fourteen days as two weeks`() {
        val dob = dobForDaysAgo(14)
        assertEquals("2 weeks", AgeUtils.calculateAgeLabel(dob))
    }

    @Test
    fun `calculateAgeLabel returns null for future dates`() {
        val today = LocalDate.now(istZone)
        val futureDob = today.plusDays(2).format(dateFormatter)
        assertNull(AgeUtils.calculateAgeLabel(futureDob))
    }
}
