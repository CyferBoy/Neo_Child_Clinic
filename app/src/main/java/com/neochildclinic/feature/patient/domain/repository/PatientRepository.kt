package com.neochildclinic.feature.patient.domain.repository

import com.neochildclinic.domain.model.PatientNote
import com.neochildclinic.domain.model.Patient
import kotlinx.coroutines.flow.Flow

interface PatientRepository {
    val allPatients: Flow<List<Patient>>
    fun searchPatients(query: String): Flow<List<Patient>>
    suspend fun deletePatient(id: String)
    suspend fun refreshPatients()

    suspend fun getPatientById(id: String): Patient?
    suspend fun addPatient(patient: Patient)
    fun getPatientCount(): Flow<Int>

    /**
     * Ids of patients with at least one visit whose totalPaid is <= 0.0, for the
     * missing-price indicator on the patient list.
     *
     * This exists so the list does not have to load the entire vaccination dataset
     * (and one query per visit behind it) just to compute a flag. Replaces a
     * `vaccinations.filter { it.totalPaid <= 0.0 }` projection over the full set.
     */
    val patientIdsWithMissingPrice: Flow<Set<String>>
    fun getNotes(patientId: String): Flow<List<PatientNote>>
}