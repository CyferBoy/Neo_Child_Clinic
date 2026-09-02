package com.neochildclinic.domain.repository

import com.neochildclinic.core.model.BorrowReturnRecord
import com.neochildclinic.core.model.BorrowedVaccine
import com.neochildclinic.features.inventory.BorrowedDisplayItem
import kotlinx.coroutines.flow.Flow

data class NewBatchInfo(
    val batchNumber: String,
    val expiryDate: String,
    val purchaseCost: Double = 0.0,
    val sellingPrice: Double = 0.0
)

interface BorrowRepository {
    fun getActiveBorrowedRecords(): Flow<List<BorrowedVaccine>>
    fun getReturnedRecords(): Flow<List<BorrowedVaccine>>
    fun getReturnRecords(): Flow<List<BorrowReturnRecord>>
    
    suspend fun saveBorrowedItem(item: BorrowedVaccine)
    suspend fun deleteBorrowedItem(id: String)
    
    suspend fun submitReturn(
        item: BorrowedDisplayItem,
        quantity: Int,
        batchId: String,
        notes: String?,
        newBatchInfo: NewBatchInfo? = null
    )

    suspend fun refreshBorrows()
}
