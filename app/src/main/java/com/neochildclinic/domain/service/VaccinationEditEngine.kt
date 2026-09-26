package com.neochildclinic.domain.service

import androidx.room.withTransaction
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single edit transaction coordinator for Vaccination.
 *
 * It compares the persisted vaccination with the edited vaccination and only applies
 * side effects when the corresponding part of the record actually changed.
 */
@Singleton
class VaccinationEditEngine @Inject constructor(
    private val database: AppDatabase,
    private val vaccinationRepository: VaccinationRepository,
    private val inventoryRepository: InventoryRepository,
    private val financeRepository: FinanceRepository,
    private val reminderRepository: ReminderRepository
) {
    data class ReminderSpec(
        val reminderId: String? = null,
        val type: String,
        val vaccineNames: List<String>,
        val vaccineIds: List<String>,
        val dueDate: String,
        val notes: String
    )

    suspend fun execute(
        original: Vaccination,
        updated: Vaccination,
        user: String,
        reminderSpecs: List<ReminderSpec>,
        excludedReminderIds: Set<String> = emptySet()
    ) {
        require(original.id == updated.id) { "Vaccination edit requires the same vaccination ID." }

        val transactionGroupId = UUID.randomUUID().toString()

        database.withTransaction {
            val inventoryChanged = inventoryDiff(original, updated).isNotEmpty()
            val financeChanged = financeChanged(original, updated)

            // Item-level reconciliation (EditReconciler): unchanged vaccination_items keep
            // their rows/ids, changed rows are DELETE+CREATE under a new id, removed rows
            // are deleted, added rows are created. Parent visit id is untouched.
            val itemsChanged = vaccinationRepository.addVaccination(updated, transactionGroupId)

            if (financeChanged) {
                financeRepository.updateIncomeForVisit(
                    visitId = updated.id,
                    amount = updated.totalPaid,
                    cashAmount = updated.cashAmount,
                    onlineAmount = updated.onlineAmount,
                    remarks = com.neochildclinic.domain.statistics.FinanceCalculator.buildVaccinationRemarks(updated),
                    recordedBy = user,
                    transactionGroupId = transactionGroupId
                )
            }

            if (inventoryChanged) {
                applyInventoryDiff(original, updated, user)
            }
            // Local-only deduction audit: rebuild whenever the item set moved at all so
            // no stale row (e.g. an old vaccine name) is left behind - but never as part
            // of a no-op save.
            if (inventoryChanged || itemsChanged) {
                reconcileInventoryDeductions(updated)
            }

            reconcileReminders(original.patientId, original.id, reminderSpecs, user, excludedReminderIds)
        }
    }

    private fun financeChanged(old: Vaccination, new: Vaccination): Boolean {
        return old.totalPaid != new.totalPaid ||
            old.cashAmount != new.cashAmount ||
            old.onlineAmount != new.onlineAmount ||
            old.items.map { Triple(it.vaccineId, it.quantity, it.netRate) }.sortedBy { it.first } !=
                new.items.map { Triple(it.vaccineId, it.quantity, it.netRate) }.sortedBy { it.first }
    }

    private fun inventoryDiff(
        old: Vaccination,
        new: Vaccination
    ): Map<String, Int> {
        val oldByBatch = old.items.groupingBy { it.batchId }.fold(0) { total, item -> total + item.quantity.coerceAtLeast(0) }
        val newByBatch = new.items.groupingBy { it.batchId }.fold(0) { total, item -> total + item.quantity.coerceAtLeast(0) }

        return (oldByBatch.keys + newByBatch.keys)
            .associateWith { batchId -> (newByBatch[batchId] ?: 0) - (oldByBatch[batchId] ?: 0) }
            .filterValues { it != 0 }
    }

    private suspend fun applyInventoryDiff(old: Vaccination, new: Vaccination, user: String) {
        inventoryDiff(old, new).forEach { (batchId, delta) ->
            if (delta > 0) {
                // A batch already recorded on the original visit was valid when first
                // administered; don't block an edit (e.g. a quantity bump) just because
                // the batch has since expired. Only brand-new batch selections must pass
                // the expiry check.
                val batchAlreadyUsed = old.items.any { it.batchId == batchId }
                inventoryRepository.deductStockFromBatch(
                    batchId = batchId,
                    quantity = delta,
                    user = user,
                    transactionType = InventoryTransactionType.VACCINATION,
                    visitId = new.id,
                    patientId = new.patientId,
                    allowExpired = batchAlreadyUsed,
                    givenDate = new.dateGiven
                )
            } else {
                inventoryRepository.reverseDeduction(
                    batchId = batchId,
                    quantity = -delta,
                    user = user,
                    visitId = new.id,
                    patientId = new.patientId
                )
            }
        }
    }

    // inventory_deductions is a local-only audit of what a visit took out of stock
    // (see the create path in ClinicalVaccinationService). Edits never touched it, so a
    // swapped-out batch left its old COMPLETED row behind and the patient's inventory
    // dialog kept showing both the old and the new vaccine. Recreate the set from the
    // final items whenever stock actually changed, mirroring the create-path rows.
    private suspend fun reconcileInventoryDeductions(vaccination: Vaccination) {
        inventoryRepository.deleteInventoryDeductionsForVaccination(vaccination.id)
        vaccination.items.forEach { item ->
            inventoryRepository.insertInventoryDeduction(InventoryDeductionEntity(
                vaccinationId = vaccination.id,
                vaccineId = item.vaccineId,
                vaccineName = item.vaccineName,
                batchId = item.batchId,
                quantity = item.quantity,
                status = "COMPLETED",
                errorMessage = null,
                resolvedAt = System.currentTimeMillis()
            ))
        }
    }

    private suspend fun reconcileReminders(
        patientId: String,
        visitId: String,
        desired: List<ReminderSpec>,
        user: String,
        excludedReminderIds: Set<String> = emptySet()
    ) {
        val existing = reminderRepository.getRemindersByVisitId(visitId)

        val desiredRows = desired.flatMap { spec ->
            val names = spec.vaccineNames
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val ids = spec.vaccineIds
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (names.isEmpty()) {
                listOf(
                    EditReconciler.ReminderRow(
                        reminderId = spec.reminderId,
                        type = spec.type,
                        vaccineName = "",
                        vaccineId = null,
                        dueDate = spec.dueDate,
                        notes = spec.notes
                    )
                )
            } else {
                names.mapIndexedNotNull { index, name ->
                    EditReconciler.ReminderRow(
                        reminderId = spec.reminderId,
                        type = spec.type,
                        vaccineName = name,
                        vaccineId = ids.getOrNull(index),
                        dueDate = spec.dueDate,
                        notes = spec.notes
                    )
                }
            }
        }

        // Item-level plan: unchanged reminders are left completely alone (same row, same
        // id, no sync op); changed ones are DELETE+CREATE with a fresh id; removed ones
        // are deleted; added ones are created. Rows cancelled this session
        // (excludedReminderIds) were already soft-dismissed and are never hard-deleted.
        val plan = EditReconciler.classifyReminders(existing, desiredRows, excludedReminderIds)

        // Deletes first: the unique (visit, dueDate, type, vaccineName) index must be
        // free before a replacement row with a colliding key is inserted.
        plan.delete.forEach { reminder ->
            reminderRepository.deleteReminder(reminder, user)
        }

        plan.create.forEach { row ->
            // The paired old row (if any) was deleted above, so a unique-event hit here
            // can only mean a genuine duplicate event - saveNextVaccination reuses that
            // row instead of hard-deleting an unrelated kept reminder.
            reminderRepository.saveNextVaccination(
                patientId = patientId,
                originalVisitId = visitId,
                type = row.type,
                vaccineNames = if (row.vaccineName.isBlank()) emptyList() else listOf(row.vaccineName),
                nxtVaccineId = row.vaccineId?.let { listOf(it) } ?: emptyList(),
                dueDate = row.dueDate,
                notes = row.notes,
                performedBy = user
            )
        }
    }

}

