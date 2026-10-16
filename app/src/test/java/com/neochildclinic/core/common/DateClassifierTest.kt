package com.neochildclinic.core.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Due-date maths the whole Due tab, dashboard counts and the widget depend on.
 * classify() takes an injectable todayStart precisely so it is testable on the JVM.
 */
class DateClassifierTest {

    private val originalZone = TimeZone.getDefault()

    @Before
    fun setUp() = Unit

    @After
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    /** Midnight IST on the given date. */
    private fun istStart(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }

    @Test
    fun `a date before today is overdue by the whole number of days`() {
        val today = istStart(2026, 10, 16)
        val category = DateClassifier.classify("13 Oct 2026", today)
        assertEquals(DateCategory.Overdue(3), category)
    }

    @Test
    fun `today is not overdue on a device in a timezone ahead of the clinic`() {
        // Tokyo is UTC+9: a naive device-time parse of a date-only string lands on the
        // previous day in IST, which used to classify today's dose as overdue and drop it
        // from due-today counts. 2031 keeps this string out of DateUtils' process-wide
        // parse cache, which would otherwise hand back a value parsed by another test.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        val today = istStart(2031, 10, 16)
        assertEquals(DateCategory.Today, DateClassifier.classify("16 Oct 2031", today))
    }

    @Test
    fun `a date-only string keeps its calendar day on a device in a timezone behind the clinic`() {
        // Honolulu is UTC-10: the same naive parse landed on the next day in IST.
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
        assertEquals(
            LocalDate.of(2032, 5, 21),
            PatientUtils.parseDate("21 May 2032")?.toInstant()
                ?.atZone(java.time.ZoneId.of("Asia/Kolkata"))?.toLocalDate()
        )
    }

    @Test
    fun `tomorrow and future are distinguished`() {
        val today = istStart(2026, 10, 16)
        assertEquals(DateCategory.Tomorrow, DateClassifier.classify("17 Oct 2026", today))
        assertTrue(DateClassifier.classify("20 Oct 2026", today) is DateCategory.Future)
    }

    @Test
    fun `an unparseable date is treated as future rather than throwing`() {
        val today = istStart(2026, 10, 16)
        assertEquals(DateCategory.Future("not-a-date"), DateClassifier.classify("not-a-date", today))
    }

    @Test
    fun `overdue items sort after every upcoming item, nearest first`() {
        val today = istStart(2026, 10, 16)
        val sorted = listOf("10 Oct 2026", "18 Oct 2026", "16 Oct 2026", "15 Oct 2026", "20 Oct 2026")
            .sortedWith(DateClassifier.dueDateComparator(today))
        // upcoming ascending, then overdue newest-first
        assertEquals(listOf("16 Oct 2026", "18 Oct 2026", "20 Oct 2026", "15 Oct 2026", "10 Oct 2026"), sorted)
    }

    @Test
    fun `display formatting names today and tomorrow instead of printing the date`() {
        val today = istStart(2026, 10, 16)
        assertEquals("Today", DateClassifier.formatDisplay("16 Oct 2026", today))
        assertEquals("Tomorrow", DateClassifier.formatDisplay("17 Oct 2026", today))
    }

    @Test
    fun `parseDate still honours an explicit offset in an iso timestamp`() {
        // The IST fix must not break timestamps that carry their own UTC offset.
        val parsed = PatientUtils.parseDate("2026-08-22T03:48:08.105121+00:00")
        assertTrue(parsed != null)
        val istDate = parsed!!.toInstant().atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 22), istDate)
    }

    @Test
    fun `legacy date formats keep parsing`() {
        listOf("2026-10-16", "16/10/2026", "16 Oct 2026", "2026-10-16 09:30:00").forEach { raw ->
            val istDate = PatientUtils.parseDate(raw)?.toInstant()
                ?.atZone(java.time.ZoneId.of("Asia/Kolkata"))?.toLocalDate()
            assertEquals("failed to parse $raw", LocalDate.of(2026, 10, 16), istDate)
        }
    }

    @Test
    fun `display formatting of a legacy month name is unaffected by the device locale`() {
        // Guards the Locale.ENGLISH parse path: a device set to a non-English locale must
        // still read "Oct" as October.
        Locale.setDefault(Locale.GERMANY)
        val today = istStart(2026, 10, 16)
        assertEquals(DateCategory.Overdue(1), DateClassifier.classify("15 Oct 2026", today))
    }
}