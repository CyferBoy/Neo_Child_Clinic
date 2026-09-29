package com.neochildclinic.feature.patient.domain.repository

import com.neochildclinic.domain.model.ConsultationTodo
import com.neochildclinic.domain.model.VaccinationTodo
import kotlinx.coroutines.flow.Flow

interface PatientTodoRepository {
    fun getConsultationsByDateAndStatus(date: String, status: String): Flow<List<ConsultationTodo>>
    fun getVaccinationsByDateAndStatus(date: String, status: String): Flow<List<VaccinationTodo>>
    fun getDatesWithData(start: String, end: String): Flow<List<String>>
    suspend fun updateStatus(type: String, id: String, status: String)
    suspend fun addConsultation(todo: ConsultationTodo)
    suspend fun addVaccination(todo: VaccinationTodo)
    suspend fun deleteConsultation(id: String)
    suspend fun deleteVaccination(id: String)
    suspend fun refresh()
}