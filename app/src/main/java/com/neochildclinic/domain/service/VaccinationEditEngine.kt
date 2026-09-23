package com.neochildclinic.domain.service

import androidx.room.withTransaction
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.data.repository.FinanceRepositoryImpl
import com.neochildclinic.data.repository.InventoryRepositoryImpl
import com.neochildclinic.data.repository.ReminderRepositoryImpl
import com.neochildclinic.data.repository.VaccinationRepositoryImpl
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first
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
    private val vaccinationRepository: VaccinationRepositoryImpl,
    private val inventoryRepository: InventoryRepositoryImpl,
    private val financeRepository: FinanceRepositoryImpl,
    private val reminderRepository: ReminderRepositoryImpl
) {
    data class ReminderSpec(
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

            // Replace-all items: DELETE prior IDs + CREATE under fresh UUIDs (same visit id).
            vaccinationRepository.addVaccination(updated, transactionGroupId)

            if (financeChanged) {
                financeRepository.updateIncomeForVisit(
                    visitId = updated.id,
                    amount = updated.totalPaid,
                    cashAmount = updated.cashAmount,
                    onlineAmount = updated.onlineAmount,
                    remarks = com.neochildclinic.features.statistics.FinanceCalculator.buildVaccinationRemarks(updated),
                    recordedBy = user,
                    transactionGroupId = transactionGroupId
                )
            }

            if (inventoryChanged) {
                applyInventoryDiff(original, updated, user)
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
        database.inventoryDeductionDao().deleteForVaccination(vaccination.id)
        vaccination.items.forEach { item ->
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
            .filter { it.id !in excludedReminderIds }
            .toMutableList()

        // Replace-all: hard-delete every remaining reminder for this visit (queue DELETE),
        // then create the desired set under fresh UUIDs. Empty desired = delete only.
        existing.forEach { reminder ->
            reminderRepository.deleteReminder(reminder, user)
        }

        data class DesiredRow(
            val type: String,
            val vaccineName: String,
            val vaccineId: String?,
            val dueDate: String,
            val notes: String
        )

        val desiredRows = desired.flatMap { spec ->
            val names = spec.vaccineNames
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val ids = spec.vaccineIds
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (names.isEmpty()) {
                listOf(
                    DesiredRow(
                        type = spec.type,
                        vaccineName = "",
                        vaccineId = null,
                        dueDate = spec.dueDate,
                        notes = spec.notes
                    )
                )
            } else {
                names.mapIndexedNotNull { index, name ->
                    val id = ids.getOrNull(index)
                    DesiredRow(
                        type = spec.type,
                        vaccineName = name,
                        vaccineId = id,
                        dueDate = spec.dueDate,
                        notes = spec.notes
                    )
                }
            }
        }

        desiredRows.forEach { row ->
            reminderRepository.saveNextVaccination(
                patientId = patientId,
                originalVisitId = visitId,
                type = row.type,
                vaccineNames = if (row.vaccineName.isBlank()) emptyList() else listOf(row.vaccineName),
                nxtVaccineId = row.vaccineId?.let { listOf(it) } ?: emptyList(),
                dueDate = row.dueDate,
                notes = row.notes,
                performedBy = user,
                forceNewId = true
            )
        }
    }

}