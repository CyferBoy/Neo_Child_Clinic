package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.Patient
import kotlinx.coroutines.flow.Flow

interface PatientRepository {
    val allPatients: Flow<List<Patient>>
    fun searchPatients(query: String): Flow<List<Patient>>
    suspend fun deletePatient(id: String)
    suspend fun refreshPatients()
}