package com.neochildclinic.domain.service

import androidx.room.withTransaction
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.model.InventoryStatus
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import com.neochildclinic.features.statistics.FinanceCalculator
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.utils.PatientUtils
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClinicalVaccinationService @Inject constructor(
    private val database: AppDatabase,
    private val vaccinationRepository: VaccinationRepository,
    private val consultationRepository: ConsultationRepository,
    private val financeRepository: FinanceRepository,
    private val reminderRepository: ReminderRepository,
    private val syncRepository: SyncRepository,
    private val inventoryRepository: com.neochildclinic.domain.repository.InventoryRepository
) {
    suspend fun recordVaccination(
        vaccination: Vaccination,
        user: String,
        isNew: Boolean = true
    ) {
        val transactionGroupId = UUID.randomUUID().toString()
        database.withTransaction {
            // 1. Add/Update Vaccination Record
            vaccinationRepository.addVaccination(vaccination, transactionGroupId)

            // 2. Create the finance transaction only for a new vaccination.
            // Editing must update the existing transaction for this visit instead of
            // creating a second income record.
            if (isNew) {
                // Record every vaccination in the finance ledger, including free
                // vaccinations, so vaccine COGS remains historically reportable.
                financeRepository.recordIncome(
                        amount = vaccination.totalPaid.coerceAtLeast(0.0),
                        cashAmount = vaccination.cashAmount,
                        onlineAmount = vaccination.onlineAmount,
                        category = "VACCINATION",
                        patientId = vaccination.patientId,
                        visitId = vaccination.id,
                        remarks = FinanceCalculator.buildVaccinationRemarks(
                            vaccination.items.joinToString(", ") { it.vaccineName },
                            vaccination.items.sumOf { it.netRate.coerceAtLeast(0.0) * it.quantity.coerceAtLeast(0) }
                        ),
                        recordedBy = user,
                        transactionGroupId = transactionGroupId
                    )
            } else {
                financeRepository.updateIncomeForVisit(
                    visitId = vaccination.id,
                    amount = vaccination.totalPaid,
                    cashAmount = vaccination.cashAmount,
                    onlineAmount = vaccination.onlineAmount,
                    remarks = FinanceCalculator.buildVaccinationRemarks(
                            vaccination.items.joinToString(", ") { it.vaccineName },
                            vaccination.items.sumOf { it.netRate.coerceAtLeast(0.0) * it.quantity.coerceAtLeast(0) }
                        ),
                    recordedBy = user,
                    transactionGroupId = transactionGroupId
                )
            }

            // 3. If this is a newly administered vaccination, complete any existing
            // reminder rows for this visit/vaccine directly. No legacy requirement object or
            // automatic reminder-engine calculation is involved.
            if (isNew) satisfyRelatedReminders(vaccination, user)

            // 4. Inventory Domain - deduct stock for a newly administered vaccination.
            // Editing an existing record already deducts/reverses stock via
            // VaccinationEditEngine's inventoryDiff; this covers the creation path, which
            // previously had no inventory logic at all - a new vaccination never actually
            // touched stock or wrote an inventory_deductions audit row. Deduction failures
            // (e.g. insufficient stock) are recorded, not thrown - the clinical record of a
            // vaccination that already happened must still be saved; inventory gets flagged
            // FAILED/PARTIAL for staff to reconcile manually rather than blocking the visit.
            if (isNew) deductInventoryForNewVaccination(vaccination, user)
        }
    }

    private suspend fun deductInventoryForNewVaccination(vaccination: Vaccination, user: String) {
        if (vaccination.items.isEmpty()) return

        var completedCount = 0
        for (item in vaccination.items) {
            try {
                inventoryRepository.deductStockFromBatch(
                    batchId = item.batchId,
                    quantity = item.quantity,
                    user = user,
                    transactionType = InventoryTransactionType.VACCINATION,
                    visitId = vaccination.id,
                    patientId = vaccination.patientId
                )
                database.inventoryDeductionDao().insert(InventoryDeductionEntity(
                    vaccinationId = vaccination.id,
                    vaccineId = item.vaccineId,
                    vaccineName = item.vaccineName,
                    batchId = item.batchId,
                    quantity = item.quantity,
                    status = "COMPLETED",
                    errorMessage = null,
                    resolvedAt = System.currentTimeMillis()
                ))
                completedCount++
            } catch (e: Exception) {
                database.inventoryDeductionDao().insert(InventoryDeductionEntity(
                    vaccinationId = vaccination.id,
                    vaccineId = item.vaccineId,
                    vaccineName = item.vaccineName,
                    batchId = item.batchId,
                    quantity = item.quantity,
                    status = "FAILED",
                    errorMessage = e.message,
                    resolvedAt = System.currentTimeMillis()
                ))
            }
        }

        val finalStatus = when {
            completedCount == vaccination.items.size -> InventoryStatus.COMPLETED
            completedCount > 0 -> InventoryStatus.PARTIAL
            else -> InventoryStatus.FAILED
        }
        database.vaccinationDao().updateInventoryStatus(vaccination.id, finalStatus.name)

        // Without this, the status change stays local-only until this visit's next
        // unrelated edit - other devices pulling this visit down in the meantime would
        // still see it as PENDING and could attempt to reconcile/deduct it again.
        syncRepository.enqueue(
            entityName = "VACCINATION",
            entityId = vaccination.id,
            operation = com.neochildclinic.core.model.SyncOperation.UPDATE,
            priority = com.neochildclinic.core.model.SyncPriority.LOW
        )
    }

    suspend fun recordConsultation(
        consultation: Consultation,
        user: String
    ) {
        val transactionGroupId = UUID.randomUUID().toString()
        database.withTransaction {
            val visitId = if (consultation.visitId.isBlank()) UUID.randomUUID().toString() else consultation.visitId
            
            // 1. Create Visit Header
            val visit = VisitEntity(
                id = visitId,
                patientId = consultation.patientId,
                dateGiven = consultation.date,
                doctorId = consultation.doctorId,
                doctor = consultation.doctorName,
                notes = consultation.problem,
                visitType = "CONSULTATION",
                cashAmount = consultation.cashAmount,
                onlineAmount = consultation.onlineAmount,
                totalPaid = consultation.amount,
                updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                isSynced = false
            )
            database.vaccinationDao().insertVaccination(visit)
            
            // Sync visit header
            syncRepository.enqueue(
                entityName = "VISIT",
                entityId = visitId,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.HIGH,
                transactionGroupId = transactionGroupId
            )
            
            // 2. Create Consultation Record
            val finalConsultation = consultation.copy(visitId = visitId)
            consultationRepository.addConsultation(finalConsultation, transactionGroupId)
            
            // 3. Record Financial Transaction
            financeRepository.recordIncome(
                amount = consultation.amount,
                cashAmount = consultation.cashAmount,
                onlineAmount = consultation.onlineAmount,
                category = "CONSULTATION",
                patientId = consultation.patientId,
                visitId = visitId,
                remarks = "Consultation: ${consultation.problem} [CONSULTATION_ID:${finalConsultation.id}]",
                recordedBy = user,
                transactionGroupId = transactionGroupId
            )
        }
    }

    private suspend fun satisfyRelatedReminders(vaccination: Vaccination, user: String) {
        val reminders = reminderRepository.getRemindersByVisitId(vaccination.id)
        val givenNames = vaccination.items.map {
            PatientUtils.cleanVaccineName(it.vaccineName).lowercase().trim()
        }
        reminders.forEach { reminder ->
            val reminderNames = reminder.vaccineName.split(", ").map {
                PatientUtils.cleanVaccineName(it).lowercase().trim()
            }.filter { it.isNotBlank() }
            val matches = reminderNames.isEmpty() || reminderNames.any { it in givenNames }
            if (matches && reminder.status != "COMPLETED" && reminder.status != "DISMISSED") {
                reminderRepository.markReminderCompleted(reminder, user, vaccination.id)
            }
        }
    }

}
