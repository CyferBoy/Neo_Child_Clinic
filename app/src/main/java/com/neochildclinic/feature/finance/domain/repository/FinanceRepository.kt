package com.neochildclinic.feature.finance.domain.repository

import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.Vaccination
import kotlinx.coroutines.flow.Flow

interface FinanceRepository {
    fun getAllTransactions(): Flow<List<FinanceTransaction>>
    suspend fun recordIncome(
        amount: Double,
        cashAmount: Double,
        onlineAmount: Double,
        category: String,
        patientId: String?,
        visitId: String?,
        remarks: String?,
        recordedBy: String,
        transactionGroupId: String? = null
    )
    suspend fun updateConsultationIncome(
        visitId: String,
        consultationId: String,
        originalAmount: Double,
        originalCashAmount: Double,
        originalOnlineAmount: Double,
        amount: Double,
        cashAmount: Double,
        onlineAmount: Double,
        remarks: String?,
        recordedBy: String,
        transactionGroupId: String? = null
    )
    suspend fun updateIncomeForVisit(
        visitId: String,
        amount: Double,
        cashAmount: Double,
        onlineAmount: Double,
        remarks: String?,
        recordedBy: String,
        transactionGroupId: String? = null
    )
    suspend fun migrateLegacyVaccinationCogs(vaccinations: List<Vaccination>)
    suspend fun refreshTransactions()
}