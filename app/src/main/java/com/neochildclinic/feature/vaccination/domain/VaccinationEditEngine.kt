package com.neochildclinic.feature.vaccination.domain

import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.finance.domain.repository.FinanceRepository
import com.neochildclinic.feature.inventory.domain.InsufficientStockException
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.feature.reminder.domain.repository.ReminderRepository
import com.neochildclinic.feature.vaccination.domain.repository.VaccinationRepository
import com.neochildclinic.domain.model.InventoryDeduction
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-batch quantity diff between two versions of a visit, keyed by batchId:
 * negative = stock to reverse, positive = stock to deduct. Pure so it can be
 * unit-tested without Room/Android (see VaccinationEditEngineTest).
 */
fun inventoryDiff(
    old: Vaccination,
    new: Vaccination
): Map<String, Int> {
    val oldByBatch = old.items.groupingBy { it.batchId }.fold(0) { total, item -> total + item.quantity.coerceAtLeast(0) }
    val newByBatch = new.items.groupingBy { it.batchId }.fold(0) { total, item -> total + item.quantity.coerceAtLeast(0) }

    return (oldByBatch.keys + newByBatch.keys)
        .associateWith { batchId -> (newByBatch[batchId] ?: 0) - (oldByBatch[batchId] ?: 0) }
        .filterValues { it != 0 }
}

/**
 * Status for a rebuilt inventory_deductions row, mirroring the create-path rows written
 * by ClinicalVaccinationService. Pure so it can be unit-tested without Room/Android (see
 * VaccinationEditEngineTest), like [inventoryDiff].
 *
 * A batch this edit actually deducted from is COMPLETED: that deduction either succeeded
 * (ground truth that stock moved) or is already covered by the shortfall branch, so it
 * never inherits an older row's verdict. For a batch the edit did not touch, a rewritten
 * row keeps the verdict of the old row with the same batch, vaccine and quantity; failing
 * that the row is new or re-sized here and is COMPLETED, unless every previous row for
 * that vaccine on that batch was FAILED (stock was never moved and still is not - matching
 * the reversal cap in applyInventoryDiff).
 */
internal fun deductionStatus(
    shortfall: String?,
    deductedThisEdit: Boolean,
    exact: InventoryDeduction?,
    previousForVaccine: List<InventoryDeduction>
): String = when {
    // This edit could not take the extra doses off the shelf: stock did not move.
    shortfall != null -> "FAILED"
    // This edit did take doses off this batch. A batch that failed on create, then
    // succeeded on edit after a restock, used to inherit the old FAILED row here - and
    // deleteVaccination reverses only COMPLETED rows (VaccinationRepositoryImpl), so the
    // dose was gone with no way back.
    deductedThisEdit -> "COMPLETED"
    exact != null -> exact.status
    previousForVaccine.isEmpty() -> "COMPLETED"
    previousForVaccine.none { it.status == "COMPLETED" } -> "FAILED"
    else -> "COMPLETED"
}

/**
 * Single edit transaction coordinator for Vaccination.
 *
 * It compares the persisted vaccination with the edited vaccination and only applies
 * side effects when the corresponding part of the record actually changed.
 */
