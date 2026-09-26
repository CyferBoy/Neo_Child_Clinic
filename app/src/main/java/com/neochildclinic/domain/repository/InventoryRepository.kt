package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.InventoryFilter
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventorySort
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.VaccineEntity
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun getInventoryItems(
        query: String = "",
        filter: InventoryFilter = InventoryFilter.ALL,
        sort: InventorySort = InventorySort.ALPHABETICAL
    ): Flow<List<InventoryItem>>
    fun getAllVaccines(): Flow<List<VaccineEntity>>
    fun getVaccineBatches(vaccineId: String): Flow<List<VaccineBatchEntity>>
    suspend fun getBatchById(batchId: String): VaccineBatchEntity?
    suspend fun getVaccineById(vaccineId: String): VaccineEntity?
    suspend fun addVaccine(vaccine: VaccineEntity, user: String)
    suspend fun updateVaccine(vaccine: VaccineEntity, user: String)
    suspend fun addBatch(batch: VaccineBatchEntity, user: String, transactionGroupId: String? = null)
    suspend fun addStockBatch(entriesByVaccine: Map<String, List<VaccineBatchEntity>>, user: String)
    suspend fun updateBatch(batch: VaccineBatchEntity, user: String, notes: String? = null)
    suspend fun deleteBatch(batchId: String, user: String)
    suspend fun deleteVaccine(vaccineId: String, user: String)
    suspend fun getStockHistoryPage(
        vaccineId: String? = null,
        batchId: String? = null,
        types: List<InventoryTransactionType> = emptyList(),
        fromDateIso: String? = null,
        toDateIso: String? = null,
        limit: Int = 50,
        offset: Int = 0,
        remoteOnly: Boolean = true
    ): List<InventoryTransactionEntity>
    suspend fun deductStock(
        vaccineId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        visitId: String? = null,
        patientId: String? = null
    )
    suspend fun addStockToBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        notes: String? = null
    )
    suspend fun returnBorrowedStock(
        originalBatchId: String,
        returnToBatchId: String,
        quantity: Int,
        user: String,
        notes: String? = null,
        transactionGroupId: String? = null
    )
    suspend fun deductStockFromBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        visitId: String? = null,
        patientId: String? = null,
        notes: String? = null,
        allowExpired: Boolean = false,
        givenDate: String? = null,
        transactionGroupId: String? = null
    )
    suspend fun reverseDeduction(
        batchId: String,
        quantity: Int,
        user: String,
        visitId: String? = null,
        patientId: String? = null,
        transactionGroupId: String? = null
    )
    suspend fun transferPatientTransactions(duplicateId: String, masterId: String)
    suspend fun getInventoryDeductionsForVaccination(vaccinationId: String): List<InventoryDeductionEntity>
    suspend fun insertInventoryDeduction(entity: InventoryDeductionEntity)
    suspend fun deleteInventoryDeductionsForVaccination(vaccinationId: String)
    suspend fun refreshInventory()
}