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
    fun getNotes(patientId: String): Flow<List<PatientNote>>
}