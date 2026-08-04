package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.ConsultationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsultationDao {
    @Query("SELECT * FROM consultations WHERE isDeleted = 0")
    fun getAllConsultations(): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE patientId = :patientId AND isDeleted = 0 ORDER BY date DESC")
    fun getConsultationsForPatient(patientId: String): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE id = :id AND isDeleted = 0")
    suspend fun getConsultationById(id: String): ConsultationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultation(consultation: ConsultationEntity)

    @Query("UPDATE consultations SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun deleteConsultation(id: String)

    @Query("SELECT * FROM consultations WHERE isSynced = 0")
    suspend fun getUnsyncedConsultations(): List<ConsultationEntity>

    @Query("UPDATE consultations SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
