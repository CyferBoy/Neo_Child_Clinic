package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.ConsultationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsultationDao {
    @Query("SELECT * FROM consultations ORDER BY date DESC")
    fun getAllConsultations(): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE patientId = :patientId ORDER BY date DESC")
    fun getConsultationsForPatient(patientId: String): Flow<List<ConsultationEntity>>

    // --- Pagination (large-data scalability pass) --- additive, existing Flow methods
    // above are untouched.
    @Query("SELECT * FROM consultations ORDER BY date DESC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getAllConsultationsPage(limit: Int, offset: Int): List<ConsultationEntity>

    @Query("SELECT * FROM consultations WHERE patientId = :patientId ORDER BY date DESC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getConsultationsForPatientPage(patientId: String, limit: Int, offset: Int): List<ConsultationEntity>

    @Query("SELECT * FROM consultations WHERE id = :id")
    suspend fun getConsultationById(id: String): ConsultationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultation(consultation: ConsultationEntity)

    @Query("DELETE FROM consultations WHERE id = :id")
    suspend fun deleteConsultation(id: String)

    // Patient merge: move the consultation to the surviving patient and mark it dirty so
    // the new patient_id is pushed. updatedAt must move too, otherwise the conflict check
    // sees the remote row as newer and downloads the old patient_id back.
    @Query("SELECT id FROM consultations WHERE patientId = :patientId")
    suspend fun getConsultationIdsForPatient(patientId: String): List<String>

    @Query("UPDATE consultations SET patientId = :masterId, updatedAt = :updatedAt, isSynced = 0 WHERE patientId = :duplicateId")
    suspend fun reassignToPatient(duplicateId: String, masterId: String, updatedAt: String)

    // Remote-deletion propagation (RemotePrune wiring, Phase 2 Batch 1 / NCC-023).
    @Query("SELECT id FROM consultations")
    suspend fun getConsultationIds(): List<String>

    @Query("SELECT id FROM consultations WHERE isSynced = 1")
    suspend fun getSyncedConsultationIds(): List<String>

}
