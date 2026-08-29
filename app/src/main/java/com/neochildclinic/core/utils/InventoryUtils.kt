package com.neochildclinic.core.utils

import java.util.*
import java.util.concurrent.TimeUnit

object InventoryUtils {

    /**
     * Checks if a batch is expired.
     */
    fun isExpired(expiryDateStr: String): Boolean {
        val expiryDate = PatientUtils.parseDate(expiryDateStr) ?: return false
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        return expiryDate.before(today)
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

        val normalizedExpiry = Calendar.getInstance().apply {
            time = expiryDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val normalizedReference = Calendar.getInstance().apply {
            time = referenceDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        return normalizedExpiry.before(normalizedReference)
    }

    /**
     * Checks if a batch is expiring today.
     */
    fun isExpiringToday(expiryDateStr: String): Boolean {
        val expiryDate = PatientUtils.parseDate(expiryDateStr) ?: return false
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val target = Calendar.getInstance().apply {
            time = expiryDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        return today.timeInMillis == target.timeInMillis
    }

    /**
     * Checks if a batch is expiring within the next 30 days.
     */
    fun isNearExpiry(expiryDateStr: String, thresholdDays: Int = 30): Boolean {
        val expiryDate = PatientUtils.parseDate(expiryDateStr) ?: return false
        
        if (isExpired(expiryDateStr) || isExpiringToday(expiryDateStr)) return false
        
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val diffInMs = expiryDate.time - today.time
        val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMs)
        
        return diffInDays <= thresholdDays
    }

    /**
     * Gets the number of days until expiry.
     */
    fun getDaysUntilExpiry(expiryDateStr: String): Long {
        val expiryDate = PatientUtils.parseDate(expiryDateStr) ?: return 0
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val diffInMs = expiryDate.time - today.time
        return TimeUnit.MILLISECONDS.toDays(diffInMs)
    }
}
