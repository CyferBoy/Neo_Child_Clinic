package com.neochildclinic.data.remote

import com.neochildclinic.data.local.entity.PatientEntity

interface PatientRemoteDataSource {
    suspend fun fetchAllPatients(): List<PatientEntity>
    suspend fun fetchPatientById(id: String): PatientEntity?
}
