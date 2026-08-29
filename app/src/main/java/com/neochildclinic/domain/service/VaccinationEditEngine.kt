package com.neochildclinic.domain.service

import androidx.room.withTransaction
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.VaccinationRepository
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
    private val vaccinationRepository: VaccinationRepository,
    private val inventoryRepository: InventoryRepository,
    private val financeRepository: FinanceRepository,
    private val reminderRepository: ReminderRepository
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
        reminderSpecs: List<ReminderSpec>
    ) {
        require(original.id == updated.id) { "Vaccination edit requires the same vaccination ID." }

        val transactionGroupId = UUID.randomUUID().toString()

        database.withTransaction {
            val inventoryChanged = inventoryDiff(original, updated).isNotEmpty()
            val financeChanged = financeChanged(original, updated)

            // Persist the clinical record first. The repository preserves unchanged item IDs
            // and queues deletes only for items that actually disappeared.
            vaccinationRepository.addVaccination(updated, transactionGroupId)

            if (financeChanged) {
                financeRepository.updateIncomeForVisit(
                    visitId = updated.id,
                    amount = updated.totalPaid,
                    cashAmount = updated.cashAmount,
                    onlineAmount = updated.onlineAmount,
                    remarks = com.neochildclinic.features.statistics.FinanceCalculator.buildVaccinationRemarks(
                        updated.items.joinToString(", ") { it.vaccineName },
                        updated.items.sumOf { it.netRate.coerceAtLeast(0.0) * it.quantity.coerceAtLeast(0) }
                    ),
                    recordedBy = user,
                    transactionGroupId = transactionGroupId
                )
            }

            if (inventoryChanged) {
                applyInventoryDiff(original, updated, user)
            }

            reconcileReminders(original.patientId, original.id, reminderSpecs, user)
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

    private suspend fun reconcileReminders(
        patientId: String,
        visitId: String,
        desired: List<ReminderSpec>,
        user: String
    ) {
        val existing = reminderRepository.getRemindersByVisitId(visitId)

        fun baseKey(type: String, vaccineNames: List<String>): String = buildString {
            append(type.trim().lowercase())
            append('|')
            append(vaccineNames.map { PatientUtils.cleanVaccineName(it).lowercase().trim() }.sorted().joinToString(","))
        }

        val unused = existing.toMutableList()

        for (spec in desired) {
            val key = baseKey(spec.type, spec.vaccineNames)
            val match = unused.firstOrNull {
                baseKey(
                    it.type,
                    it.vaccineName.split(", ").filter { n -> n.isNotBlank() }
                ) == key
            }

            if (match != null) {
                unused.remove(match)
                val changed = match.dueDate != spec.dueDate ||
                    match.type != spec.type ||
                    match.vaccineName != spec.vaccineNames.distinct().joinToString(", ") ||
                    match.nxtVaccineId?.sorted() != spec.vaccineIds.distinct().sorted() ||
                    (match.notes ?: "") != spec.notes

                if (changed) {
                    reminderRepository.updateReminderForEdit(
                        match.copy(
                            type = spec.type,
                            vaccineName = spec.vaccineNames.distinct().joinToString(", "),
                            nxtVaccineId = spec.vaccineIds.distinct().ifEmpty { null },
                            dueDate = spec.dueDate,
                            notes = spec.notes,
                            status = "ACTIVE",
                            reminderEnabled = true
                        ),
                        performedBy = user
                    )
                }
            } else {
                reminderRepository.saveNextVaccination(
                    patientId = patientId,
                    originalVisitId = visitId,
                    type = spec.type,
                    vaccineNames = spec.vaccineNames,
                    nxtVaccineId = spec.vaccineIds,
                    dueDate = spec.dueDate,
                    notes = spec.notes,
                    performedBy = user
                )
            }
        }

        // Anything left was removed by the user, so delete only those reminders.
        unused.forEach { reminder ->
            reminderRepository.deleteReminder(reminder, user)
        }
    }
}