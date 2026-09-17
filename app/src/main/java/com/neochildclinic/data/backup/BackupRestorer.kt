package com.neochildclinic.data.backup

import androidx.room.withTransaction
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.dao.BackupDao
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.domain.model.RestoreMode

/**
 * Applies a [BackupPayloadV1] that has already passed [BackupValidator] to the local
 * database.
 *
 * FK ordering: tables are deleted child-first and inserted parent-first, matching the
 * `ForeignKey(...)` declarations in data/local/entity/ (see the ordering comment on
 * BackupDao). Everything happens inside a single Room transaction - if any step throws, the
 * whole restore rolls back and the pre-restore data is left exactly as it was (req. 11: "the
 * application must never leave the database half-restored").
 *
 * Merge-mode conflict resolution (req. 10, documented here rather than left implicit):
 *  - Tables with a reliable last-updated column (profiles.updated_at, vaccines.last_updated,
 *    vaccine_batches.updated_at, doctor_weekly_slots/doctor_slot_exceptions.updated_at,
 *    patients/consultations/patient_visits.updatedAt, reminders/personal_vaccine_reminders/
 *    expenses/consultation_todos/vaccination_todos.updated_at, waste_records.updatedAt):
 *    the incoming row replaces the existing one only if its timestamp is strictly newer -
 *    the exact same last-write-wins rule SyncRepositoryImpl already uses when resolving
 *    upload conflicts against Supabase, reused here instead of inventing a second rule.
 *  - Tables that are append-only ledgers/logs by design and carry no reliable "last
 *    updated" column (borrow_records, borrow_returns, patient_notes, finance_transactions,
 *    inventory_transactions, inventory_deductions, audit_logs): insert-if-absent only - an
 *    existing row is never overwritten, since there is no safe way to tell which copy is
 *    newer.
 *  - vaccination_items are children of a patient_visits row with no timestamp of their own:
 *    they follow their parent visit - re-inserted only for a visit that itself was
 *    inserted/updated by this merge, left untouched otherwise.
 *
 * After the data tables are applied, every restored row whose `isSynced` is false is
 * re-enqueued onto sync_queue (UPDATE) so the existing SyncWorker/SyncRepositoryImpl push it
 * on its own schedule - the same upsert-by-id path already used for every local edit, which
 * is what prevents restore from creating duplicate Supabase rows (req. 12).
 */
