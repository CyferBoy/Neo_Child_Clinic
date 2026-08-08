package com.neochildclinic.domain.repository

import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.VaccineEntity
import com.neochildclinic.domain.model.InventoryFilter
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventorySort
import com.neochildclinic.domain.model.InventoryTransactionType
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun getInventoryItems(
        query: String = "",
        filter: InventoryFilter = InventoryFilter.ALL,
        sort: InventorySort = InventorySort.ALPHABETICAL
    ): Flow<List<InventoryItem>>
    
    fun getVaccineBatches(vaccineId: String): Flow<List<VaccineBatchEntity>>
    fun getInventoryTransactions(vaccineId: String): Flow<List<InventoryTransactionEntity>>
    suspend fun getBatchById(batchId: String): VaccineBatchEntity?
    
    suspend fun addVaccine(vaccine: VaccineEntity, user: String)
    suspend fun updateVaccine(vaccine: VaccineEntity, user: String)
    suspend fun addBatch(batch: VaccineBatchEntity, user: String)
    
    suspend fun updateBatch(
        batch: VaccineBatchEntity,
        user: String,
        notes: String? = null
    )
    
    suspend fun deleteBatch(batchId: String, user: String)
    suspend fun deleteVaccine(vaccineId: String, user: String)
    
    suspend fun deductStock(
        vaccineId: String, 
        quantity: Int, 
        user: String, 
        transactionType: InventoryTransactionType,
        visitId: String? = null,
        patientId: String? = null
    )

    suspend fun deductStockFromBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        visitId: String? = null,
        patientId: String? = null,
        notes: String? = null
    )

    suspend fun addStockToBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        notes: String? = null
    )

    suspend fun reverseDeduction(
        batchId: String,
        quantity: Int,
        user: String
    )

    suspend fun adjustStock(
        batchId: String, 
        newQuantity: Int, 
        user: String, 
        reason: String
    )

    suspend fun transferPatientTransactions(duplicateId: String, masterId: String)
    suspend fun refreshInventory()
}
