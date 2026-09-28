package com.neochildclinic.data.remote.datasource

import com.neochildclinic.data.local.entity.PatientEntity
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow

interface PatientRemoteDataSource {
    suspend fun fetchAllPatients(): List<PatientEntity>
    suspend fun fetchPatientById(id: String): PatientEntity?
}