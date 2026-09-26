package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import kotlinx.coroutines.flow.Flow

// ponytail: the Today's Patient list is a pure projection over consultation_todos /
// vaccination_todos composite-entity views consumed read-mostly by the dashboard calendar
// grid; it passes the Room views straight through, same as the item/card DTOs elsewhere.
interface PatientTodoRepository {
    fun getConsultationsByDateAndStatus(date: String, status: String): Flow<List<ConsultationTodoEntity>>
    fun getVaccinationsByDateAndStatus(date: String, status: String): Flow<List<VaccinationTodoEntity>>
    fun getDatesWithData(start: String, end: String): Flow<List<String>>
    suspend fun updateStatus(type: String, id: String, status: String)
    suspend fun addConsultation(todo: ConsultationTodoEntity)
    suspend fun addVaccination(todo: VaccinationTodoEntity)
    suspend fun deleteConsultation(id: String)
    suspend fun deleteVaccination(id: String)
    suspend fun refresh()
}