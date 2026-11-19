package com.neochildclinic.core.common

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Optional
import java.util.TimeZone as JTimeZone
import java.util.TimeZone

// Single source of truth for the clinic's timezone. All calendar-date calculations
// (today, tomorrow, date bucketing, age, due dates) must use this.
internal const val IST_ZONE_ID = "Asia/Kolkata"

internal fun Date.toLocalDate(): java.time.LocalDate =
    toInstant().atZone(ZoneId.of(IST_ZONE_ID)).toLocalDate()

internal fun Calendar.toLocalDate(): java.time.LocalDate =
    toInstant().atZone(ZoneId.of(IST_ZONE_ID)).toLocalDate()

internal fun Calendar.startOfDay(): Calendar {
    val zone = ZoneId.of(IST_ZONE_ID)
    val zdt = toInstant().atZone(zone).toLocalDate().atStartOfDay(zone)
    return Calendar.getInstance(JTimeZone.getTimeZone(IST_ZONE_ID)).apply { timeInMillis = zdt.toInstant().toEpochMilli() }
}

/**
 * Date parsing/formatting helpers shared across the app. parseDate() is the single
 * entry point for turning any legacy date/ISO string into a Date.
 */
object DateUtils {

    val APP_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern(Constants.DATE_FORMAT, Locale.ENGLISH)

