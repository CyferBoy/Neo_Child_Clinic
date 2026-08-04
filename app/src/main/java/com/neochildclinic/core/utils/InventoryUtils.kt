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
