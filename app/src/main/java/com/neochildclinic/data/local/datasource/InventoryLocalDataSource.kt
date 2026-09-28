package com.neochildclinic.data.local.datasource

import com.neochildclinic.data.local.entity.VaccineEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import com.neochildclinic.domain.model.InventoryTransactionType
import kotlinx.coroutines.flow.Flow

interface InventoryLocalDataSource {
    fun getAllVaccines(): Flow<List<VaccineEntity>>
    fun getBatchesByVaccine(vaccineId: String): Flow<List<VaccineBatchEntity>>
    suspend fun getVaccineById(vaccineId: String): VaccineEntity?
    suspend fun getBatchById(batchId: String): VaccineBatchEntity?
    suspend fun getBatchByVaccineAndNumber(vaccineId: String, batchNumber: String): VaccineBatchEntity?
    suspend fun insertVaccine(vaccine: VaccineEntity)
    suspend fun updateVaccine(vaccine: VaccineEntity)
    suspend fun insertBatch(batch: VaccineBatchEntity)
    suspend fun updateBatch(batch: VaccineBatchEntity)
    suspend fun deleteBatch(batchId: String, deletedAt: String, deletedBy: String)
    suspend fun deleteVaccine(vaccineId: String, deletedAt: String, deletedBy: String)
    suspend fun getBatchCountForVaccine(vaccineId: String): Int
    suspend fun getVaccinationCountForVaccine(vaccineId: String): Int
    suspend fun getWasteCountForVaccine(vaccineId: String): Int
    suspend fun getTransactionCountForVaccine(vaccineId: String): Int
    suspend fun insertTransaction(transaction: InventoryTransactionEntity)
    suspend fun getFilteredTransactionsPage(
        vaccineId: String?,
        batchId: String?,
        types: List<String>,
        typesEmpty: Boolean,
        fromDate: String?,
        toDate: String?,
        limit: Int,
        offset: Int
    ): List<InventoryTransactionEntity>
    suspend fun getTransactionById(transactionId: String): InventoryTransactionEntity?
    suspend fun updatePatientIdInTransactions(duplicateId: String, masterId: String)
    suspend fun getTotalStockForVaccine(vaccineId: String): Int?
    fun getActiveBatchesByExpiry(vaccineId: String): Flow<List<VaccineBatchEntity>>
    suspend fun getCompletedForVaccination(vaccinationId: String): List<InventoryDeductionEntity>
    suspend fun insertDeduction(deduction: InventoryDeductionEntity)
    suspend fun deleteForVaccination(vaccinationId: String)
    suspend fun getForVaccination(vaccinationId: String): List<InventoryDeductionEntity>
    suspend fun isUnsyncedBatch(batchId: String): Boolean
    suspend fun isUnsyncedVaccine(vaccineId: String): Boolean
    suspend fun isUnsyncedTransaction(transactionId: String): Boolean
}