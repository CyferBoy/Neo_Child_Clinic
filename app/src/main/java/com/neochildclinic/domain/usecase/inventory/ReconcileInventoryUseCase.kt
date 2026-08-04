package com.neochildclinic.domain.usecase.inventory

import com.neochildclinic.data.local.dao.InventoryDeductionDao
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.dao.VaccineDao
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import com.neochildclinic.domain.model.InventoryStatus
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.repository.InventoryRepository
import javax.inject.Inject

data class ReconcileResult(
    val vaccinationId: String, 
    val vaccineName: String, 
    val success: Boolean, 
    val message: String
)

class ReconcileInventoryUseCase @Inject constructor(
    private val vaccinationDao: VaccinationDao,
    private val vaccineDao: VaccineDao,
    private val inventoryRepository: InventoryRepository,
    private val inventoryDeductionDao: InventoryDeductionDao,
    private val database: AppDatabase
) {
    suspend fun execute(user: String): List<ReconcileResult> {
        val totalResults = mutableListOf<ReconcileResult>()
        
        // 1. Get all vaccinations pending reconciliation
        val pendingVisits = vaccinationDao.getVaccinationsPendingReconciliation()
        
        for (visit in pendingVisits) {
            val vaccinationId = visit.id
            val vaccineIds = if (visit.vaccineIds.isBlank()) emptyList() else visit.vaccineIds.split(",")
            val vaccineNames = if (visit.vaccineNames.isBlank()) emptyList() else visit.vaccineNames.split(",")
            val batchNumbers = if (visit.batchIds.isBlank()) emptyList() else visit.batchIds.split(",")
            
            val existingDeductions = inventoryDeductionDao.getForVaccination(vaccinationId)
            var completedCount = 0
            
            for (i in vaccineIds.indices) {
                val vaccineId = vaccineIds[i]
                val vaccineName = vaccineNames.getOrNull(i) ?: "Unknown"
                val batchNumber = batchNumbers.getOrNull(i)
                
                // Idempotency check: Skip if already successfully deducted
                if (existingDeductions.any { it.vaccineId == vaccineId && it.status == "COMPLETED" }) {
                    completedCount++
                    continue
                }
                
                try {
                    // Try to resolve batch by vaccine + number
                    val resolvedBatch = if (!batchNumber.isNullOrBlank()) {
                        vaccineDao.getBatchByVaccineAndNumber(vaccineId, batchNumber)
                    } else null
                    
                    if (resolvedBatch != null) {
                        inventoryRepository.deductStockFromBatch(
                            batchId = resolvedBatch.batchId,
                            quantity = 1,
                            user = user,
                            transactionType = InventoryTransactionType.VACCINATION
                        )
                    } else {
                        // Fallback to FEFO logic inside deductStock
                        inventoryRepository.deductStock(
                            vaccineId = vaccineId,
                            quantity = 1,
                            user = user,
                            transactionType = InventoryTransactionType.VACCINATION,
                            vaccinationId = vaccinationId,
                            patientId = visit.patientId
                        )
                    }
                    
                    // On success: insert COMPLETED deduction record
                    inventoryDeductionDao.insert(InventoryDeductionEntity(
                        vaccinationId = vaccinationId,
                        vaccineId = vaccineId,
                        vaccineName = vaccineName,
                        batchId = resolvedBatch?.batchId, // if FEFO was used, we don't necessarily know which batch was picked here without more refactor, but it's okay for audit
                        quantity = 1,
                        status = "COMPLETED",
                        errorMessage = null,
                        resolvedAt = System.currentTimeMillis()
                    ))
                    completedCount++
                    totalResults.add(ReconcileResult(vaccinationId, vaccineName, true, "Deducted successfully"))
                    
                } catch (e: Exception) {
                    // On failure: insert FAILED deduction record
                    inventoryDeductionDao.insert(InventoryDeductionEntity(
                        vaccinationId = vaccinationId,
                        vaccineId = vaccineId,
                        vaccineName = vaccineName,
                        batchId = null,
                        quantity = 1,
                        status = "FAILED",
                        errorMessage = e.message,
                        resolvedAt = System.currentTimeMillis()
                    ))
                    totalResults.add(ReconcileResult(vaccinationId, vaccineName, false, e.message ?: "Deduction failed"))
                }
            }
            
            // Determine overall status for this vaccination
            val finalStatus = when {
                completedCount == vaccineIds.size -> InventoryStatus.COMPLETED
                completedCount > 0 -> InventoryStatus.PARTIAL
                else -> InventoryStatus.FAILED
            }
            
            vaccinationDao.updateInventoryStatus(vaccinationId, finalStatus.name)
        }
        
        return totalResults
    }
}
