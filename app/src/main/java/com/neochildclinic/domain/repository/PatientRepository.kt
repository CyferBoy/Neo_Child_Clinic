package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.PatientNotesEntity
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
    // ponytail: notes pass the Room DTO straight through - it is the persisted row, read-only
    // in the UI, and the note supports audit columns the UI renders. A domain model for it would
    // be a linear map with no consumer mutating anything through it.
    fun getNotes(patientId: String): Flow<List<PatientNotesEntity>>
}