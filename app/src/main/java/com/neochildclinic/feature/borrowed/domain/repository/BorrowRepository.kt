package com.neochildclinic.feature.borrowed.domain.repository

import com.neochildclinic.domain.model.BorrowReturnRecord
import com.neochildclinic.domain.model.BorrowedVaccine
import kotlinx.coroutines.flow.Flow

interface BorrowRepository {
    fun getActiveBorrowedRecords(): Flow<List<BorrowedVaccine>>

    /** Tile count for the dashboard; COUNT in SQL, same predicate as the list. */
    fun getActiveBorrowCount(): Flow<Int>
    fun getReturnedRecords(): Flow<List<BorrowedVaccine>>
    fun getReturnRecords(): Flow<List<BorrowReturnRecord>>
    suspend fun saveBorrowedItem(item: BorrowedVaccine)
    suspend fun deleteBorrowedItem(id: String)
    suspend fun submitReturn(
        borrowRecordId: String,
        originalBatchId: String,
        vaccineId: String,
        vaccineName: String,
        quantity: Int,
        batchId: String,
        notes: String?,
        newBatchInfo: NewBatchInfo? = null
    )
    suspend fun refreshBorrows()
}

data class NewBatchInfo(
    val batchNumber: String,
    val expiryDate: String,
    val purchaseCost: Double = 0.0,
    val sellingPrice: Double = 0.0
)