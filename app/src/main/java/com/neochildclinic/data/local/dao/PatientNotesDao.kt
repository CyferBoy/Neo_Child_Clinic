package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.PatientNotesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientNotesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: PatientNotesEntity)

    @Query("SELECT * FROM patient_notes WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getNoteById(id: String): PatientNotesEntity?

    @Query("SELECT * FROM patient_notes WHERE patientId = :patientId AND is_deleted = 0 ORDER BY timestamp DESC")
    fun getNotesForPatient(patientId: String): Flow<List<PatientNotesEntity>>

    @Query("UPDATE patient_notes SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, isSynced = 0, timestamp = :deletedAt, updated_by = :deletedBy WHERE id = :id AND is_deleted = 0")
    suspend fun deleteNote(id: String, deletedAt: String, deletedBy: String?)
}
