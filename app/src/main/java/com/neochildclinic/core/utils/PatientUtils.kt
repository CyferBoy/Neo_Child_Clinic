package com.neochildclinic.core.utils

import com.neochildclinic.core.constants.Constants
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.ReminderStatus
import java.text.SimpleDateFormat
import java.util.*

object PatientUtils {
    
    /**
     * Returns a user-friendly age string (e.g., "5 Years", "2 Months", "3 Weeks").
     */
    fun calculateAgeLabel(dob: String): String? {
        try {
            val birthDate = parseDate(dob) ?: return null
            val today = Calendar.getInstance()
            val birth = Calendar.getInstance()
            birth.time = birthDate

            var years = today[Calendar.YEAR] - birth[Calendar.YEAR]
            var months = today[Calendar.MONTH] - birth[Calendar.MONTH]
            
            if (today[Calendar.DAY_OF_MONTH] < birth[Calendar.DAY_OF_MONTH]) {
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
                    val diffMs = today.timeInMillis - birth.timeInMillis
                    val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()
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
            val today = Calendar.getInstance()
            val birth = Calendar.getInstance()
            birth.time = birthDate

            val diffMs = today.timeInMillis - birth.timeInMillis
            val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()

            if (diffDays < 30) {
                val weeks = diffDays / 7
                return if (weeks > 0) weeks to "Weeks" else 0 to "Weeks"
            }
            
            val years = today[Calendar.YEAR] - birth[Calendar.YEAR]
            val months = today[Calendar.MONTH] - birth[Calendar.MONTH]
            val totalMonths = (years * 12) + months
            
            return if (totalMonths < 12) {
                totalMonths to "Months"
            } else {
                var ageYears = years
                if (today[Calendar.DAY_OF_YEAR] < birth[Calendar.DAY_OF_YEAR]) {
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
        // Order matters: SimpleDateFormat.parse() happily matches just a leading prefix
        // of the string and silently ignores unparsed trailing text (even with
        // isLenient = false). A bare date pattern like "yyyy-MM-dd" will therefore
        // "successfully" match a full timestamp string (e.g. Postgres's
        // "2026-08-22 03:48:08.105121+00"), parsing only the date and silently
        // dropping the time - which is exactly why timestamps were displaying as
        // the right date at 00:00. Every datetime pattern must be tried before any
        // date-only pattern so the more complete match wins first.
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
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.ENGLISH)
                sdf.isLenient = false
                return sdf.parse(dateStr)
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Formats a date or ISO string for user-friendly display.
     */
    fun formatDateTimeForDisplay(isoString: String): String {
        if (isoString.isBlank()) return "N/A"
        val date = parseDate(isoString) ?: return isoString
        return SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault()).format(date)
    }

    /**
     * Formats a Date object to the standard app display format.
     */
    fun formatDate(date: Date): String {
        return SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(date)
    }

    /**
     * Formats a timestamp to date and time.
     */
    fun formatDateTime(date: Date): String {
        return SimpleDateFormat("${Constants.DATE_FORMAT}, hh:mm:ss a", Locale.ENGLISH).format(date)
    }

    /**
     * Standardizes any date string to the current app format (e.g. 9 May 2026).
     */
    fun formatDateForDisplay(dateStr: String): String {
        val date = parseDate(dateStr) ?: return dateStr
        return SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(date)
    }

    /**
     * Returns current time in ISO 8601 format.
     */
    fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ENGLISH)
        return sdf.format(Date())
    }

    /**
     * Returns ISO 8601 timestamp for some minutes ago.
     */
    fun getIsoTimestampMinutesAgo(minutes: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ENGLISH)
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, -minutes)
        return sdf.format(cal.time)
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
     * Legacy Logic: A vaccination is "actually pending" if:
     * 1. isDone is false
     * 2. There is NO other vaccination record for the same patient that was given AFTER this record's dateGiven.
     *    (If a patient visits and a record is added, it supercedes all previous reminders/pending items).
     */
    fun getPendingVaccinations(allVaccinations: List<Vaccination>): List<Vaccination> {
        return allVaccinations.filter { v ->
            if (v.status != ReminderStatus.ACTIVE) return@filter false
            if (v.nextDueDate.isBlank()) return@filter false
            
            val thisDateGiven = parseDate(v.dateGiven)
            
            // Check if any record for the same patient has a strictly later dateGiven
            val hasNewerRecord = allVaccinations.any { other ->
                if (other.id == v.id || other.patientId != v.patientId) return@any false
                val otherDateGiven = parseDate(other.dateGiven)
                otherDateGiven != null && thisDateGiven != null && otherDateGiven.after(thisDateGiven)
            }
            
            !hasNewerRecord
        }
    }

    /**
     * Unified Logic: Filters pending vaccinations based on a string filter (e.g., "Overdue", "Today").
     */
    fun filterVaccinationsByPeriod(
        pendingVaccinations: List<Vaccination>,
        filter: String,
    ): List<Vaccination> {
        val now = Calendar.getInstance()

        fun startOfDay(cal: Calendar): Calendar = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        fun endOfDay(cal: Calendar): Calendar = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val todayStart = startOfDay(now)
        val todayEnd = endOfDay(now)

        // Use the device locale's calendar week boundaries.
        val weekStart = startOfDay(now).apply {
            val daysFromWeekStart =
                (get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
            add(Calendar.DAY_OF_YEAR, -daysFromWeekStart)
        }
        val weekEnd = endOfDay(weekStart).apply {
            add(Calendar.DAY_OF_YEAR, 6)
        }

        val monthStart = startOfDay(now).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val monthEnd = endOfDay(now).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }

        return pendingVaccinations.filter { v ->
            val date = parseDate(v.nextDueDate)
            if (date == null) {
                filter == "All"
            } else {
                val dateCal = Calendar.getInstance().apply {
                    time = date
                }
                when (filter) {
                    "Overdue" -> dateCal.before(todayStart)
                    "Today" -> !dateCal.before(todayStart) && !dateCal.after(todayEnd)
                    "Tomorrow" -> {
                        val tomorrowStart = (todayStart.clone() as Calendar).apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                        val tomorrowEnd = endOfDay(tomorrowStart)
                        !dateCal.before(tomorrowStart) && !dateCal.after(tomorrowEnd)
                    }
                    "This Week" -> !dateCal.before(weekStart) && !dateCal.after(weekEnd)
                    "Month" -> !dateCal.before(monthStart) && !dateCal.after(monthEnd)
                    "Upcoming" -> dateCal.after(weekEnd)
                    "All" -> true
                    else -> true
                }
            }
        }.sortedBy { DateClassifier.getSortWeight(it.nextDueDate) }
    }
}
