package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.model.Vaccination
import kotlinx.coroutines.flow.Flow

interface FinanceRepository {
    fun getAllTransactions(): Flow<List<FinanceEntity>>
    fun getTransactionsForPatient(patientId: String): Flow<List<FinanceEntity>>
    fun getDailyIncome(start: Long): Flow<Double?>
    
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
    
    suspend fun recordExpense(
        amount: Double, 
        category: String, 
        remarks: String?,
        recordedBy: String
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

    suspend fun refreshTransactions()

    /** Creates immutable COGS snapshots for legacy vaccination income transactions. */
    suspend fun migrateLegacyVaccinationCogs(vaccinations: List<Vaccination>)
}
