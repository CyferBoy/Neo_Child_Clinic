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
}
