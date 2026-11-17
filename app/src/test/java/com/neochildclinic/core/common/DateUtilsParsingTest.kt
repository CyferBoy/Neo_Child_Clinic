package com.neochildclinic.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DateUtilsParsingTest {

    @Test
    fun testParseIsoTimestamp() {
        // 23:02:37.424 at +05:30 is 17:32:37.424Z.
        assertEquals(
            1_785_951_157_424L,
            DateUtils.parseDate("2026-08-05T23:02:37.424+05:30")!!.time
        )
    }

    @Test
    fun testIsoToLongKeepsMicrosecondOffsetInstant() {
        // parseDate's SimpleDateFormat list tops out at millisecond precision and falls
        // through to a date-only parse for a 6-digit fraction; isoToLong is the production
        // entry point for ISO timestamps and must keep the exact instant.
        assertEquals(
            1_785_951_157_424L,
            DateUtils.isoToLong("2026-08-05T23:02:37.424193+05:30")
        )
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
            DateUtils.isoToLong(instant.toString())
        )
    }

    @Test
    fun testDateOnlyParseUsesClinicDateInIst() {
        // Finance date bucketing must not follow the device zone: a date-only visit date
        // parsed on a UTC device must still be the same calendar day in IST.
        assertEquals("2026-05-09", DateUtils.parseDate("2026-05-09")!!.toLocalDate().toString())
    }

    // Postgres renders text timestamps as "2026-08-22 03:48:08.105121+00". Instant.parse
    // rejects that form, and the old fallback parsed it as device/IST wall time, making a
    // remote row look 5.5 hours older and letting a stale local edit win (SQL-030).
    @Test
    fun testPostgresTextTimestampWithOffsetIsParsedAsUtc() {
        assertEquals(
            Instant.parse("2026-08-22T03:48:08.105121Z").toEpochMilli(),
            DateUtils.isoToLong("2026-08-22 03:48:08.105121+00")
        )
    }

    // Consolidation guard: the single shared parser must accept every format the removed
    // per-feature helpers accepted and read the calendar date in IST (not the device zone).
    @Test
    fun testParseFlexibleDateCoversAllFormatsAndNormalizesToIso() {
        assertEquals(LocalDate.of(2026, 5, 9), DateUtils.parseFlexibleDate("9 May 2026"))
        assertEquals(LocalDate.of(2026, 8, 22), DateUtils.parseFlexibleDate("2026-08-22"))
        assertEquals(LocalDate.of(2026, 8, 22), DateUtils.parseFlexibleDate("22/08/2026"))
        assertEquals(LocalDate.of(2026, 8, 2), DateUtils.parseFlexibleDate("2/8/2026"))
        assertEquals(
            LocalDate.of(2026, 8, 23),
            DateUtils.parseFlexibleDate("2026-08-22T23:30:00.000+00:00")
        )
        assertEquals("2026-05-09", DateUtils.toIsoDate("9 May 2026"))
        assertEquals("2026-05-09", DateUtils.toIsoDate("2026-05-09"))
        assertNull(DateUtils.parseFlexibleDate(" "))
    }
}
