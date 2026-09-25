package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.ConsultationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsultationDao {
    @Query("SELECT * FROM consultations WHERE is_deleted = 0 ORDER BY date DESC")
    fun getAllConsultations(): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE patientId = :patientId AND is_deleted = 0 ORDER BY date DESC")
    fun getConsultationsForPatient(patientId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE id = :id AND is_deleted = 0")
    suspend fun getConsultationById(id: String): ConsultationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultation(consultation: ConsultationEntity)

    @Query("UPDATE consultations SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, isSynced = 0, updatedAt = :deletedAt, updated_by = :deletedBy WHERE id = :id")
    suspend fun deleteConsultation(id: String, deletedAt: String, deletedBy: String?)
}
