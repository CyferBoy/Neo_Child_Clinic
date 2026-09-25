package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientTodoDao {
    @Query("SELECT * FROM consultation_todos WHERE todo_date = :date AND status = :status AND is_deleted = 0 ORDER BY name COLLATE NOCASE")
    fun getConsultationsByDateAndStatus(date: String, status: String): Flow<List<ConsultationTodoEntity>>

    @Query("SELECT * FROM vaccination_todos WHERE todo_date = :date AND status = :status AND is_deleted = 0 ORDER BY name COLLATE NOCASE")
    fun getVaccinationsByDateAndStatus(date: String, status: String): Flow<List<VaccinationTodoEntity>>

    @Query("SELECT DISTINCT todo_date FROM consultation_todos WHERE is_deleted = 0 AND todo_date BETWEEN :start AND :end UNION SELECT DISTINCT todo_date FROM vaccination_todos WHERE is_deleted = 0 AND todo_date BETWEEN :start AND :end")
    fun getDatesWithData(start: String, end: String): Flow<List<String>>

    @Query("UPDATE consultation_todos SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateConsultationStatus(id: String, status: String, updatedAt: String)

    @Query("UPDATE vaccination_todos SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateVaccinationStatus(id: String, status: String, updatedAt: String)

    @Query("SELECT * FROM consultation_todos WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getConsultationTodoById(id: String): ConsultationTodoEntity?

    @Query("SELECT * FROM vaccination_todos WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getVaccinationTodoById(id: String): VaccinationTodoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultation(todo: ConsultationTodoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccination(todo: VaccinationTodoEntity)

    @Query("UPDATE consultation_todos SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, is_synced = 0, updated_at = :deletedAt, updated_by = :deletedBy WHERE id = :id AND is_deleted = 0")
    suspend fun deleteConsultation(id: String, deletedAt: String, deletedBy: String?)

    @Query("UPDATE vaccination_todos SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, is_synced = 0, updated_at = :deletedAt, updated_by = :deletedBy WHERE id = :id AND is_deleted = 0")
    suspend fun deleteVaccination(id: String, deletedAt: String, deletedBy: String?)
}
