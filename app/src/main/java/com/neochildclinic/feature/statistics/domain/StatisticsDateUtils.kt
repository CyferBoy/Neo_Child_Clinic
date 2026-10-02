package com.neochildclinic.feature.statistics.domain

import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.common.toLocalDate
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Centralized IST date utilities for Statistics.
 * All Statistics date bucketing and "today" calculations must go through here.
 */
object StatisticsDateUtils {

    val IST: ZoneId = ZoneId.of("Asia/Kolkata")


    /**
     * Get today's date in IST as a Calendar with time zeroed.
     */
    fun todayIST(): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
    }

    /**
     * Get today's date string in IST as "d MMM yyyy" (app display format).
     */
    fun todayISTString(): String {
        return DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            .withZone(IST)
            .format(Instant.now())
    }

    /**
     * Get the current IST year and month.
     */
    fun currentISTYearMonth(): Pair<Int, Int> {
        val cal = todayIST()
        return cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
    }

    /**
     * The current IST month, as a YearMonth. Use this rather than a hand-rolled
     * `year * 12 + month` integer key: YearMonth already has correct equals/hashCode
     * for use as a map key, and carries the 1-based monthValue (no off-by-one decode
     * step to get wrong).
     */
    fun currentISTYearMonthObj(): YearMonth = YearMonth.now(IST)

    /**
     * The [n] calendar months ending with the current IST month, oldest first.
     */
    fun lastNMonths(n: Int): List<YearMonth> =
        (0 until n).reversed().map { currentISTYearMonthObj().minusMonths(it.toLong()) }

    /**
     * Convert a date-only string (no time component) to a LocalDate.
     * Handles "d MMM yyyy", "yyyy-MM-dd", "d/M/yyyy", "dd/MM/yyyy".
     * Date-only values are never shifted through UTC.
     */
    fun parseDateOnly(dateStr: String): LocalDate? {
        if (dateStr.isBlank()) return null
        val date = PatientUtils.parseDate(dateStr) ?: return null
        return date.toLocalDate()
    }

    /**
     * Parse a timestamp (may include time+timezone), convert to IST, extract LocalDate.
     * For date-only strings, no timezone shift occurs.
     */
    fun parseToISTLocalDate(dateStr: String): LocalDate? {
        if (dateStr.isBlank()) return null
        // For date-only formats (d MMM yyyy, yyyy-MM-dd, d/M/yyyy, dd/MM/yyyy),
        // PatientUtils.parseDate already returns the correct local date.
        // For timestamps with timezone info (ISO 8601), parse as Instant and convert to IST.
        if (dateStr.contains("T") && (dateStr.contains("+") || dateStr.contains("-") || dateStr.endsWith("Z"))) {
            return try {
                val instant = Instant.parse(dateStr.replace(" ", "T"))
                instant.atZone(IST).toLocalDate()
            } catch (_: Exception) {
                // Fallback: parse as date-only
                parseDateOnly(dateStr)
            }
        }
        return parseDateOnly(dateStr)
    }

    /**
     * Compute the month bucket (as a YearMonth) for a date string, in IST.
     */
    fun monthOfIST(dateStr: String): YearMonth? {
        val localDate = parseToISTLocalDate(dateStr) ?: return null
        return YearMonth.from(localDate)
    }

    /**
     * Compute the effective registration date for a patient.
     * effectiveRegistrationDate = MIN(registrationDate, firstConsultationDate, firstVaccinationDate)
     * All dates are compared as calendar dates in IST.
     */
    fun effectiveRegistrationDate(
        patient: Patient,
        consultations: List<Vaccination>,
        vaccinations: List<Vaccination>
    ): LocalDate? {
        val regDate = parseToISTLocalDate(patient.registrationDate ?: "")

        val firstConsultDate = consultations
            .filter { it.patientId == patient.id && it.visitType.equals("CONSULTATION", true) }
            .mapNotNull { parseToISTLocalDate(it.dateGiven) }
            .minOrNull()

        val firstVaccDate = vaccinations
            .filter { it.patientId == patient.id && it.visitType.equals("VACCINATION", true) }
            .mapNotNull { parseToISTLocalDate(it.dateGiven) }
            .minOrNull()

        return listOfNotNull(regDate, firstConsultDate, firstVaccDate).minOrNull()
    }

    /**
     * Batch-compute effective registration dates for all patients.
     * Returns a Map<patientId, LocalDate?> for O(1) lookups.
     */
    fun computeEffectiveRegistrationDates(
        patients: List<Patient>,
        allVisits: List<Vaccination>
    ): Map<String, LocalDate?> {
        val consultsByPatient = allVisits
            .filter { it.visitType.equals("CONSULTATION", true) }
            .groupBy { it.patientId }
        val vaccsByPatient = allVisits
            .filter { it.visitType.equals("VACCINATION", true) }
            .groupBy { it.patientId }

        return patients.associate { patient ->
            patient.id to effectiveRegistrationDate(
                patient,
                consultsByPatient[patient.id] ?: emptyList(),
                vaccsByPatient[patient.id] ?: emptyList()
            )
        }
    }

    /**
     * Format a LocalDate as "d MMM yyyy" for display.
     */
    fun formatDateIST(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    }
}

