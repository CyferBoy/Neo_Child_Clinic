package com.neochildclinic.domain.repository

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

    // Paginated access (large-data scalability pass). Keyset-based: pass the name/id of
    // the last patient from the previous page, or null for the first page. Additive -
    // allPatients/searchPatients above are unchanged; screens that need a bounded,
    // infinite-scroll-style list should migrate to this instead of collecting allPatients.
    suspend fun getPatientsPage(afterName: String?, afterId: String?, limit: Int): List<Patient>
    
    // Timeline & History
    // NOTE: patient audit history is now loaded online-only via PatientAuditLogPager
    // (see PatientViewModel/PatientListViewModel), not through this repository.
    fun getPatientHistory(patientId: String): Flow<List<com.neochildclinic.domain.model.Vaccination>>
    
    // Notes Module
    fun getNotes(patientId: String): Flow<List<PatientNotesEntity>>
    suspend fun addNote(patientId: String, content: String, author: String)
    suspend fun deleteNote(noteId: String)

    suspend fun refreshNotes()
}
