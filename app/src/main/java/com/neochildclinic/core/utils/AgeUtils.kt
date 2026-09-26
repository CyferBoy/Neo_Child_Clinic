package com.neochildclinic.core.utils

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar

/**
 * Age-related helpers used across Dashboard/Statistics: milestone lookups,
 * "is older than" checks, and the various humanized age labels.
 */
object AgeUtils {

    data class AgeMilestone(val label: String, val date: Calendar)

    /**
     * Calculates the next requested age milestone within the supplied calendar window.
     * Each patient receives only their earliest upcoming milestone.
     */
    fun getNextAgeMilestone(
        dob: String,
        fromDate: Calendar = Calendar.getInstance(),
        windowEnd: Calendar = (Calendar.getInstance()).apply { add(Calendar.MONTH, 2) }
    ): AgeMilestone? {
        val birthDate = PatientUtils.parseDate(dob) ?: return null
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

    /**
     * Whether a patient has already reached (or passed) a given age in whole months, as of
     * onDate. Used for the "Older" milestone bucket - patients past the last defined
     * milestone (18 Months) who getNextAgeMilestone() correctly returns null for, since it
     * only looks ahead within its 2-month window.
     */
    fun isOlderThanMonths(dob: String, months: Int, onDate: Calendar = Calendar.getInstance()): Boolean {
        val birthDate = PatientUtils.parseDate(dob) ?: return false
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
        val birthDate = PatientUtils.parseDate(dob) ?: return null
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
            val birthDate = PatientUtils.parseDate(dob) ?: return null
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
            val birthDate = PatientUtils.parseDate(dob) ?: return 0 to "Years"
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
}