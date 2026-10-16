package com.neochildclinic.feature.inventory.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import com.neochildclinic.core.common.IST_ZONE_ID
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.common.toLocalDate

object InventoryUtils {

    /**
     * Today in the clinic's timezone, not the device's. A device east of IST runs a day ahead,
     * so comparing an IST expiry date against LocalDate.now() treated a batch expiring *today*
     * as already expired - hiding usable doses and blocking a same-day vaccination.
     */
    private fun clinicToday(): LocalDate = LocalDate.now(ZoneId.of(IST_ZONE_ID))

    /**
     * Checks if a batch is expired as of the given reference date (defaults to today in the
     * clinic timezone). Injectable so the boundary is deterministic in tests.
     */
    fun isExpired(expiryDateStr: String, today: LocalDate = clinicToday()): Boolean {
        val expiryDate = PatientUtils.parseDate(expiryDateStr) ?: return false
        return expiryDate.toLocalDate().isBefore(today)
    }

    /**
     * Checks whether a batch is expired as of a specific reference date (e.g. a
     * vaccination's given date), rather than today. Used for historical-record validation
     * where "expired" must mean "already expired on the date the record represents", not
     * "expired as of right now". Dates are compared at day granularity only; equal dates
     * are NOT considered expired (expiryDate >= referenceDate is valid).
     * Falls back to isExpired(expiryDateStr) (today-based) if the reference date can't be
     * parsed, matching this object's existing safe-default behavior.
     */
    fun isExpiredAsOf(expiryDateStr: String, referenceDateStr: String): Boolean {
        val expiryDate = PatientUtils.parseDate(expiryDateStr) ?: return false
        val referenceDate = PatientUtils.parseDate(referenceDateStr) ?: return isExpired(expiryDateStr)

        return expiryDate.toLocalDate().isBefore(referenceDate.toLocalDate())
    }

    /**
     * Checks if a batch is expiring on the given day (defaults to today in the clinic timezone).
     */
    fun isExpiringToday(expiryDateStr: String, today: LocalDate = clinicToday()): Boolean {
        val expiryDate = PatientUtils.parseDate(expiryDateStr) ?: return false
        return expiryDate.toLocalDate() == today
    }

    /**
     * Checks if a batch is expiring within the given number of days (default 30).
     */
    fun isNearExpiry(expiryDateStr: String, thresholdDays: Int = 30, today: LocalDate = clinicToday()): Boolean {
        val expiryDate = PatientUtils.parseDate(expiryDateStr) ?: return false
        if (isExpired(expiryDateStr, today) || isExpiringToday(expiryDateStr, today)) return false

        val diffInDays = ChronoUnit.DAYS.between(today, expiryDate.toLocalDate())
        return diffInDays <= thresholdDays
    }
}
