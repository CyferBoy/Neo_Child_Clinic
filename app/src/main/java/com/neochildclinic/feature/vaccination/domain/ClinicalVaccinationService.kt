package com.neochildclinic.feature.vaccination.domain
import com.neochildclinic.core.common.VaccineUtils
import com.neochildclinic.core.common.DateUtils

import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Visit
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.model.InventoryStatus
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.model.InventoryDeduction
import com.neochildclinic.feature.inventory.domain.InsufficientStockException
import com.neochildclinic.feature.inventory.domain.StockDeductionBlockedException
import com.neochildclinic.feature.finance.domain.FinanceCalculator
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClinicalVaccinationService @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val vaccinationRepository: VaccinationRepository,
    private val financeRepository: FinanceRepository,
    private val reminderRepository: ReminderRepository,
    private val syncRepository: SyncRepository,
    private val inventoryRepository: com.neochildclinic.domain.repository.InventoryRepository
) {
    /**
     * [reminderSpecs] are the visit's next-vaccination reminders. They are written INSIDE
     * this transaction, not by the caller afterwards.
     *
     * They used to be written by AddVaccinationViewModel in separate transactions after this
     * one had already committed the visit AND its stock deduction. A failure there left the
     * clinic with a saved visit but no reminder, and the UI reported total failure - so staff
     * pressed Save again. The retry minted a new vaccinationId while reusing the same
     * `vaccination_items` primary keys, and because that table is
     * OnConflictStrategy.REPLACE, visit #2's items re-pointed away from visit #1. Net: two
     * visits, one set of item rows, and two stock deductions (NCC-004).
     *
     * The edit path already reconciles reminders inside VaccinationEditEngine's transaction;
     * this makes create symmetric with it.
     */
    suspend fun recordVaccination(
        vaccination: Vaccination,
        user: String,
        isNew: Boolean = true,
        reminderSpecs: List<VaccinationEditEngine.ReminderSpec> = emptyList()
    ) {
        val transactionGroupId = UUID.randomUUID().toString()
        transactionRunner.run {
            vaccinationRepository.addVaccination(vaccination, transactionGroupId)

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
                        remarks = FinanceCalculator.buildVaccinationRemarks(vaccination),
                        recordedBy = user,
                        transactionGroupId = transactionGroupId
                    )
            } else {
                financeRepository.updateIncomeForVisit(
                    visitId = vaccination.id,
                    amount = vaccination.totalPaid,
                    cashAmount = vaccination.cashAmount,
                    onlineAmount = vaccination.onlineAmount,
                    remarks = FinanceCalculator.buildVaccinationRemarks(vaccination),
                    recordedBy = user,
                    transactionGroupId = transactionGroupId
                )
            }

            // reminder rows for this visit/vaccine directly. No legacy requirement object or
            // automatic reminder-engine calculation is involved.
            if (isNew) satisfyRelatedReminders(vaccination, user)

            // Editing an existing record already deducts/reverses stock via
            // VaccinationEditEngine's inventoryDiff; this covers the creation path, which
            // previously had no inventory logic at all - a new vaccination never actually
            // touched stock or wrote an inventory_deductions audit row. Deduction failures
            // (e.g. insufficient stock) are recorded, not thrown - the clinical record of a
            // vaccination that already happened must still be saved; inventory gets flagged
            // FAILED/PARTIAL for staff to reconcile manually rather than blocking the visit.
            if (isNew) deductInventoryForNewVaccination(vaccination, user)

            // stock movement. Either the whole record lands or none of it does.
            reminderSpecs.forEach { spec ->
                reminderRepository.saveNextVaccination(
                    patientId = vaccination.patientId,
                    originalVisitId = vaccination.id,
                    type = spec.type,
                    vaccineNames = spec.vaccineNames,
                    nxtVaccineId = spec.vaccineIds,
                    dueDate = spec.dueDate,
                    notes = spec.notes,
                    performedBy = user
                )
            }
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
                    patientId = vaccination.patientId,
                    givenDate = vaccination.dateGiven
                )
                inventoryRepository.insertInventoryDeduction(InventoryDeduction(
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: StockDeductionBlockedException) {
                // Only a PRE-WRITE rejection is recoverable: the clinical record of a
                // vaccination that already happened must still be saved, with stock flagged
                // for staff to reconcile. StockDeductionBlockedException is thrown only when
                // nothing has been written yet - short shelf, missing batch, batch expired on
                // the given date. Any OTHER failure here (a WorkManager rejection, a full disk)
                // can arrive AFTER deductStockFromBatch already moved the stock, and recording
                // that as FAILED committed a deducted dose with a FAILED ledger row that
                // neither deleteVaccination nor VaccinationEditEngine will ever restore - the
                // dose was lost. Let those propagate so the transaction rolls back.
                inventoryRepository.insertInventoryDeduction(InventoryDeduction(
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
        vaccinationRepository.updateVisitInventoryStatus(vaccination.id, finalStatus.name)

        // Without this, the status change stays local-only until this visit's next
        // unrelated edit - other devices pulling this visit down in the meantime would
        // still see it as PENDING and could attempt to reconcile/deduct it again.
        syncRepository.enqueue(
            entityName = "VACCINATION",
            entityId = vaccination.id,
            operation = com.neochildclinic.domain.model.SyncOperation.UPDATE,
            priority = com.neochildclinic.domain.model.SyncPriority.LOW
        )
    }

    private suspend fun satisfyRelatedReminders(vaccination: Vaccination, user: String) {
        val reminders = reminderRepository.getPatientReminders(vaccination.patientId).first()
        val givenNames = vaccination.items.map {
            VaccineUtils.cleanVaccineName(it.vaccineName).lowercase().trim()
        }
        // A dose given today must not close a reminder for the same vaccine that is not due
        // until later. Unparseable dates (including a blank visit date) complete nothing.
        val visitDate = DateUtils.parseDate(vaccination.dateGiven)
        reminders.forEach { reminder ->
            val reminderNames = reminder.vaccineName.split(",").map {
                VaccineUtils.cleanVaccineName(it).lowercase().trim()
            }.filter { it.isNotBlank() }
            val matches = reminderNames.isNotEmpty() && reminderNames.any { it in givenNames }
            val dueDate = DateUtils.parseDate(reminder.dueDate)
            val dueByVisit = visitDate != null && dueDate != null && dueDate <= visitDate
            if (matches && dueByVisit && reminder.status != "COMPLETED" && reminder.status != "DISMISSED") {
                reminderRepository.markReminderCompleted(reminder, user, vaccination.id)
            }
        }
    }

}


