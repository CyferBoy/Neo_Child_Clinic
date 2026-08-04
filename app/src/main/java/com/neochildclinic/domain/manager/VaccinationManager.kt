package com.neochildclinic.domain.manager

import androidx.room.withTransaction
import com.neochildclinic.core.utils.InventoryUtils
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.dao.VaccineDao
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.VaccinationItem
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.PendingRequirement
import com.neochildclinic.domain.repository.*
import com.neochildclinic.domain.service.ClinicalVaccinationService
import com.neochildclinic.domain.service.InventoryProcessingService
import kotlinx.coroutines.flow.first
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single point of truth for the Vaccination business process.
 * Orchestrates between Clinical and Inventory domains.
 */
@Singleton
class VaccinationManager @Inject constructor(
    private val database: AppDatabase,
    private val clinicalService: ClinicalVaccinationService,
    private val inventoryService: InventoryProcessingService,
    private val vaccinationRepository: VaccinationRepository,
    private val vaccinationDao: VaccinationDao,
    private val vaccineDao: VaccineDao,
    private val inventoryRepository: InventoryRepository
) {
    /**
     * Completes a vaccination event with explicit parameters.
     * Orchestrates clinical record saving and inventory management atomically.
     */
    suspend fun completeVaccination(
        vaccination: Vaccination,
        user: String,
        isNew: Boolean = true,
        selectedVaccineIds: List<String> = emptyList(),
        requirement: PendingRequirement? = null,
        selectedBatchIds: List<String> = emptyList()
    ): String? {
        return database.withTransaction {
            val oldRecord = if (!isNew) vaccinationDao.getVaccinationById(vaccination.id) else null
            
            // 1. Clinical Domain Save
            clinicalService.recordVaccination(
                vaccination = vaccination,
                user = user,
                isNew = isNew,
                requirement = requirement
            )

            // 2. Inventory Management Logic
            if (isNew) {
                // For new records, simply deduct stock
                inventoryService.processVaccinationInventory(
                    vaccinationId = vaccination.id,
                    patientId = vaccination.patientId,
                    vaccineIds = selectedVaccineIds,
                    batchIds = selectedBatchIds,
                    user = user
                )
            } else if (oldRecord != null) {
                // For edited records, check if vaccines changed
                val oldBatchIds = oldRecord.batchIds.split(",").filter { it.isNotBlank() }
                
                // Compare batches to determine if stock needs to be adjusted
                // If the selected batches are different, we perform a swap
                if (oldBatchIds.sorted() != selectedBatchIds.sorted()) {
                    // Return old stock
                    for (oldBatchId in oldBatchIds) {
                        inventoryRepository.reverseDeduction(oldBatchId, 1, user)
                    }
                    
                    // Deduct new stock
                    inventoryService.processVaccinationInventory(
                        vaccinationId = vaccination.id,
                        patientId = vaccination.patientId,
                        vaccineIds = selectedVaccineIds,
                        batchIds = selectedBatchIds,
                        user = user
                    )
                }
            }
            null // Return null on success
        }
    }

    /**
     * Specialized flow to complete a vaccination directly from a pending requirement.
     * Automatically handles inventory mapping and record creation.
     */
    suspend fun completeFromRequirement(
        requirement: PendingRequirement,
        user: String,
        notes: String = ""
    ) {
        val vaccination = Vaccination(
            id = UUID.randomUUID().toString(),
            patientId = requirement.patientId,
            items = listOf(VaccinationItem(vaccineName = requirement.vaccineName)),
            dateGiven = PatientUtils.formatDate(Date()),
            status = ReminderStatus.COMPLETED,
            performedBy = user,
            notes = notes
        )

        // Automated Inventory Mapping
        val vaccines = vaccineDao.getAllVaccines().first()
        val matchingVaccine = vaccines.find { 
            it.brandName.contains(requirement.vaccineName, ignoreCase = true) 
        }
        
        val matchingVaccineId = matchingVaccine?.id
        val selectedIds = matchingVaccineId?.let { listOf(it) } ?: emptyList()

        // Attempt to find FEFO batch to record expiry date even for automated completions
        var enrichedVaccination = vaccination
        val selectedBatchIds = mutableListOf<String>()
        
        if (matchingVaccineId != null) {
            val activeBatches = vaccineDao.getActiveBatchesByExpiry(matchingVaccineId)
            val firstBatch = activeBatches.firstOrNull { it.remainingQuantity > 0 && !InventoryUtils.isExpired(it.expiryDate) }
            if (firstBatch != null) {
                enrichedVaccination = vaccination.copy(
                    items = listOf(
                        VaccinationItem(
                            vaccineName = requirement.vaccineName,
                            batchId = firstBatch.batchId,
                            batchNumber = firstBatch.batchNumber,
                            expiryDate = firstBatch.expiryDate
                        )
                    )
                )
                selectedBatchIds.add(firstBatch.batchId)
            }
        }

        completeVaccination(
            vaccination = enrichedVaccination,
            user = user,
            isNew = true,
            selectedVaccineIds = selectedIds,
            requirement = requirement,
            selectedBatchIds = selectedBatchIds
        )
    }

    /**
     * Marks an existing vaccination record as completed and satisfies related reminders.
     */
    suspend fun satisfyExistingVaccination(
        vaccinationId: String,
        user: String
    ) {
        vaccinationRepository.markAsDone(vaccinationId)
    }
}
