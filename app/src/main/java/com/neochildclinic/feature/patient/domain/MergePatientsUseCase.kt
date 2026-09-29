package com.neochildclinic.feature.patient.domain

import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.feature.patient.domain.repository.PatientRepository
import com.neochildclinic.feature.vaccination.domain.repository.VaccinationRepository
import com.neochildclinic.feature.reminder.domain.repository.ReminderRepository
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Business Logic to merge duplicate patient records.
 * Ensures data integrity across Patients, Vaccinations, Reminders, and Audit logs.
 */
class MergePatientsUseCase @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val patientRepository: PatientRepository,
    private val vaccinationRepository: VaccinationRepository,
    private val reminderRepository: ReminderRepository,
    private val inventoryRepository: InventoryRepository,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger
) {
    suspend operator fun invoke(masterId: String, duplicateIds: List<String>) {
        if (masterId.isBlank() || duplicateIds.isEmpty()) return

        transactionRunner.run {
            for (dupId in duplicateIds) {
                // Fetch record IDs before moving them so we can queue sync properly
                val vaccIds = vaccinationRepository.getVaccinationsForPatient(dupId).first().map { it.id }
                val reminderIds = reminderRepository.getPatientReminders(dupId).first().map { it.id }
                val auditIds = reminderRepository.getAuditTrail(dupId).first().map { it.auditId }

                // 1. Move all vaccinations from duplicate to master
                vaccinationRepository.transferVaccinations(dupId, masterId)

                // 2. Move all reminders and audits
                reminderRepository.transferReminders(dupId, masterId)

                // 3. Move inventory transactions
                inventoryRepository.transferPatientTransactions(dupId, masterId)

                // 4. Create an audit log for the merge
                auditLogger.log(
                    module = "PATIENT",
                    entityType = "PATIENT",
                    entityId = masterId,
                    action = "MERGE_PATIENT",
                    patientId = masterId,
                    remarks = "Merged with duplicate patient ID: $dupId"
                )

                // 5. Delete duplicate patient locally (this also queues sync)
                patientRepository.deletePatient(dupId)

                // 6. Queue sync for moved records
                vaccIds.forEach { id ->
                    syncRepository.enqueue("VACCINATION", id, SyncOperation.UPDATE, SyncPriority.MEDIUM)
                }
                reminderIds.forEach { id ->
                    syncRepository.enqueue("REMINDERS", id, SyncOperation.UPDATE, SyncPriority.LOW)
                }
                auditIds.forEach { id ->
                    syncRepository.enqueue("REMINDER_AUDIT", id.toString(), SyncOperation.UPDATE, SyncPriority.LOW)
                }
            }
            
            // Queue sync for the master patient
            syncRepository.enqueue(
                entityName = "PATIENT",
                entityId = masterId,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM
            )
        }
    }
}