@Singleton
class VaccinationEditEngine @Inject constructor(
    private val transactionRunner: TransactionRunner,
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

        // Batches whose extra deduction could not happen because the shelf was short, mapped
        // to the reason. Recorded as FAILED in the deduction ledger below, exactly as the
        // create path does, instead of throwing the whole clinical edit away.
        var stockShortfalls: Map<String, String> = emptyMap()

        transactionRunner.run {
            val stockDelta = inventoryDiff(original, updated)
            val inventoryChanged = stockDelta.isNotEmpty()
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
                    remarks = com.neochildclinic.feature.statistics.domain.FinanceCalculator.buildVaccinationRemarks(updated),
                    recordedBy = user,
                    transactionGroupId = transactionGroupId
                )
            }

            if (inventoryChanged) {
                stockShortfalls = applyInventoryDiff(stockDelta, original, updated, user)
            }
            // Local-only deduction audit: rebuild whenever the item set moved at all so
            // no stale row (e.g. an old vaccine name) is left behind - but never as part
            // of a no-op save.
            if (inventoryChanged || itemsChanged) {
                reconcileInventoryDeductions(updated, stockDelta, stockShortfalls)
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

    private suspend fun applyInventoryDiff(delta: Map<String, Int>, old: Vaccination, new: Vaccination, user: String): Map<String, String> {
        // Only a COMPLETED inventory_deductions row means stock actually moved. A FAILED
        // row (ClinicalVaccinationService writes one when e.g. stock was short) left
        // stock untouched, so reversing its quantity would invent doses that never left:
        // batch has 1 dose, visit for 2 -> deduction FAILED, stock stays 1; editing the
        // visit to 1 must not push stock to 2. Cap every reversal at the ledger's
        // completed quantity for that batch (0 when the ledger has no completed row,
        // which also covers legacy visits created before the ledger existed - their
        // stock was never deducted, so nothing may be given back on an edit).
        val completedByBatch = inventoryRepository.getInventoryDeductionsForVaccination(old.id)
            .filter { it.status == "COMPLETED" && it.batchId != null }
            .groupingBy { it.batchId!! }
            .fold(0) { acc, row -> acc + row.quantity }

        val shortfalls = mutableMapOf<String, String>()

        delta.forEach { (batchId, batchDelta) ->
            if (batchDelta > 0) {
                // A batch already recorded on the original visit was valid when first
                // administered; don't block an edit (e.g. a quantity bump) just because
                // the batch has since expired. Only brand-new batch selections must pass
                // the expiry check.
                val batchAlreadyUsed = old.items.any { it.batchId == batchId }
                try {
                    inventoryRepository.deductStockFromBatch(
                        batchId = batchId,
                        quantity = batchDelta,
                        user = user,
                        transactionType = InventoryTransactionType.VACCINATION,
                        visitId = new.id,
                        patientId = new.patientId,
                        allowExpired = batchAlreadyUsed,
                        givenDate = new.dateGiven
                    )
                } catch (e: InsufficientStockException) {
                    // Same rule as the create path: a short shelf is reconciled by staff, it
                    // must not discard the payment, date, doctor and reminder corrections the
                    // user made in this same save. Recorded as FAILED below, which also keeps
                    // the reversal cap from handing back doses that never left the batch.
                    shortfalls[batchId] = e.message ?: "Insufficient stock"
                }
            } else {
                val reverseQuantity = minOf(-batchDelta, completedByBatch[batchId] ?: 0)
                if (reverseQuantity > 0) {
                    inventoryRepository.reverseDeduction(
                        batchId = batchId,
                        quantity = reverseQuantity,
                        user = user,
                        visitId = new.id,
                        patientId = new.patientId
                    )
                }
            }
        }
        return shortfalls
    }

    // inventory_deductions is a local-only audit of what a visit took out of stock
    // (see the create path in ClinicalVaccinationService). Edits never touched it, so a
    // swapped-out batch left its old COMPLETED row behind and the patient's inventory
    // dialog kept showing both the old and the new vaccine. Recreate the set from the
    // final items whenever stock actually changed, mirroring the create-path rows.
    //
    // Local-only deduction audit, rebuilt from the final items whenever stock actually
    // changed, mirroring the create-path rows. Status comes from [deductionStatus].
    private suspend fun reconcileInventoryDeductions(vaccination: Vaccination, delta: Map<String, Int> = emptyMap(), shortfalls: Map<String, String> = emptyMap()) {
        val previousByBatch = inventoryRepository.getInventoryDeductionsForVaccination(vaccination.id)
            .groupBy { it.batchId }
        inventoryRepository.deleteInventoryDeductionsForVaccination(vaccination.id)
        vaccination.items.forEach { item ->
            val shortfall = shortfalls[item.batchId]
            val forVaccine = previousByBatch[item.batchId].orEmpty().filter { it.vaccineId == item.vaccineId }
            val status = deductionStatus(
                shortfall = shortfall,
                deductedThisEdit = (delta[item.batchId] ?: 0) > 0,
                exact = forVaccine.firstOrNull { it.quantity == item.quantity },
                previousForVaccine = forVaccine
            )
            inventoryRepository.insertInventoryDeduction(InventoryDeduction(
                vaccinationId = vaccination.id,
                vaccineId = item.vaccineId,
                vaccineName = item.vaccineName,
                batchId = item.batchId,
                quantity = item.quantity,
                status = status,
                errorMessage = when {
                    shortfall != null -> shortfall
                    status == "FAILED" -> exact?.errorMessage ?: forVaccine.firstOrNull { it.status == "FAILED" }?.errorMessage
                    else -> null
                },
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

