package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.PatientNotesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientNotesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: PatientNotesEntity): Long

    @Query("SELECT * FROM patient_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): PatientNotesEntity?

    @Query("SELECT * FROM patient_notes WHERE patientId = :patientId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getNotesForPatient(patientId: String): Flow<List<PatientNotesEntity>>

    @Query("UPDATE patient_notes SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun deleteNote(id: Long)
}
