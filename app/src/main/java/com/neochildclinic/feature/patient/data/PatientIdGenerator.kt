package com.neochildclinic.feature.patient.data

import android.content.Context
import com.neochildclinic.data.local.dao.PatientDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientIdGenerator @Inject constructor(
    private val patientDao: PatientDao,
    @ApplicationContext context: Context
) {
    // Two devices both starting at NEO-1000 produced the same ID for different patients;
    // the server's unique index then rejected one device's upload forever. A stable
    // per-install suffix keeps the numeric part readable while making the full ID unique.
    private val deviceSuffix: String by lazy {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_SUFFIX, null) ?: UUID.randomUUID().toString()
            .replace("-", "").take(4).uppercase()
            .also { prefs.edit().putString(KEY_DEVICE_SUFFIX, it).apply() }
    }

    /**
     * Generates a unique clinic ID.
     * Format: NEO-XXXX-YYYY (sequential number plus a per-install suffix)
     */
    suspend fun generateUniqueClinicId(): String {
        val maxId = patientDao.getMaxClinicId()
        val nextNumber = if (maxId != null && maxId.startsWith("NEO-")) {
            val numericPart = maxId.removePrefix("NEO-").substringBefore('-').toIntOrNull() ?: 0
            numericPart + 1
        } else {
            1000 // Start from 1000 for new clinics
        }

        var clinicId = "NEO-$nextNumber-$deviceSuffix"
        var isUnique = false
        var currentNum = nextNumber

        // Final safety check for uniqueness
        while (!isUnique) {
            val existing = patientDao.getPatientByClinicId(clinicId)
            if (existing == null) {
                isUnique = true
            } else {
                currentNum++
                clinicId = "NEO-$currentNum-$deviceSuffix"
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

    private companion object {
        const val PREFS_NAME = "patient_id_generator"
        const val KEY_DEVICE_SUFFIX = "device_suffix"
    }
}
