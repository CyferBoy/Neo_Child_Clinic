package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.PatientNotesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientNotesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: PatientNotesEntity)

    @Query("SELECT * FROM patient_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: String): PatientNotesEntity?

    @Query("SELECT * FROM patient_notes WHERE patientId = :patientId ORDER BY timestamp DESC")
    fun getNotesForPatient(patientId: String): Flow<List<PatientNotesEntity>>

    @Query("DELETE FROM patient_notes WHERE id = :id")
    suspend fun deleteNote(id: String)

    // Patient merge: notes have no updatedAt column, so the descriptor treats an unsynced
    // note as freshly edited; isSynced=0 is what lets the new patient_id upload.
    @Query("SELECT id FROM patient_notes WHERE patientId = :patientId")
    suspend fun getNoteIdsForPatient(patientId: String): List<String>

    @Query("UPDATE patient_notes SET patientId = :masterId, isSynced = 0 WHERE patientId = :duplicateId")
    suspend fun reassignToPatient(duplicateId: String, masterId: String)
}
