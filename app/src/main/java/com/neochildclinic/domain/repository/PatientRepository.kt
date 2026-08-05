package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.AuditLogEntity
import com.neochildclinic.data.local.entity.PatientNotesEntity
import com.neochildclinic.domain.model.Patient
import kotlinx.coroutines.flow.Flow

/**
 * Domain-level Repository interface for Patient data.
 * The Patient entity is the primary root of this module.
 */
interface PatientRepository {
    val allPatients: Flow<List<Patient>>
    
    suspend fun getPatientById(id: String): Patient?
    suspend fun refreshPatients()
    suspend fun addPatient(patient: Patient)
    suspend fun deletePatient(id: String)
    
    fun searchPatients(query: String): Flow<List<Patient>>
    fun getPatientCount(): Flow<Int>
    suspend fun getTotalPatientCount(): Int
    
    // Timeline & History
    fun getPatientTimeline(patientId: String): Flow<List<AuditLogEntity>>
    fun getPatientHistory(patientId: String): Flow<List<com.neochildclinic.domain.model.Vaccination>>
    
    // Notes Module
    fun getNotes(patientId: String): Flow<List<PatientNotesEntity>>
    suspend fun addNote(patientId: String, content: String, author: String)
    suspend fun deleteNote(noteId: String)

    suspend fun refreshNotes()
}
