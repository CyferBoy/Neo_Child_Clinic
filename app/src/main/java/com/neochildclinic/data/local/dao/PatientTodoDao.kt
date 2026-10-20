package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientTodoDao {
    @Query("SELECT * FROM consultation_todos WHERE todo_date = :date AND status = :status ORDER BY name COLLATE NOCASE")
    fun getConsultationsByDateAndStatus(date: String, status: String): Flow<List<ConsultationTodoEntity>>

    @Query("SELECT * FROM vaccination_todos WHERE todo_date = :date AND status = :status ORDER BY name COLLATE NOCASE")
    fun getVaccinationsByDateAndStatus(date: String, status: String): Flow<List<VaccinationTodoEntity>>

    @Query("SELECT DISTINCT todo_date FROM consultation_todos WHERE todo_date BETWEEN :start AND :end UNION SELECT DISTINCT todo_date FROM vaccination_todos WHERE todo_date BETWEEN :start AND :end")
    fun getDatesWithData(start: String, end: String): Flow<List<String>>

    // is_synced = 0 marks the row dirty: without it the refresh guard in
    // PatientTodoRepositoryImpl (local == null || local.isSynced) treats the row as fully
    // synced and lets the next pull overwrite the local status change.
    @Query("UPDATE consultation_todos SET status = :status, updated_at = :updatedAt, is_synced = 0 WHERE id = :id")
    suspend fun updateConsultationStatus(id: String, status: String, updatedAt: String)

    @Query("UPDATE vaccination_todos SET status = :status, updated_at = :updatedAt, is_synced = 0 WHERE id = :id")
    suspend fun updateVaccinationStatus(id: String, status: String, updatedAt: String)

    @Query("SELECT * FROM consultation_todos WHERE id = :id LIMIT 1")
    suspend fun getConsultationTodoById(id: String): ConsultationTodoEntity?

    @Query("SELECT * FROM vaccination_todos WHERE id = :id LIMIT 1")
    suspend fun getVaccinationTodoById(id: String): VaccinationTodoEntity?

    // Parameterless snapshots for the refresh loop (same pattern as PatientDao's
    // getAllPatientsSnapshot): one query per table, built once outside the loop.
    @Query("SELECT * FROM consultation_todos")
    suspend fun getAllConsultationTodos(): List<ConsultationTodoEntity>

    @Query("SELECT * FROM vaccination_todos")
    suspend fun getAllVaccinationTodos(): List<VaccinationTodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultation(todo: ConsultationTodoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccination(todo: VaccinationTodoEntity)

    @Query("DELETE FROM consultation_todos WHERE id = :id")
    suspend fun deleteConsultation(id: String)

    @Query("DELETE FROM vaccination_todos WHERE id = :id")
    suspend fun deleteVaccination(id: String)
}
