package com.neochildclinic.domain.usecase.patient

import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.repository.PatientRepository

/**
 * Use case to save or update a patient record.
 * Centralizes patient saving logic and validation.
 */
class SavePatientUseCase(private val repository: PatientRepository) {
    suspend operator fun invoke(patient: Patient) {
        // Business Rule: A patient must have a name and DOB
        if (patient.name.isBlank() || patient.dob.isBlank()) {
            throw IllegalArgumentException("Patient name and Date of Birth are required.")
        }
        
        repository.addPatient(patient)
    }
}
