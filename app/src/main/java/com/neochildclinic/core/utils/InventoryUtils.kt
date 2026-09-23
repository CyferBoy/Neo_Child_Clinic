package com.neochildclinic.core.utils

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object InventoryUtils {

    /**
     * Checks if a batch is expired.
     */
    fun isExpired(expiryDateStr: String): Boolean {
        val expiryDate = PatientUtils.parseDate(expiryDateStr) ?: return false
        return expiryDate.toLocalDate().isBefore(LocalDate.now())
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
     * Checks if a batch is expiring today.
     */
    fun isExpiringToday(expiryDateStr: String): Boolean {
        val expiryDate = PatientUtils.parseDate(expiryDateStr) ?: return false
        return expiryDate.toLocalDate() == LocalDate.now()
    }

    /**
     * Checks if a batch is expiring within the next 30 days.
     */
    fun isNearExpiry(expiryDateStr: String, thresholdDays: Int = 30): Boolean {
        if (isExpired(expiryDateStr) || isExpiringToday(expiryDateStr)) return false

        val expiryDate = PatientUtils.parseDate(expiryDateStr) ?: return false
        val diffInDays = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate.toLocalDate())
        return diffInDays <= thresholdDays
    }
}
