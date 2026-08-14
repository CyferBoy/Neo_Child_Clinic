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
        selectedBatchIds: List<String> = emptyList()
    ): String? {
        return database.withTransaction {
            val oldRecord = if (!isNew) vaccinationDao.getVaccinationById(vaccination.id) else null
            
            // 1. Clinical Domain Save
            clinicalService.recordVaccination(
                vaccination = vaccination,
                user = user,
                isNew = isNew
            )

            // 2. Inventory Management Logic
            if (isNew) {
                // For new records, simply deduct stock
                inventoryService.processVaccinationInventory(
                    visitId = vaccination.id,
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
                        visitId = vaccination.id,
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
     * Marks an existing vaccination record as completed and satisfies related reminders.
     */
    suspend fun satisfyExistingVaccination(
        vaccinationId: String,
        user: String
    ) {
        vaccinationRepository.markAsDone(vaccinationId)
    }
}
