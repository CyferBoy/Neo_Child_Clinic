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
    suspend fun getVaccineById(vaccineId: String): VaccineEntity?
    
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
        notes: String? = null,
        // Skip the expiry guard for a batch that was already recorded against this visit
        // before it expired (e.g. bumping quantity on an edit). New batch selections should
        // still be blocked once expired.
        allowExpired: Boolean = false,
        // The vaccination's given date. A VACCINATION-type deduction is validated against
        // this date, not today's date - a batch is valid as long as expiryDate >= givenDate,
        // even if it has since expired by today. Null falls back to today-based validation
        // (only relevant to non-vaccination transaction types, which don't use this check).
        givenDate: String? = null
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
        user: String,
        visitId: String? = null,
        patientId: String? = null
    )

    // Returns previously borrowed stock. If returnToBatchId matches the batch the
    // stock was originally borrowed from, that batch's borrowedQuantity is cleared.
    // If it's returned into a different batch, the stock lands there but the
    // original batch keeps showing the amount as still outstanding/borrowed.
    suspend fun returnBorrowedStock(
        originalBatchId: String,
        returnToBatchId: String,
        quantity: Int,
        user: String,
        notes: String? = null
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
