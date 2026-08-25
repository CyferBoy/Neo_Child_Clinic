package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import kotlinx.coroutines.flow.Flow

interface PatientTodoRepository {
    fun getTodayConsultations(date: String): Flow<List<ConsultationTodoEntity>>
    fun getTodayVaccinations(date: String): Flow<List<VaccinationTodoEntity>>
    suspend fun refresh()
    suspend fun addConsultation(todo: ConsultationTodoEntity)
    suspend fun addVaccination(todo: VaccinationTodoEntity)
    suspend fun deleteConsultation(id: String)
    suspend fun deleteVaccination(id: String)
}
