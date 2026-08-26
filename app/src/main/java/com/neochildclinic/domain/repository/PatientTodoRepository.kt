package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import kotlinx.coroutines.flow.Flow

interface PatientTodoRepository {
    fun getConsultationsByDateAndStatus(date: String, status: String): Flow<List<ConsultationTodoEntity>>
    fun getVaccinationsByDateAndStatus(date: String, status: String): Flow<List<VaccinationTodoEntity>>
    fun getDatesWithData(start: String, end: String): Flow<List<String>>
    suspend fun updateStatus(type: String, id: String, status: String)
    fun getTodayConsultations(date: String): Flow<List<ConsultationTodoEntity>>
    fun getTodayVaccinations(date: String): Flow<List<VaccinationTodoEntity>>
    suspend fun refresh()
    suspend fun addConsultation(todo: ConsultationTodoEntity)
    suspend fun addVaccination(todo: VaccinationTodoEntity)
    suspend fun deleteConsultation(id: String)
    suspend fun deleteVaccination(id: String)
}
