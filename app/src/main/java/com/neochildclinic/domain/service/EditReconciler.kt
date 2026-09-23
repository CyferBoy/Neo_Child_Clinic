package com.neochildclinic.domain.service

import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.data.local.entity.VaccinationItemEntity
import java.util.UUID

/**
 * Item-level classification for edit saves. Old rows are matched to edited rows by
 * their existing id (the UI carries the persisted id through editing), never by
 * vaccine name. Each match is then classified as:
 *
 *   unchanged -> KEEP the row (id preserved, no local write, no sync op)
 *   changed   -> DELETE the old row, CREATE a replacement under a fresh id
 *   removed   -> DELETE the old row
 *   added     -> CREATE a new row
 *
 * Applied independently to vaccination_items and reminders; the parent visit row is
 * never touched here. Pure functions - covered by EditReconcilerTest.
 */
object EditReconciler {

    data class ItemPlan(
        val keepIds: Set<String>,
        val deleteIds: List<String>,
        val inserts: List<VaccinationItemEntity>
    ) {
        val anyChange: Boolean get() = deleteIds.isNotEmpty() || inserts.isNotEmpty()
    }

    fun classifyItems(
        existing: List<VaccinationItemEntity>,
        edited: List<VaccinationItemEntity>
    ): ItemPlan {
        val oldById = existing.associateBy { it.id }
        val claimed = mutableSetOf<String>()
        val keep = linkedSetOf<String>()
        val dels = mutableListOf<String>()
        val inserts = mutableListOf<VaccinationItemEntity>()

        for (incoming in edited) {
            val prior = incoming.id
                .takeIf { it.isNotBlank() }
                ?.let { oldById[it] }
                // A second edited row claiming the same original id (defensive - the UI
                // cannot produce this) must not match twice.
                ?.takeIf { it.id !in claimed }
            when {
                prior == null -> {
                    // Added row. Never reuse an id that still belongs to an old row.
                    val id = if (incoming.id.isBlank() || incoming.id in oldById) {
                        UUID.randomUUID().toString()
                    } else incoming.id
                    inserts += incoming.copy(id = id)
                }
                sameItem(prior, incoming) -> {
                    claimed += prior.id
                    keep += prior.id
                }
                else -> {
                    // Changed: the old row goes away and the replacement gets a NEW id,
                    // assigned here - after the comparison - never before it.
                    claimed += prior.id
                    dels += prior.id
                    inserts += incoming.copy(id = UUID.randomUUID().toString())
                }
            }
        }
        // Removed: any old row no edited row claimed.
        for (old in existing) if (old.id !in claimed) dels += old.id

        return ItemPlan(keep, dels, inserts)
    }

    private fun sameItem(a: VaccinationItemEntity, b: VaccinationItemEntity): Boolean =
        a.vaccineId == b.vaccineId &&
            a.vaccineName == b.vaccineName &&
            a.batchId == b.batchId &&
            a.batchNumber == b.batchNumber &&
            a.quantity == b.quantity &&
            a.mrp == b.mrp &&
            a.netRate == b.netRate &&
            a.expiryDate == b.expiryDate

    /** One flattened desired reminder row; reminderId is the original DB row it came from. */
    data class ReminderRow(
        val reminderId: String?,
        val type: String,
        val vaccineName: String,
        val vaccineId: String?,
        val dueDate: String,
        val notes: String
    )

    data class ReminderPlan(
        val delete: List<ReminderEntity>,
        val create: List<ReminderRow>
    )

    fun classifyReminders(
        existing: List<ReminderEntity>,
        desired: List<ReminderRow>,
        excludedIds: Set<String>
    ): ReminderPlan {
        val byId = existing.associateBy { it.id }
        val claimed = mutableSetOf<String>()
        val dels = mutableListOf<ReminderEntity>()
        val creates = mutableListOf<ReminderRow>()

        for (row in desired) {
            val prior = row.reminderId
                ?.takeIf { it !in excludedIds }
                ?.let { byId[it] }
                ?.takeIf { it.id !in claimed }
            when {
                prior == null -> creates += row
                sameReminder(prior, row) -> claimed += prior.id
                else -> {
                    // Changed: DELETE the old reminder, CREATE a new row with a fresh id.
                    claimed += prior.id
                    dels += prior
                    creates += row
                }
            }
        }
        // Removed: existing rows no desired row claimed. Rows cancelled this session
        // (excludedIds) were already soft-dismissed and must survive untouched.
        for (old in existing) {
            if (old.id !in excludedIds && old.id !in claimed) dels += old
        }
        return ReminderPlan(dels, creates)
    }

    // notes is intentionally not compared: it is cosmetic provenance text
    // ("Scheduled during visit on ..."), not part of the reminder's identity -
    // comparing it would replace every reminder whenever the visit date changed.
    private fun sameReminder(old: ReminderEntity, row: ReminderRow): Boolean {
        val oldIds = old.nxtVaccineId.orEmpty().map { it.trim() }.filter { it.isNotBlank() }
        val newIds = listOfNotNull(row.vaccineId?.trim()?.takeIf { it.isNotBlank() })
        return old.type == row.type &&
            old.vaccineName.trim() == row.vaccineName.trim() &&
            oldIds == newIds &&
            old.dueDate == row.dueDate
    }
}