    /** Legacy zero-padded display format used by the milestone patient list. */
    val LEGACY_PADDED_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    val FULL_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US)

    val TIME_12H_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    // PE-02: these used to be rebuilt with ofPattern() on every formatting call. All are
    // immutable and thread-safe, so build them once and share (IST zone baked in where the
    // output is an instant that must read as clinic-local time).
    private val SHORT_DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val SHORT_DATE_WITH_YEAR_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH)
    private val DATE_TIME_DISPLAY_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss", Locale.getDefault())
            .withZone(ZoneId.of(IST_ZONE_ID))
    private val DATE_TIME_IST_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy hh:mm:ss a", Locale.ENGLISH)
            .withZone(ZoneId.of(IST_ZONE_ID))
    private val APP_DATE_FORMATTER_IST: DateTimeFormatter =
        APP_DATE_FORMATTER.withZone(ZoneId.of(IST_ZONE_ID))
    private val DATE_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("${Constants.DATE_FORMAT}, hh:mm:ss a", Locale.ENGLISH)
            .withZone(ZoneId.of(IST_ZONE_ID))

    /**
     * Returns today's calendar date in IST formatted with the standard display format (Constants.DATE_FORMAT).
     */
    fun todayDateString(): String =
        LocalDate.now(ZoneId.of(IST_ZONE_ID)).format(APP_DATE_FORMATTER)

    /**
     * Single parser for every date/date-time string form callers exchange. Delegates to
     * [parseDate] (all 8 supported formats plus the display format) and reads the calendar
     * date in IST, so date-only input is never shifted through the device timezone.
     */
    fun parseFlexibleDate(dateStr: String): LocalDate? {
        if (dateStr.isBlank()) return null
        // parseDate anchors SimpleDateFormat to IST; read the date back in the same zone
        // so date-only input is never shifted by the device/JVM default timezone.
        return parseDate(dateStr)?.toInstant()?.atZone(java.time.ZoneId.of(IST_ZONE_ID))?.toLocalDate()
    }

    /** Normalizes any [parseFlexibleDate]-supported string to ISO "yyyy-MM-dd"; unparseable input is returned unchanged. */
    fun toIsoDate(dateStr: String): String =
        parseFlexibleDate(dateStr)?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: dateStr

    /**
     * Formats a date as "15 Jun" when it is in the current IST year, otherwise "15 Jun 25".
     * Unparseable input is returned unchanged. Shared so every year-trimming display makes
     * the same decision using the clinic's (not the device's) current year.
     */
    fun formatDateWithOptionalYear(dateStr: String): String {
        val date = parseDate(dateStr) ?: return dateStr
        val dateInIST = date.toInstant().atZone(ZoneId.of(IST_ZONE_ID))
        val currentYear = Instant.now().atZone(ZoneId.of(IST_ZONE_ID)).year
        return dateInIST.format(
            if (dateInIST.year == currentYear) SHORT_DATE_FORMATTER else SHORT_DATE_WITH_YEAR_FORMATTER
        )
    }

    // parseDate() is called for essentially every date across every Statistics screen
    // (often the same string many times over, e.g. once per filter pass and again for
    // each of the 6 months in the trend chart). Caching results avoids re-parsing the
    // same string repeatedly. ConcurrentHashMap can't store null, so failed parses are
    // cached as Optional.empty(). Capped and cleared wholesale if it ever grows large,
    // since date-string cardinality is normally small (bounded by distinct records) but
    // this keeps memory bounded defensively.
    private val dateParseCache = java.util.concurrent.ConcurrentHashMap<String, Optional<Date>>()
    private const val DATE_PARSE_CACHE_LIMIT = 5000

    /**
     * Tries to parse a date string using multiple common formats.
     */
    fun parseDate(dateStr: String): Date? {
        if (dateStr.isBlank()) return null

        dateParseCache[dateStr]?.let { return it.orElse(null) }

        // Order matters: SimpleDateFormat.parse() happily matches just a leading prefix
        // of the string and silently ignores unparsed trailing text (even with
        // isLenient = false). A bare date pattern like "yyyy-MM-dd" will therefore
        // "successfully" match a full timestamp string (e.g. Postgres's
        // "2026-08-22 03:48:08.105121+00"), parsing only the date and silently
        // dropping the time - which is exactly why timestamps were displaying as
        // the right date at 00:00. Every datetime pattern must be tried before any
        // date-only pattern so the more complete match wins first.
        //
        // NOTE: this used to try each format with sdf.parse(String), which throws and
        // catches a ParseException for every failed attempt. On Android that's a real
        // cost (stack trace capture) and this runs for nearly every date, everywhere in
        // Statistics - it was the single biggest source of the lag. sdf.parse(String,
        // ParsePosition) is the exact same underlying parse logic (same prefix-matching
        // behavior, same precedence semantics) but returns null on failure instead of
        // throwing, so behavior is unchanged and failed attempts are cheap.
        // KEEP this one SimpleDateFormat: DateTimeFormatter is strict about trailing
        // text and cannot reproduce prefix-match parsing for mixed legacy formats.
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            Constants.DATE_FORMAT,
            "d/M/yyyy",
            "dd/MM/yyyy",
            "yyyy-MM-dd"
        )
        var result: Date? = null
        for (format in formats) {
            val sdf = SimpleDateFormat(format, Locale.ENGLISH)
            sdf.isLenient = false
            // Date-only strings carry no offset, so the formatter falls back to the DEVICE
            // timezone. On a device east of IST that lands on the previous calendar day once
            // every consumer converts to IST (Date.toLocalDate()), which classified today's
            // dose as overdue and made a batch expiring "today" look expired. Anchor parsing
            // to the clinic timezone instead - it only affects patterns without an explicit
            // offset; ISO timestamps that carry their own offset still win.
            sdf.timeZone = JTimeZone.getTimeZone(IST_ZONE_ID)
            val pos = java.text.ParsePosition(0)
            val parsed = sdf.parse(dateStr, pos)
            if (parsed != null) {
                result = parsed
                break
            }
        }

        if (dateParseCache.size >= DATE_PARSE_CACHE_LIMIT) dateParseCache.clear()
        dateParseCache[dateStr] = Optional.ofNullable(result)
        return result
    }

    /**
     * Formats a date or ISO string for user-friendly display.
     */
    fun formatDateTimeForDisplay(isoString: String): String {
        if (isoString.isBlank()) return "N/A"
        val date = parseDate(isoString) ?: return isoString
        return DATE_TIME_DISPLAY_FORMATTER.format(date.toInstant())
    }

    /**
     * Formats a date/ISO string as clinic-local (Indian Standard Time) date and time,
     * regardless of the device's own timezone setting. Staff account timestamps
     * (created/updated/last login) should always read in IST since that's what the
     * clinic operates on - a staff member whose phone is set to a different timezone
     * would otherwise see a shifted, confusing time for these fields.
     */
    fun formatDateTimeIST(isoString: String): String {
        if (isoString.isBlank()) return "N/A"
        val date = parseDate(isoString) ?: return isoString
        val formatted = DATE_TIME_IST_FORMATTER.format(date.toInstant())
        return "$formatted IST"
    }

    fun formatDate(date: Date): String {
        return APP_DATE_FORMATTER_IST.format(date.toInstant())
    }

    fun formatDateTime(date: Date): String {
        return DATE_TIME_FORMATTER.format(date.toInstant())
    }

    fun formatDateForDisplay(dateStr: String): String {
        val date = parseDate(dateStr) ?: return dateStr
        return APP_DATE_FORMATTER_IST.format(date.toInstant())
    }

    /**
     * Formats a byte count into a human-readable binary unit string (B, KB, MB, GB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.1f GB".format(mb / 1024.0)
    }

    // Absolute instants, stored/transported as UTC. The clinic's calendar date is
    // derived by converting to IST at read time (StatisticsDateUtils.parseToISTLocalDate),
    // not by baking a device-local offset into the value - see IST_ZONE_ID.
    fun getCurrentIsoTimestamp(): String = Instant.now().toString()

    fun getIsoTimestampMinutesAgo(minutes: Int): String =
        Instant.now().minus(minutes.toLong(), ChronoUnit.MINUTES).toString()

    /**
     * Safely converts an ISO string or legacy millis string to Long.
     */
    fun isoToLong(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            // Instant.parse is strict and exact, and covers every precision
            // Instant.toString() emits (0/3/6/9 fractional digits). parseDate's
            // SimpleDateFormat list tops out at 6 digits, so a nano-precision value
            // fell through to a date-only pattern and lost its time-of-day - which made
            // the sync conflict check read a stale server row as newer and silently
            // revert local edits. Anything non-ISO still falls through unchanged.
            runCatching { Instant.parse(dateStr).toEpochMilli() }.getOrNull()
                // Postgres text timestamps: "2026-08-22 03:48:08.105121+00" - a space and a
                // two-digit offset, both of which Instant.parse rejects. Parse the offset
                // instead of falling through to parseDate, which reads the wall time as IST
                // and makes the row look 5.5 hours older than it is (SQL-030).
                ?: runCatching {
                    java.time.OffsetDateTime.parse(dateStr.replace(' ', 'T'))
                        .toInstant().toEpochMilli()
                }.getOrNull()
                ?: parseDate(dateStr)?.time ?: dateStr.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            dateStr.toLongOrNull() ?: 0L
        }
    }
}