package com.neochildclinic.core.common

import java.util.Calendar
import java.util.Date

/**
 * Deprecated shallow facade over [AgeUtils], [DateUtils], and [VaccineUtils].
 * Kept strictly for backward compatibility with external references. Callers should access the specific
 * utilities directly.
 */
@Deprecated("Shallow facade. Call DateUtils, AgeUtils, or VaccineUtils directly.")
object PatientUtils {

    typealias AgeMilestone = AgeUtils.AgeMilestone

    fun getNextAgeMilestone(
        dob: String,
        fromDate: Calendar = Calendar.getInstance(),
        windowEnd: Calendar = (Calendar.getInstance()).apply { add(Calendar.MONTH, 2) }
    ): AgeMilestone? = AgeUtils.getNextAgeMilestone(dob, fromDate, windowEnd)

    fun isOlderThanMonths(dob: String, months: Int, onDate: Calendar = Calendar.getInstance()): Boolean =
        AgeUtils.isOlderThanMonths(dob, months, onDate)

    fun formatAgeYearsMonths(dob: String, onDate: Calendar = Calendar.getInstance()): String? =
        AgeUtils.formatAgeYearsMonths(dob, onDate)

    fun calculateAgeLabel(dob: String): String? = AgeUtils.calculateAgeLabel(dob)

    fun calculateDetailedAge(dob: String): Pair<Int, String> = AgeUtils.calculateDetailedAge(dob)

    fun parseDate(dateStr: String): Date? = DateUtils.parseDate(dateStr)

    fun formatDateTimeForDisplay(isoString: String): String = DateUtils.formatDateTimeForDisplay(isoString)

    fun formatDateTimeIST(isoString: String): String = DateUtils.formatDateTimeIST(isoString)

    fun formatDate(date: Date): String = DateUtils.formatDate(date)

    fun formatDateTime(date: Date): String = DateUtils.formatDateTime(date)

    fun formatDateForDisplay(dateStr: String): String = DateUtils.formatDateForDisplay(dateStr)

    fun todayDateString(): String = DateUtils.todayDateString()

    fun formatBytes(bytes: Long): String = DateUtils.formatBytes(bytes)

    fun getCurrentIsoTimestamp(): String = DateUtils.getCurrentIsoTimestamp()

    fun getIsoTimestampMinutesAgo(minutes: Int): String = DateUtils.getIsoTimestampMinutesAgo(minutes)

    fun isoToLong(dateStr: String?): Long = DateUtils.isoToLong(dateStr)

    fun cleanVaccineName(name: String): String = VaccineUtils.cleanVaccineName(name)
}