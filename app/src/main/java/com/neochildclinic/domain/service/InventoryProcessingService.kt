package com.neochildclinic.domain.service

import androidx.room.withTransaction
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.domain.model.InventoryStatus
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryProcessingService @Inject constructor(
    private val database: AppDatabase,
    private val inventoryRepository: InventoryRepository,
    private val vaccinationDao: VaccinationDao,
    private val syncRepository: SyncRepository
) {
    suspend fun processVaccinationInventory(
        visitId: String,
        patientId: String,
        vaccineIds: List<String>,
        batchIds: List<String>,
        user: String
    ): String? {
        try {
            if (batchIds.isNotEmpty()) {
                batchIds.forEach { batchId ->
                    inventoryRepository.deductStockFromBatch(
                        batchId = batchId,
                        quantity = 1,
                        user = user,
                        transactionType = InventoryTransactionType.VACCINATION
                    )
                }
            } else {
                vaccineIds.forEach { vaccineId ->
                    inventoryRepository.deductStock(
                        vaccineId = vaccineId,
                        quantity = 1,
                        user = user,
                        transactionType = InventoryTransactionType.VACCINATION,
                        visitId = visitId,
                        patientId = patientId
                    )
                }
            }
            
            // If we reach here, deduction succeeded
            database.withTransaction {
                vaccinationDao.updateInventoryStatus(visitId, InventoryStatus.COMPLETED.name)
            }
            return null
        } catch (e: Exception) {
            database.withTransaction {
                vaccinationDao.updateInventoryStatus(visitId, InventoryStatus.FAILED.name)
                // Record the failure in a transaction (already handled by deductStock if it got partially through, 
                // but since we want clinical data to be safe, we mark it here)
            }
            return "Inventory could not be updated: ${e.message}"
        }
    }

    suspend fun retryDeduction(
        visitId: String,
        patientId: String,
        vaccineIds: List<String>,
        user: String
    ): String? {
        return processVaccinationInventory(visitId, patientId, vaccineIds, emptyList(), user)
    }

    suspend fun resolveManual(
        visitId: String,
        batchIds: List<String>,
        user: String
    ): String? {
        return processVaccinationInventory(visitId, "", emptyList(), batchIds, user)
    }
}
