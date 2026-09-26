package com.neochildclinic.core.utils

import com.neochildclinic.domain.model.Vaccination
import java.time.ZoneId
import java.util.Calendar
import java.util.Date

internal fun Date.toLocalDate(): java.time.LocalDate =
    toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

internal fun Calendar.toLocalDate(): java.time.LocalDate =
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

/**
 * Facade over [AgeUtils] and [DateUtils], kept so existing call sites (`PatientUtils.x`)
 * compile unchanged after the oversized object was decomposed.
 */
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

    fun formatBytes(bytes: Long): String = DateUtils.formatBytes(bytes)

    fun getCurrentIsoTimestamp(): String = DateUtils.getCurrentIsoTimestamp()

    fun getIsoTimestampMinutesAgo(minutes: Int): String = DateUtils.getIsoTimestampMinutesAgo(minutes)

    fun isoToLong(dateStr: String?): Long = DateUtils.isoToLong(dateStr)

    fun cleanVaccineName(name: String): String = DateUtils.cleanVaccineName(name)

    fun filterVaccinationsByPeriod(
        pendingVaccinations: List<Vaccination>,
        filter: String,
    ): List<Vaccination> = DateUtils.filterVaccinationsByPeriod(pendingVaccinations, filter)
}