class BackupRestorer(
    private val database: AppDatabase,
    private val backupDao: BackupDao = database.backupDao()
) {

    data class Outcome(val appliedRecordCounts: Map<String, Int>)

    suspend fun restore(payload: BackupPayloadV1, mode: RestoreMode): Outcome {
        return database.withTransaction {
            when (mode) {
                RestoreMode.REPLACE -> replaceAll(payload)
                RestoreMode.MERGE -> mergeAll(payload)
            }
        }
    }

    // ---------------------------------------------------------------- Replace mode ----

    private suspend fun replaceAll(payload: BackupPayloadV1): Outcome {
        // Delete child-first (reverse of the insert order below).
        backupDao.clearAuditLogs()
        backupDao.clearVaccinationTodos()
        backupDao.clearConsultationTodos()
        backupDao.clearPatientNotes()
        backupDao.clearExpenses()
        backupDao.clearFinance()
        backupDao.clearInventoryDeductions()
        backupDao.clearInventoryTransactions()
        backupDao.clearWaste()
        backupDao.clearBorrowReturns()
        backupDao.clearBorrowRecords()
        backupDao.clearPersonalReminders()
        backupDao.clearReminders()
        backupDao.clearConsultations()
        backupDao.clearVaccinationItems()
        backupDao.clearVisits()
        backupDao.clearPatients()
        backupDao.clearDoctorSlotExceptions()
        backupDao.clearDoctorWeeklySlots()
        backupDao.clearVaccineBatches()
        backupDao.clearVaccines()
        backupDao.clearProfiles()

        // Insert parent-first.
        insertAllParentFirst(payload)

        // Replace mode writes every row in the payload, so appliedIds = null means
        // "everything counts" for resync purposes.
        enqueueUnsyncedForResync(payload, appliedIds = null)

        return Outcome(payload.recordCounts())
    }

    private suspend fun insertAllParentFirst(payload: BackupPayloadV1) {
        if (payload.profiles.isNotEmpty()) backupDao.insertProfiles(payload.profiles)
        if (payload.vaccines.isNotEmpty()) backupDao.insertVaccines(payload.vaccines)
        if (payload.vaccineBatches.isNotEmpty()) backupDao.insertVaccineBatches(payload.vaccineBatches)
        if (payload.doctorWeeklySlots.isNotEmpty()) backupDao.insertDoctorWeeklySlots(payload.doctorWeeklySlots)
        if (payload.doctorSlotExceptions.isNotEmpty()) backupDao.insertDoctorSlotExceptions(payload.doctorSlotExceptions)
        if (payload.patients.isNotEmpty()) backupDao.insertPatients(payload.patients)
        if (payload.visits.isNotEmpty()) backupDao.insertVisits(payload.visits)
        if (payload.vaccinationItems.isNotEmpty()) backupDao.insertVaccinationItems(payload.vaccinationItems)
        if (payload.consultations.isNotEmpty()) backupDao.insertConsultations(payload.consultations)
        if (payload.reminders.isNotEmpty()) backupDao.insertReminders(payload.reminders)
        if (payload.personalReminders.isNotEmpty()) backupDao.insertPersonalReminders(payload.personalReminders)
        if (payload.borrowRecords.isNotEmpty()) backupDao.insertBorrowRecords(payload.borrowRecords)
        if (payload.borrowReturns.isNotEmpty()) backupDao.insertBorrowReturns(payload.borrowReturns)
        if (payload.wasteRecords.isNotEmpty()) backupDao.insertWaste(payload.wasteRecords)
        if (payload.inventoryTransactions.isNotEmpty()) backupDao.insertInventoryTransactions(payload.inventoryTransactions)
        if (payload.inventoryDeductions.isNotEmpty()) backupDao.insertInventoryDeductions(payload.inventoryDeductions)
        if (payload.financeTransactions.isNotEmpty()) backupDao.insertFinance(payload.financeTransactions)
        if (payload.expenses.isNotEmpty()) backupDao.insertExpenses(payload.expenses)
        if (payload.patientNotes.isNotEmpty()) backupDao.insertPatientNotes(payload.patientNotes)
        if (payload.consultationTodos.isNotEmpty()) backupDao.insertConsultationTodos(payload.consultationTodos)
        if (payload.vaccinationTodos.isNotEmpty()) backupDao.insertVaccinationTodos(payload.vaccinationTodos)
        if (payload.auditLogs.isNotEmpty()) backupDao.insertAuditLogs(payload.auditLogs)
    }

    // ------------------------------------------------------------------ Merge mode ----

    private suspend fun mergeAll(payload: BackupPayloadV1): Outcome {
        val appliedCounts = LinkedHashMap<String, Int>()
        val appliedIds = HashMap<String, MutableSet<String>>() // table label -> ids actually written

        fun <T> lww(
            table: String,
            incoming: List<T>,
            existing: List<T>,
            idOf: (T) -> String,
            updatedAtOf: (T) -> String?
        ): List<T> {
            if (incoming.isEmpty()) return emptyList()
            val existingById = existing.associateBy(idOf)
            val toApply = incoming.filter { row ->
                val current = existingById[idOf(row)] ?: return@filter true
                (updatedAtOf(row) ?: "") > (updatedAtOf(current) ?: "")
            }
            appliedIds.getOrPut(table) { mutableSetOf() }.addAll(toApply.map(idOf))
            return toApply
        }

        fun <T> insertIfAbsent(table: String, incoming: List<T>, existing: List<T>, idOf: (T) -> String): List<T> {
            if (incoming.isEmpty()) return emptyList()
            val existingIds = existing.map(idOf).toHashSet()
            val toApply = incoming.filter { idOf(it) !in existingIds }
            appliedIds.getOrPut(table) { mutableSetOf() }.addAll(toApply.map(idOf))
            return toApply
        }

        // Parents first, exactly mirroring the Replace-mode insert order.
        val profilesToApply = lww("profiles", payload.profiles, backupDao.getAllProfilesForBackup(), { it.id }, { it.updatedAt })
        if (profilesToApply.isNotEmpty()) backupDao.insertProfiles(profilesToApply)
        appliedCounts["profiles"] = profilesToApply.size

        val vaccinesToApply = lww("vaccines", payload.vaccines, backupDao.getAllVaccinesForBackup(), { it.id }, { it.lastUpdated })
        if (vaccinesToApply.isNotEmpty()) backupDao.insertVaccines(vaccinesToApply)
        appliedCounts["vaccines"] = vaccinesToApply.size

        val batchesToApply = lww("vaccineBatches", payload.vaccineBatches, backupDao.getAllVaccineBatchesForBackup(), { it.batchId }, { it.updatedAt })
        if (batchesToApply.isNotEmpty()) backupDao.insertVaccineBatches(batchesToApply)
        appliedCounts["vaccineBatches"] = batchesToApply.size

        val slotsToApply = lww("doctorWeeklySlots", payload.doctorWeeklySlots, backupDao.getAllDoctorWeeklySlotsForBackup(), { it.id }, { it.updatedAt })
        if (slotsToApply.isNotEmpty()) backupDao.insertDoctorWeeklySlots(slotsToApply)
        appliedCounts["doctorWeeklySlots"] = slotsToApply.size

        val exceptionsToApply = lww("doctorSlotExceptions", payload.doctorSlotExceptions, backupDao.getAllDoctorSlotExceptionsForBackup(), { it.id }, { it.updatedAt })
        if (exceptionsToApply.isNotEmpty()) backupDao.insertDoctorSlotExceptions(exceptionsToApply)
        appliedCounts["doctorSlotExceptions"] = exceptionsToApply.size

        val patientsToApply = lww("patients", payload.patients, backupDao.getAllPatientsForBackup(), { it.id }, { it.updatedAt })
        if (patientsToApply.isNotEmpty()) backupDao.insertPatients(patientsToApply)
        appliedCounts["patients"] = patientsToApply.size

        val visitsToApply = lww("visits", payload.visits, backupDao.getAllVisitsForBackup(), { it.id }, { it.updatedAt })
        if (visitsToApply.isNotEmpty()) backupDao.insertVisits(visitsToApply)
        appliedCounts["vaccinations"] = visitsToApply.size
        val appliedVisitIds = appliedIds["visits"].orEmpty()

        // vaccination_items follow their parent visit: only re-applied for a visit that
        // was itself just inserted/updated above.
        val itemsToApply = payload.vaccinationItems.filter { it.vaccinationId in appliedVisitIds }
        if (itemsToApply.isNotEmpty()) {
            itemsToApply.map { it.vaccinationId }.toSet().forEach { visitId ->
                backupDao.clearVaccinationItemsForVisit(visitId)
            }
            backupDao.insertVaccinationItems(itemsToApply)
        }
        appliedCounts["vaccinationItems"] = itemsToApply.size

        val consultationsToApply = lww("consultations", payload.consultations, backupDao.getAllConsultationsForBackup(), { it.id }, { it.updatedAt })
        if (consultationsToApply.isNotEmpty()) backupDao.insertConsultations(consultationsToApply)
        appliedCounts["consultations"] = consultationsToApply.size

        val remindersToApply = lww("reminders", payload.reminders, backupDao.getAllRemindersForBackup(), { it.id }, { it.updatedAt })
        if (remindersToApply.isNotEmpty()) backupDao.insertReminders(remindersToApply)
        appliedCounts["reminders"] = remindersToApply.size

        val personalRemindersToApply = lww("personalReminders", payload.personalReminders, backupDao.getAllPersonalRemindersForBackup(), { it.id }, { it.updatedAt })
        if (personalRemindersToApply.isNotEmpty()) backupDao.insertPersonalReminders(personalRemindersToApply)
        appliedCounts["personalReminders"] = personalRemindersToApply.size

        val borrowToApply = insertIfAbsent("borrowRecords", payload.borrowRecords, backupDao.getAllBorrowRecordsForBackup(), { it.id })
        if (borrowToApply.isNotEmpty()) backupDao.insertBorrowRecords(borrowToApply)
        appliedCounts["borrowRecords"] = borrowToApply.size

        val borrowReturnsToApply = insertIfAbsent("borrowReturns", payload.borrowReturns, backupDao.getAllBorrowReturnsForBackup(), { it.id })
        if (borrowReturnsToApply.isNotEmpty()) backupDao.insertBorrowReturns(borrowReturnsToApply)
        appliedCounts["borrowReturns"] = borrowReturnsToApply.size

        val wasteToApply = lww("wasteRecords", payload.wasteRecords, backupDao.getAllWasteForBackup(), { it.id }, { it.updatedAt })
        if (wasteToApply.isNotEmpty()) backupDao.insertWaste(wasteToApply)
        appliedCounts["wasteRecords"] = wasteToApply.size

        val invTxToApply = insertIfAbsent("inventoryTransactions", payload.inventoryTransactions, backupDao.getAllInventoryTransactionsForBackup(), { it.transactionId })
        if (invTxToApply.isNotEmpty()) backupDao.insertInventoryTransactions(invTxToApply)
        appliedCounts["inventoryTransactions"] = invTxToApply.size

        val invDeductionsExisting = backupDao.getAllInventoryDeductionsForBackup()
        val invDeductionsToApply = insertIfAbsent(
            "inventoryDeductions", payload.inventoryDeductions, invDeductionsExisting
        ) { "${it.vaccinationId}:${it.vaccineId}:${it.batchId}:${it.resolvedAt}" }
        if (invDeductionsToApply.isNotEmpty()) backupDao.insertInventoryDeductions(invDeductionsToApply)
        appliedCounts["inventoryDeductions"] = invDeductionsToApply.size

        val financeToApply = insertIfAbsent("financeTransactions", payload.financeTransactions, backupDao.getAllFinanceForBackup(), { it.id })
        if (financeToApply.isNotEmpty()) backupDao.insertFinance(financeToApply)
        appliedCounts["financeTransactions"] = financeToApply.size

        val expensesToApply = lww("expenses", payload.expenses, backupDao.getAllExpensesForBackup(), { it.id }, { it.updatedAt })
        if (expensesToApply.isNotEmpty()) backupDao.insertExpenses(expensesToApply)
        appliedCounts["expenses"] = expensesToApply.size

        val notesToApply = insertIfAbsent("patientNotes", payload.patientNotes, backupDao.getAllPatientNotesForBackup(), { it.id })
        if (notesToApply.isNotEmpty()) backupDao.insertPatientNotes(notesToApply)
        appliedCounts["patientNotes"] = notesToApply.size

        val consultationTodosToApply = lww("consultationTodos", payload.consultationTodos, backupDao.getAllConsultationTodosForBackup(), { it.id }, { it.updatedAt })
        if (consultationTodosToApply.isNotEmpty()) backupDao.insertConsultationTodos(consultationTodosToApply)
        appliedCounts["consultationTodos"] = consultationTodosToApply.size

        val vaccinationTodosToApply = lww("vaccinationTodos", payload.vaccinationTodos, backupDao.getAllVaccinationTodosForBackup(), { it.id }, { it.updatedAt })
        if (vaccinationTodosToApply.isNotEmpty()) backupDao.insertVaccinationTodos(vaccinationTodosToApply)
        appliedCounts["vaccinationTodos"] = vaccinationTodosToApply.size

        val auditLogsToApply = insertIfAbsent("auditLogs", payload.auditLogs, backupDao.getAllAuditLogsForBackup(), { it.id })
        if (auditLogsToApply.isNotEmpty()) backupDao.insertAuditLogs(auditLogsToApply)
        appliedCounts["auditLogs"] = auditLogsToApply.size

        // Re-enqueue only the rows actually written in this merge, matching each table's
        // applied-id set collected above.
        enqueueUnsyncedForResync(payload, appliedIds)

        return Outcome(appliedCounts)
    }

    // ------------------------------------------------------------- Sync re-enqueue ----

    /**
     * Re-enqueues restored rows that were still unsynced at export time, so the existing
     * SyncWorker/SyncRepositoryImpl push them to Supabase on its normal schedule (upsert by
     * id - never a duplicate remote row).
     *
     * Only tables whose entity actually carries an `isSynced` flag AND that
     * SyncRepositoryImpl.uploadEntity() already knows how to push are included here:
     * profiles, vaccines, vaccine_batches, and vaccination_items have no `isSynced` column
     * on their entities at all (catalog/child-of-visit data that isn't pushed through this
     * offline outbox), so there is nothing to re-enqueue for them - restoring them is a
     * pure local-database operation.
     *
     * [appliedIds] is null for Replace mode (every row in the payload was just written, so
     * every unsynced row qualifies) or the per-table "ids actually written" map built by
     * [mergeAll] for Merge mode (a row that lost its last-write-wins comparison was never
     * touched in the DB, so it must not be re-queued here either).
     */
    private suspend fun enqueueUnsyncedForResync(payload: BackupPayloadV1, appliedIds: Map<String, Set<String>>?) {
        val now = PatientUtils.getCurrentIsoTimestamp()
        val entries = mutableListOf<SyncQueueEntity>()

        fun <T> collect(table: String, entityName: String, items: List<T>, idOf: (T) -> String, isSyncedOf: (T) -> Boolean) {
            val applied = appliedIds?.get(table)
            items.forEach { item ->
                val id = idOf(item)
                if (isSyncedOf(item)) return@forEach
                if (applied != null && id !in applied) return@forEach
                entries += SyncQueueEntity(
                    entityName = entityName,
                    entityId = id,
                    operation = com.neochildclinic.core.model.SyncOperation.UPDATE.name,
                    priority = com.neochildclinic.core.model.SyncPriority.MEDIUM.name,
                    createdAt = now,
                    updatedAt = now
                )
            }
        }

        collect("visits", "VACCINATION", payload.visits, { it.id }, { it.isSynced })
        collect("consultations", "CONSULTATION", payload.consultations, { it.id }, { it.isSynced })
        collect("reminders", "REMINDERS", payload.reminders, { it.id }, { it.isSynced })
        collect("personalReminders", "PERSONAL_REMINDER", payload.personalReminders, { it.id }, { it.isSynced })
        collect("borrowRecords", "BORROW", payload.borrowRecords, { it.id }, { it.isSynced })
        collect("borrowReturns", "BORROW_RETURN", payload.borrowReturns, { it.id }, { it.isSynced })
        collect("wasteRecords", "WASTE", payload.wasteRecords, { it.id }, { it.isSynced })
        collect("inventoryTransactions", "INVENTORY_TRANSACTION", payload.inventoryTransactions, { it.transactionId }, { it.isSynced })
        collect("financeTransactions", "FINANCE", payload.financeTransactions, { it.id }, { it.isSynced })
        collect("expenses", "EXPENSE", payload.expenses, { it.id }, { it.isSynced })
        collect("patientNotes", "PATIENT_NOTE", payload.patientNotes, { it.id }, { it.isSynced })
        collect("consultationTodos", "CONSULTATION_TODO", payload.consultationTodos, { it.id }, { it.isSynced })
        collect("vaccinationTodos", "VACCINATION_TODO", payload.vaccinationTodos, { it.id }, { it.isSynced })
        collect("auditLogs", "AUDIT_LOG", payload.auditLogs, { it.id }, { it.isSynced })
        collect("doctorWeeklySlots", "DOCTOR_WEEKLY_SLOT", payload.doctorWeeklySlots, { it.id }, { it.isSynced })
        collect("doctorSlotExceptions", "DOCTOR_SLOT_EXCEPTION", payload.doctorSlotExceptions, { it.id }, { it.isSynced })
        collect("patients", "PATIENT", payload.patients, { it.id }, { it.isSynced })

        entries.forEach { database.syncQueueDao().enqueue(it) }
    }
}
