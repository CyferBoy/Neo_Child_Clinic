package com.neochildclinic.domain.repository

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
    suspend fun deductStockFromBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        visitId: String? = null,
        patientId: String? = null,
        notes: String? = null,
        allowExpired: Boolean = false,
        givenDate: String? = null
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
    suspend fun refreshInventory()
}