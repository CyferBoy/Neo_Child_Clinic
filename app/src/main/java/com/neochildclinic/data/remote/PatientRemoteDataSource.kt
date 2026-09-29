package com.neochildclinic.data.remote

import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.InventoryTransactionEntity

interface PatientRemoteDataSource {
    suspend fun fetchAllPatients(): List<PatientEntity>
    suspend fun fetchPatientById(id: String): PatientEntity?
    suspend fun getStockHistoryPage(
        vaccineId: String?,
        batchId: String?,
        types: List<String>,
        typesEmpty: Boolean,
        fromDateIso: String?,
        toDateIso: String?,
        limit: Int,
        offset: Int
    ): List<InventoryTransactionEntity>
}
