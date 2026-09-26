package com.neochildclinic.features.vaccination

import com.neochildclinic.core.utils.InventoryUtils
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.VaccinationItem
import com.neochildclinic.domain.model.VaccineBatch
import java.util.Date

/** The best batch for a vaccine at a given date: in stock, not expired, soonest expiry first. */
internal fun bestAvailableBatch(vaccine: InventoryItem, givenDate: String): VaccineBatch? =
    vaccine.batches
        .filter { it.remainingQuantity > 0 && !InventoryUtils.isExpiredAsOf(it.expiryDate, givenDate) }
        .minByOrNull { PatientUtils.parseDate(it.expiryDate) ?: Date(Long.MAX_VALUE) }

/**
 * Re-validates every stored row's batch against a (possibly new) given date: a batch that is
 * still in stock but expires before the date is swapped for the next valid one (or cleared).
 */
internal fun revalidateRowsForDate(rows: List<VaccineSelectionState>, date: String): List<VaccineSelectionState> =
    rows.map { row ->
        val vaccine = row.selectedVaccine
        val batch = row.selectedBatch
        if (vaccine == null || batch == null || !InventoryUtils.isExpiredAsOf(batch.expiryDate, date)) row
        else row.copy(selectedBatch = bestAvailableBatch(vaccine, date))
    }

/**
 * Maps the persisted reminders of a vaccination into the UI's due-date grouped structure.
 * ACTIVE + enabled reminders only; a multi-vaccine reminder expands to one item per vaccine.
 */
internal fun buildNextVaccinationGroups(reminders: List<ReminderEntity>, inventory: List<InventoryItem>): List<NextVaccinationGroup> =
    reminders.filter { it.status == "ACTIVE" && it.reminderEnabled }
        .groupBy { it.dueDate }
        .map { (dueDate, groupReminders) ->
            NextVaccinationGroup(
                dueDate = dueDate,
                items = groupReminders.flatMap { reminder ->
                    val nextVaccineIds = reminder.nxtVaccineId ?: emptyList()
                    if (nextVaccineIds.isEmpty()) {
                        listOf(NextVaccinationItem(reminderId = reminder.id, type = reminder.type, vaccine = null))
                    } else {
                        nextVaccineIds.map { id ->
                            NextVaccinationItem(reminderId = reminder.id, type = reminder.type, vaccine = inventory.find { it.id == id })
                        }
                    }
                }
            )
        }

/**
 * Rebuilds the edit-screen's row selections from the persisted vaccination items, keeping each
 * row's stable identity (item id). Vaccines/batches deleted from the catalog fall back to a
 * placeholder vaccine / a direct batch lookup rather than dropping the row's data silently.
 */
internal suspend fun buildVaccineSelectionRows(
    items: List<VaccinationItem>,
    inventory: List<InventoryItem>,
    getBatch: suspend (String) -> VaccineBatch?
): List<VaccineSelectionState> =
    items.map { item ->
        val vaccine = inventory.firstOrNull { it.id == item.vaccineId }
            ?: InventoryItem(
                id = item.vaccineId,
                brandName = item.vaccineName.ifBlank { "Saved vaccine" },
                stock = 0,
                type = "",
                company = ""
            )
        val batch = inventory
            .firstOrNull { it.id == item.vaccineId }
            ?.batches
            ?.firstOrNull { it.batchId == item.batchId }
            ?: getBatch(item.batchId)
        VaccineSelectionState(
            id = item.id,
            selectedVaccine = vaccine,
            selectedBatch = batch,
            quantity = item.quantity
        )
    }

internal data class NextGroupsValidation(
    val errorMessage: String,
    val groupsWithErrors: List<NextVaccinationGroup>
)

/** Returns null when every group has a Type and Due Date, else the error plus groups with typeError set. */
internal fun validateNextGroups(groups: List<NextVaccinationGroup>): NextGroupsValidation? {
    var firstInvalidGroup: String? = null
    var firstInvalidItem: String? = null

    groups.forEach { group ->
        if (group.dueDate.isBlank()) {
            if (firstInvalidGroup == null) firstInvalidGroup = group.id
        }
        group.items.forEach { item ->
            if (item.type.isBlank()) {
                if (firstInvalidGroup == null) firstInvalidGroup = group.id
                if (firstInvalidItem == null) firstInvalidItem = item.id
            }
        }
    }

    if (firstInvalidGroup == null) return null
    return NextGroupsValidation(
        errorMessage = "Each Next Vaccination entry requires a Type and Due Date.",
        groupsWithErrors = groups.map { g ->
            g.copy(items = g.items.map { it.copy(typeError = it.type.isBlank()) })
        }
    )
}