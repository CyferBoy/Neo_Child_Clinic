package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.model.Vaccination
import kotlinx.coroutines.flow.Flow

// ponytail: getAllTransactions returns the Room DTO (FinanceEntity) - it IS the clinic's
// transaction record and the statistics calculators already consume it as such. Mapping to a
// parallel domain model here would be pure churn with no caller paying attention to the
// difference; introduce a Transaction domain model only if a consumer starts mutating it.
interface FinanceRepository {
    fun getAllTransactions(): Flow<List<FinanceEntity>>
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