package com.neochildclinic.core.utils

import com.neochildclinic.data.local.dao.PatientDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientIdGenerator @Inject constructor(
    private val patientDao: PatientDao
) {
    /**
     * Generates a unique clinic ID.
     * Format: NEO-XXXX (Sequential based on highest existing)
     */
    suspend fun generateUniqueClinicId(): String {
        val maxId = patientDao.getMaxClinicId()
        val nextNumber = if (maxId != null && maxId.startsWith("NEO-")) {
            val numericPart = maxId.substring(4).toIntOrNull() ?: 0
            numericPart + 1
        } else {
            1000 // Start from 1000 for new clinics
        }
        
        var clinicId = "NEO-$nextNumber"
        var isUnique = false
        var currentNum = nextNumber
        
        // Final safety check for uniqueness
        while (!isUnique) {
            val existing = patientDao.getPatientByClinicId(clinicId)
            if (existing == null) {
                isUnique = true
            } else {
                currentNum++
                clinicId = "NEO-$currentNum"
            }
        }
        
        return clinicId
    }

    /**
     * Verifies if a given ID is unique.
     */
    suspend fun isIdUnique(clinicId: String, currentPatientId: String? = null): Boolean {
        if (clinicId.isBlank()) return true
        val existing = patientDao.getPatientByClinicId(clinicId)
        return existing == null || existing.id == currentPatientId
    }
}
