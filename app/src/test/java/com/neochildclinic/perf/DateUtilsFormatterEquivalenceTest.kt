package com.neochildclinic.perf

import com.neochildclinic.core.common.Constants
import com.neochildclinic.core.common.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * PE-02 guard: DateUtils hoisted its DateTimeFormatters into immutable vals. Every affected
 * output is compared against a freshly built formatter (the old per-call behaviour), so a
 * changed pattern, locale or IST zone is caught. Covers ISO date-only, ISO timestamp,
 * and display-format inputs.
 */
class DateUtilsFormatterEquivalenceTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    @Test
    fun hoistedFormattersMatchFreshlyBuiltOnes() {
        val inputs = listOf(
            "2026-05-09",                     // ISO date-only
            "2026-08-05T23:02:37.424+05:30",  // ISO timestamp with offset
            "9 May 2026",                     // Constants.DATE_FORMAT display input
            "22/08/2026"                      // legacy zero-padded display input
        )

        // Reference formatters = exactly what the old code built on every call.
        val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        val shortDateWithYear = DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH)
        val displayDateTime = DateTimeFormatter
            .ofPattern("MMM d, yyyy HH:mm:ss", Locale.getDefault()).withZone(ist)
        val istDateTime = DateTimeFormatter
            .ofPattern("MMM d, yyyy hh:mm:ss a", Locale.ENGLISH).withZone(ist)
        val appDate = DateTimeFormatter
            .ofPattern(Constants.DATE_FORMAT, Locale.ENGLISH).withZone(ist)
        val appDateTime = DateTimeFormatter
            .ofPattern("${Constants.DATE_FORMAT}, hh:mm:ss a", Locale.ENGLISH).withZone(ist)

        for (input in inputs) {
            val parsed = DateUtils.parseDate(input) ?: error("test input must parse: $input")
            val instant = parsed.toInstant()
            val inIst = instant.atZone(ist)
            val currentYear = Instant.now().atZone(ist).year

            val optionalYearRef =
                if (inIst.year == currentYear) shortDate else shortDateWithYear
            assertEquals("optional-year / $input",
                inIst.format(optionalYearRef), DateUtils.formatDateWithOptionalYear(input))
            assertEquals("display datetime / $input",
                displayDateTime.format(instant), DateUtils.formatDateTimeForDisplay(input))
            assertEquals("IST datetime / $input",
                "${istDateTime.format(instant)} IST", DateUtils.formatDateTimeIST(input))
            assertEquals("date / $input",
                appDate.format(instant), DateUtils.formatDate(parsed))
            assertEquals("datetime / $input",
                appDateTime.format(instant), DateUtils.formatDateTime(parsed))
            assertEquals("date for display / $input",
                appDate.format(instant), DateUtils.formatDateForDisplay(input))
        }
    }
}
