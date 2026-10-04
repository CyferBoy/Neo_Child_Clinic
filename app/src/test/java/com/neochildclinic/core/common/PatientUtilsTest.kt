package com.neochildclinic.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.util.*

class PatientUtilsTest {

    @Test
    fun testParseIsoTimestamp() {
        val isoTimestamp = "2026-08-05T23:02:37.424+05:30"
        val date = PatientUtils.parseDate(isoTimestamp)
        assertNotNull("Date should not be null for ISO timestamp", date)
    }

    @Test
    fun testParseIsoTimestampWithMillis() {
        val isoTimestamp = "2026-08-05T23:02:37.424193+05:30"
        val date = PatientUtils.parseDate(isoTimestamp)
        assertNotNull("Date should not be null for ISO timestamp with microseconds", date)
    }

    // Sync writes updatedAt with Instant.now().toString(), which emits NINE fractional
    // digits whenever nanos are not a multiple of 1000. The sync conflict check compares
    // isoToLong(remote) > isoToLong(local); if the local side loses its time-of-day it
    // collapses to midnight and a stale server row wins, silently reverting the edit.
    @Test
    fun testIsoToLongKeepsTimeOfDayForNanoPrecisionInstant() {
        val instant = Instant.parse("2026-08-05T23:02:37.424193691Z")
        assertEquals(
            "nano-precision Instant string must keep its time-of-day, not truncate to midnight",
            instant.toEpochMilli(),
            PatientUtils.isoToLong(instant.toString())
        )
    }
}
