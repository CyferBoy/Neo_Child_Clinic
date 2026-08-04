package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.Consultation
import kotlinx.coroutines.flow.Flow

interface ConsultationRepository {
    fun getConsultationsForPatient(patientId: String): Flow<List<Consultation>>
    suspend fun getConsultationById(id: String): Consultation?
    suspend fun addConsultation(consultation: Consultation)
    suspend fun deleteConsultation(id: String)
    suspend fun refreshConsultations()
}
