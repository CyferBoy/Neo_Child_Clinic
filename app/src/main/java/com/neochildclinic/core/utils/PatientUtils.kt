package com.neochildclinic.core.utils

import com.neochildclinic.core.constants.Constants
import com.neochildclinic.domain.model.Vaccination
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

internal fun Date.toLocalDate(): LocalDate =
    toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

internal fun Calendar.toLocalDate(): LocalDate =
    toInstant().atZone(timeZone.toZoneId()).toLocalDate()

internal fun Calendar.startOfDay(): Calendar {
    val zone = timeZone.toZoneId()
    val zdt = toInstant().atZone(zone).toLocalDate().atStartOfDay(zone)
    return Calendar.getInstance(timeZone).apply { timeInMillis = zdt.toInstant().toEpochMilli() }
}

internal fun Calendar.endOfDay(): Calendar {
    val zone = timeZone.toZoneId()
    val zdt = toInstant().atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).minusNanos(1)
    return Calendar.getInstance(timeZone).apply { timeInMillis = zdt.toInstant().toEpochMilli() }
}

object PatientUtils {

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
     * Calculates the next requested age milestone within the supplied calendar window.
     * Each patient receives only their earliest upcoming milestone.
     */
    fun getNextAgeMilestone(
        dob: String,
        fromDate: Calendar = Calendar.getInstance(),
        windowEnd: Calendar = (Calendar.getInstance()).apply { add(Calendar.MONTH, 2) }
    ): AgeMilestone? {
        val birthDate = parseDate(dob) ?: return null
        val birth = birthDate.toLocalDate()
        val start = fromDate.toLocalDate()
        val end = windowEnd.toLocalDate()
        if (birth.isAfter(start)) return null

        val definitions = listOf(
            "6 Weeks" to { d: LocalDate -> d.plusDays(42) },
            "10 Weeks" to { d: LocalDate -> d.plusDays(70) },
            "14 Weeks" to { d: LocalDate -> d.plusDays(98) },
            "6 Months" to { d: LocalDate -> d.plusMonths(6) },
            "7 Months" to { d: LocalDate -> d.plusMonths(7) },
            "9 Months" to { d: LocalDate -> d.plusMonths(9) },
            "12 Months" to { d: LocalDate -> d.plusMonths(12) },
            "13 Months" to { d: LocalDate -> d.plusMonths(13) },
            "15 Months" to { d: LocalDate -> d.plusMonths(15) },
            "16–17 Months" to { d: LocalDate -> d.plusMonths(16) },
            "16–17 Months" to { d: LocalDate -> d.plusMonths(17) },
            "18 Months" to { d: LocalDate -> d.plusMonths(18) }
        )

        return definitions.mapNotNull { (label, calculator) ->
            val date = calculator(birth)
            if (date.isAfter(start) && !date.isAfter(end)) {
                AgeMilestone(
                    label,
                    Calendar.getInstance().apply {
                        timeInMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                )
            } else {
                null
            }
        }.minByOrNull { it.date.timeInMillis }
    }

    data class AgeMilestone(val label: String, val date: Calendar)

    /**
     * Whether a patient has already reached (or passed) a given age in whole months, as of
     * onDate. Used for the "Older" milestone bucket - patients past the last defined
     * milestone (18 Months) who getNextAgeMilestone() correctly returns null for, since it
     * only looks ahead within its 2-month window.
     */
    fun isOlderThanMonths(dob: String, months: Int, onDate: Calendar = Calendar.getInstance()): Boolean {
        val birthDate = parseDate(dob) ?: return false
        val birth = birthDate.toLocalDate()
        val threshold = birth.plusMonths(months.toLong())
        val today = onDate.toLocalDate()
        return !threshold.isAfter(today)
    }

    /**
     * Returns "<years> year(s) <months> month(s)" (e.g. "1 year 0 months", "0 years 3
     * months") for the Patient Details screen's DOB/age display. Reuses the same
     * calendar-based day-borrow diff already used by calculateAgeLabel()
     * (and the shared parseDate() cache), but always renders both
     * the year and month component - rather than omitting a zero part or switching to
     * a weeks-based label for infants - to match that screen's fixed display format.
     * Returns null for an unparseable or future dob, same as the other age helpers.
     */
    fun formatAgeYearsMonths(dob: String, onDate: Calendar = Calendar.getInstance()): String? {
        val birthDate = parseDate(dob) ?: return null
        val birth = birthDate.toLocalDate()
        val today = onDate.toLocalDate()
        if (birth.isAfter(today)) return null

        var years = today.year - birth.year
        var months = today.monthValue - birth.monthValue
        if (today.dayOfMonth < birth.dayOfMonth) {
            months--
        }
        if (months < 0) {
            years--
            months += 12
        }
        if (years < 0) return null

        val yearLabel = if (years == 1) "year" else "years"
        val monthLabel = if (months == 1) "month" else "months"
        return "$years $yearLabel $months $monthLabel"
    }

    /**
     * Returns a user-friendly age string (e.g., "5 Years", "2 Months", "3 Weeks").
     */
    fun calculateAgeLabel(dob: String): String? {
        try {
            val birthDate = parseDate(dob) ?: return null
            val today = LocalDate.now()
            val birth = birthDate.toLocalDate()

            var years = today.year - birth.year
            var months = today.monthValue - birth.monthValue

            if (today.dayOfMonth < birth.dayOfMonth) {
                months--
            }

            if (months < 0) {
                years--
                months += 12
            }

            if (years < 0) return null

            return when {
                years > 0 -> {
                    if (months > 0) "$years years $months months"
                    else "$years years"
                }
                months > 0 -> "$months months"
                else -> {
                    val diffDays = ChronoUnit.DAYS.between(birth, today).toInt()
                    val weeks = diffDays / 7
                    if (weeks <= 1) "1 week" else "$weeks weeks"
                }
            }
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Calculates age and returns the value and the unit (Years/Months/Weeks).
     * Used for pre-filling the Add/Edit Patient screen.
     */
    fun calculateDetailedAge(dob: String): Pair<Int, String> {
        try {
            val birthDate = parseDate(dob) ?: return 0 to "Years"
            val today = LocalDate.now()
            val birth = birthDate.toLocalDate()

            val diffDays = ChronoUnit.DAYS.between(birth, today).toInt()

            if (diffDays < 30) {
                val weeks = diffDays / 7
                return if (weeks > 0) weeks to "Weeks" else 0 to "Weeks"
            }

            val years = today.year - birth.year
            val months = today.monthValue - birth.monthValue
            val totalMonths = (years * 12) + months

            return if (totalMonths < 12) {
                totalMonths to "Months"
            } else {
                var ageYears = years
                if (today.dayOfYear < birth.dayOfYear) {
                    ageYears--
                }
                ageYears to "Years"
            }
        } catch (_: Exception) {
            return 0 to "Years"
        }
    }

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
        return DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(date.toInstant())
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
        val formatted = DateTimeFormatter.ofPattern("MMM d, yyyy hh:mm:ss a", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Kolkata"))
            .format(date.toInstant())
        return "$formatted IST"
    }

    /**
     * Formats a Date object to the standard app display format.
     */
    fun formatDate(date: Date): String {
        return DateTimeFormatter.ofPattern(Constants.DATE_FORMAT, Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
            .format(date.toInstant())
    }

    /**
     * Formats a timestamp to date and time.
     */
    fun formatDateTime(date: Date): String {
        return DateTimeFormatter.ofPattern("${Constants.DATE_FORMAT}, hh:mm:ss a", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
            .format(date.toInstant())
    }

    /**
     * Standardizes any date string to the current app format (e.g. 9 May 2026).
     */
    fun formatDateForDisplay(dateStr: String): String {
        val date = parseDate(dateStr) ?: return dateStr
        return DateTimeFormatter.ofPattern(Constants.DATE_FORMAT, Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
            .format(date.toInstant())
    }

    /**
     * Returns current time in ISO 8601 format.
     */
    fun getCurrentIsoTimestamp(): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
    }

    /**
     * Returns ISO 8601 timestamp for some minutes ago.
     */
    fun getIsoTimestampMinutesAgo(minutes: Int): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
            .format(Instant.now().minus(minutes.toLong(), ChronoUnit.MINUTES))
    }

    /**
     * Safely converts an ISO string or legacy millis string to Long.
     */
    fun isoToLong(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            // Try ISO first
            parseDate(dateStr)?.time ?: dateStr.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            dateStr.toLongOrNull() ?: 0L
        }
    }

    /**
     * Removes parentheses from vaccine names if they exist (e.g., "Hepatitis B (HepB)" -> "HepB").
     */
    fun cleanVaccineName(name: String): String {
        return if (name.contains("(") && name.endsWith(")")) {
            name.substringAfter("(").substringBeforeLast(")").trim()
        } else {
            name
        }
    }

    /**
     * Unified Logic: Filters pending vaccinations based on a string filter (e.g., "Overdue", "Today").
     */
    fun filterVaccinationsByPeriod(
        pendingVaccinations: List<Vaccination>,
        filter: String,
    ): List<Vaccination> {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
        val weekEnd = weekStart.plusDays(6)
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())

        return pendingVaccinations.filter { v ->
            val date = parseDate(v.nextDueDate)?.toLocalDate()
            if (date == null) {
                filter == "All"
            } else {
                when (filter) {
                    "Overdue" -> date.isBefore(today)
                    "Today" -> date == today
                    "Tomorrow" -> date == tomorrow
                    "This Week" -> !date.isBefore(weekStart) && !date.isAfter(weekEnd)
                    "Month" -> !date.isBefore(monthStart) && !date.isAfter(monthEnd)
                    "Upcoming" -> date.isAfter(weekEnd)
                    "All" -> true
                    else -> true
                }
            }
        }.sortedBy { DateClassifier.getSortWeight(it.nextDueDate) }
    }
}
