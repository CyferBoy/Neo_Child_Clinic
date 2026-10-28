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

    @Test
    fun testDateOnlyParseUsesClinicDateInIst() {
        // Finance date bucketing must not follow the device zone: a date-only visit date
        // parsed on a UTC device must still be the same calendar day in IST.
        assertEquals("2026-05-09", PatientUtils.parseDate("2026-05-09")!!.toLocalDate().toString())
    }

    // Postgres renders text timestamps as "2026-08-22 03:48:08.105121+00". Instant.parse
    // rejects that form, and the old fallback parsed it as device/IST wall time, making a
    // remote row look 5.5 hours older and letting a stale local edit win (SQL-030).
    @Test
    fun testPostgresTextTimestampWithOffsetIsParsedAsUtc() {
        assertEquals(
            Instant.parse("2026-08-22T03:48:08.105121Z").toEpochMilli(),
            PatientUtils.isoToLong("2026-08-22 03:48:08.105121+00")
        )
    }
}
