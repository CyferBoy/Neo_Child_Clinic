package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.Consultation
import kotlinx.coroutines.flow.Flow

interface ConsultationRepository {
    suspend fun addConsultation(consultation: Consultation, transactionGroupId: String? = null)
    suspend fun updateConsultation(consultation: Consultation, transactionGroupId: String? = null)
    suspend fun deleteConsultation(id: String)
    suspend fun getConsultationById(id: String): Consultation?
    fun getConsultationsForPatient(patientId: String): Flow<List<Consultation>>
    suspend fun refreshConsultations()
}