package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientTodoDao {
    @Query("SELECT * FROM consultation_todos WHERE todoDate = :date AND status = 'PENDING' ORDER BY name COLLATE NOCASE")
    fun getTodayConsultations(date: String): Flow<List<ConsultationTodoEntity>>

    @Query("SELECT * FROM vaccination_todos WHERE todoDate = :date AND status = 'PENDING' ORDER BY name COLLATE NOCASE")
    fun getTodayVaccinations(date: String): Flow<List<VaccinationTodoEntity>>

    @Query("SELECT * FROM consultation_todos WHERE id = :id LIMIT 1")
    suspend fun getConsultationTodoById(id: String): ConsultationTodoEntity?

    @Query("SELECT * FROM vaccination_todos WHERE id = :id LIMIT 1")
    suspend fun getVaccinationTodoById(id: String): VaccinationTodoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultation(todo: ConsultationTodoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccination(todo: VaccinationTodoEntity)

    @Query("DELETE FROM consultation_todos WHERE id = :id")
    suspend fun deleteConsultation(id: String)

    @Query("DELETE FROM vaccination_todos WHERE id = :id")
    suspend fun deleteVaccination(id: String)
}
