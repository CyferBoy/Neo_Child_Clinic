package com.neochildclinic.core.utils

import com.neochildclinic.data.local.dao.VaccinationDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptNumberGenerator @Inject constructor(
    private val vaccinationDao: VaccinationDao
) {
    /**
     * Generates a unique receipt number.
     * Format: RCT-XXXX (Sequential based on highest existing), mirroring
     * PatientIdGenerator's NEO-XXXX clinic ID scheme.
     */
    suspend fun generateUniqueReceiptNumber(): String {
        val maxReceipt = vaccinationDao.getMaxReceiptNumber()
        val nextNumber = if (maxReceipt != null && maxReceipt.startsWith("RCT-")) {
            val numericPart = maxReceipt.substring(4).toIntOrNull() ?: 0
            numericPart + 1
        } else {
            1000 // Start from 1000, same as clinic IDs
        }

        var receiptNumber = "RCT-$nextNumber"
        var isUnique = false
        var currentNum = nextNumber

        // Final safety check for uniqueness
        while (!isUnique) {
            val existing = vaccinationDao.getVaccinationByReceiptNumber(receiptNumber)
            if (existing == null) {
                isUnique = true
            } else {
                currentNum++
                receiptNumber = "RCT-$currentNum"
            }
        }

        return receiptNumber
    }
}
