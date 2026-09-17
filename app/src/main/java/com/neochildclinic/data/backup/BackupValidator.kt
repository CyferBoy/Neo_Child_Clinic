package com.neochildclinic.data.backup

import com.neochildclinic.domain.model.RestoreSummary

/**
 * Validates a decoded [BackupEnvelope] before it is ever handed to BackupRestorer.
 *
 * Field-level validation (required fields present, correct types) is already enforced by
 * kotlinx.serialization when the JSON is decoded - a backup missing a non-nullable field
 * fails to decode at all and surfaces as [BackupException.Corrupted] in BackupSerializer.
 * What's left here is the *relational* validation the spec calls out separately (req. 21):
 * do the foreign keys the live Room schema enforces actually resolve within this payload.
 * These are exactly the FK relationships declared with `ForeignKey(...)` in the entity
 * classes under data/local/entity/ - re-checking them here means a broken backup is caught
 * with a clear message before BackupRestorer ever opens a database transaction, instead of
 * failing deep inside a bulk insert.
 */
object BackupValidator {

    fun validate(envelope: BackupEnvelope): RestoreSummary {
        val payload = envelope.data
        val issues = mutableListOf<String>()

        fun <T> duplicateIds(items: List<T>, idOf: (T) -> String, table: String) {
            val seen = HashSet<String>()
            for (item in items) {
                val id = idOf(item)
                if (!seen.add(id)) issues += "$table: duplicate id '$id'"
            }
        }

        val patientIds = payload.patients.map { it.id }.toHashSet()
        val vaccineIds = payload.vaccines.map { it.id }.toHashSet()
        val batchIds = payload.vaccineBatches.map { it.batchId }.toHashSet()
        val visitIds = payload.visits.map { it.id }.toHashSet()
        val borrowRecordIds = payload.borrowRecords.map { it.id }.toHashSet()
        val weeklySlotIds = payload.doctorWeeklySlots.map { it.id }.toHashSet()

        duplicateIds(payload.patients, { it.id }, "patients")
        duplicateIds(payload.vaccines, { it.id }, "vaccines")
        duplicateIds(payload.vaccineBatches, { it.batchId }, "vaccineBatches")
        duplicateIds(payload.visits, { it.id }, "vaccinations")
        duplicateIds(payload.consultations, { it.id }, "consultations")
        duplicateIds(payload.vaccinationItems, { it.id }, "vaccinationItems")
        duplicateIds(payload.borrowRecords, { it.id }, "borrowRecords")

        fun checkRef(ok: Boolean, table: String, id: String, refTable: String, refId: String) {
            if (!ok) issues += "$table '$id' references missing $refTable '$refId'"
        }

        payload.vaccineBatches.forEach { checkRef(it.vaccineId in vaccineIds, "vaccineBatches", it.batchId, "vaccines", it.vaccineId) }
        payload.visits.forEach { checkRef(it.patientId in patientIds, "vaccinations", it.id, "patients", it.patientId) }
        payload.consultations.forEach { c ->
            checkRef(c.patientId in patientIds, "consultations", c.id, "patients", c.patientId)
            if (c.visitId.isNotBlank()) checkRef(c.visitId in visitIds, "consultations", c.id, "vaccinations", c.visitId)
        }
        payload.vaccinationItems.forEach { item ->
            checkRef(item.vaccinationId in visitIds, "vaccinationItems", item.id, "vaccinations", item.vaccinationId)
            checkRef(item.vaccineId in vaccineIds, "vaccinationItems", item.id, "vaccines", item.vaccineId)
            checkRef(item.batchId in batchIds, "vaccinationItems", item.id, "vaccineBatches", item.batchId)
        }
        payload.reminders.forEach { checkRef(it.patientId in patientIds, "reminders", it.id, "patients", it.patientId) }
        payload.personalReminders.forEach { r ->
            r.patientId?.takeIf { it.isNotBlank() }?.let { pid ->
                checkRef(pid in patientIds, "personalReminders", r.id, "patients", pid)
            }
            r.vaccineId?.takeIf { it.isNotBlank() }?.let { vid ->
                checkRef(vid in vaccineIds, "personalReminders", r.id, "vaccines", vid)
            }
        }
        payload.borrowRecords.forEach { b ->
            checkRef(b.vaccineId in vaccineIds, "borrowRecords", b.id, "vaccines", b.vaccineId)
            checkRef(b.batchId in batchIds, "borrowRecords", b.id, "vaccineBatches", b.batchId)
        }
        payload.borrowReturns.forEach { r ->
            checkRef(r.borrowRecordId in borrowRecordIds, "borrowReturns", r.id, "borrowRecords", r.borrowRecordId)
            checkRef(r.batchId in batchIds, "borrowReturns", r.id, "vaccineBatches", r.batchId)
        }
        payload.patientNotes.forEach { checkRef(it.patientId in patientIds, "patientNotes", it.id, "patients", it.patientId) }
        payload.doctorSlotExceptions.forEach { e ->
            e.weeklySlotId?.takeIf { it.isNotBlank() }?.let { sid ->
                checkRef(sid in weeklySlotIds, "doctorSlotExceptions", e.id, "doctorWeeklySlots", sid)
            }
        }

        if (issues.isNotEmpty()) {
            throw BackupException.BrokenRelationships(issues)
        }

        return RestoreSummary(
            backupId = envelope.backupId,
            createdAt = envelope.createdAt,
            appVersion = envelope.appVersionName,
            backupVersion = envelope.backupVersion,
            recordCounts = envelope.recordCounts
        )
    }
}
