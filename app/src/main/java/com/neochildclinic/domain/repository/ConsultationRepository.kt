package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.Consultation

interface ConsultationRepository {
    suspend fun addConsultation(consultation: Consultation, transactionGroupId: String? = null)
    suspend fun updateConsultation(consultation: Consultation, transactionGroupId: String? = null)
    suspend fun refreshConsultations()
}