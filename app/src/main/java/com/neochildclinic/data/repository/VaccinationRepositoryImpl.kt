package com.neochildclinic.data.repository
import com.neochildclinic.domain.repository.VaccinationRepository

import com.neochildclinic.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.utils.WidgetUtils
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.service.EditReconciler
import com.neochildclinic.data.repository.SyncRepositoryImpl
import com.neochildclinic.data.repository.InventoryRepositoryImpl
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

fun completedQuantityByBatch(rows: List<InventoryDeductionEntity>): Map<String, Int> =
    rows.asSequence()
        .filter { it.status == "COMPLETED" && it.batchId != null }
        .groupingBy { it.batchId!! }
        .fold(0) { acc, row -> acc + row.quantity }

@Singleton
class VaccinationRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val sessionManager: com.neochildclinic.core.session.SessionManager,
    private val syncRepository: SyncRepositoryImpl,
    private val inventoryRepository: InventoryRepositoryImpl,
    private val auditLogger: AuditLogger,
    @ApplicationContext private val appContext: Context
) : VaccinationRepository {

    private val vaccinationDao = database.vaccinationDao()
    private val vaccinationItemDao = database.vaccinationItemDao()
    private val inventoryDeductionDao = database.inventoryDeductionDao()
    private val patientDao = database.patientDao()
    private val vaccineDao = database.vaccineDao()
    private val financeDao = database.financeDao()
    private val dueReminderDao = database.dueReminderDao()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val allVaccinations: Flow<List<Vaccination>> = 
        vaccinationDao.getAllVaccinations().flatMapLatest { list ->
            if (list.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val flows = list.map { entity ->
                vaccinationItemDao.getItemsForVaccination(entity.id).map { items ->
                    entity.toVaccination().copy(items = items)
                }
            }
            combine(flows) { it.toList() }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>> =
        vaccinationDao.getVaccinationsForPatient(patientId).flatMapLatest { list ->
            if (list.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val flows = list.map { entity ->
                vaccinationItemDao.getItemsForVaccination(entity.id).map { items ->
                    entity.toVaccination().copy(items = items)
                }
            }
            combine(flows) { it.toList() }
        }

    fun getVaccinationCardsForPatient(patientId: String): Flow<List<com.neochildclinic.data.local.entity.PatientVaccinationCardEntity>> =
        vaccinationDao.getVaccinationCardsForPatient(patientId)

    suspend fun getVaccinationById(id: String): Vaccination? =
        withContext(Dispatchers.IO) {
            val entity = vaccinationDao.getVaccinationById(id) ?: return@withContext null
            val items = vaccinationItemDao.getItemsForVaccination(id).first()
            entity.toVaccination().copy(items = items)
        }

    override suspend fun refreshVaccinations() = cloudRefresh("VaccinationRepo") {
                val entities = postgrest.from("patient_visits").select().decodeList<VisitEntity>()
                val totalDownloaded = entities.size
                var imported = 0
                var failedValidation = 0
                var skippedMissingPatient = 0

                database.withTransaction {
                    for (remote in entities) {
                        // Basic Validation before Room insert
                        if (remote.id.isBlank() || remote.patientId.isBlank()) {
                            android.util.Log.e("VaccinationRepo", "Validation failed for ${remote.id}: patientId=${remote.patientId}")
                            failedValidation++
                            continue
                        }

                        // FOREIGN KEY CHECK: Ensure patient exists locally before inserting visit
                        val patientExists = patientDao.getPatientById(remote.patientId) != null
                        if (!patientExists) {
                            android.util.Log.e("VaccinationRepo", "FK Violation Avoided: Skipping Vaccination ${remote.id} because Patient ${remote.patientId} is missing locally.")
                            skippedMissingPatient++
                            continue
                        }

                        val local = vaccinationDao.getVaccinationById(remote.id)
                        if ((local == null || local.isSynced) && !database.syncQueueDao().isUnsynced("VACCINATION", remote.id)) {
                            vaccinationDao.insertVaccination(remote.copy(isSynced = true))
                            imported++
                        }
                    }
                }
                
                android.util.Log.i("VaccinationRepo", """
                    Sync Complete:
                    - Total Downloaded: $totalDownloaded
                    - Successfully Imported: $imported
                    - Failed Validation (Missing Data): $failedValidation
                    - Skipped (Missing Patients): $skippedMissingPatient
                """.trimIndent())

    }

    // Pure network fetch, no local writes - safe to run in parallel with other
    // startup sync tasks (e.g. inventory) without any ordering dependency.
    override suspend fun fetchRemoteVaccinationItems(): List<VaccinationItemEntity> =
        withContext(Dispatchers.IO) {
            try {
                postgrest.from("vaccination_items").select().decodeList<VaccinationItemEntity>()
            } catch (e: Exception) {
                android.util.Log.e("VaccinationRepo", "Vaccination items fetch failed", e)
                emptyList()
            }
        }

    // Local insert only - patient_visits carries only a denormalized name/id snapshot,
    // so without this the line items (and therefore Edit Vaccination's vaccine/batch
    // selection) stay empty on any device that re-syncs from scratch (fresh install,
    // cleared data), even though the visit itself looks complete.
    //
    // Callers MUST ensure vaccines/vaccine_batches are already synced locally before
    // calling this - vaccineId/batchId are CASCADE foreign keys, so an item whose
    // vaccine or batch isn't present locally yet gets silently skipped below rather
    // than crashing the whole transaction, and it will not be retried until the next
    // full refresh. Calling this before inventory sync has completed will skip
    // everything on a fresh install/cleared data.
    override suspend fun applyDownloadedVaccinationItems(items: List<VaccinationItemEntity>) {
        withContext(Dispatchers.IO) {
            val totalItemsDownloaded = items.size
            var itemsImported = 0
            var itemsSkippedMissingVisit = 0
            var itemsSkippedUnsyncedVisit = 0
            var itemsSkippedMissingCatalogRef = 0
            var itemsHealed = 0
            val acceptedByVisit = mutableMapOf<String, MutableSet<String>>()

            database.withTransaction {
                for (remoteItem in items) {
                    // FOREIGN KEY CHECK: the visit this item belongs to must exist locally.
                    val visitExists = vaccinationDao.getVaccinationById(remoteItem.vaccinationId) != null
                    if (!visitExists) {
                        itemsSkippedMissingVisit++
                        continue
                    }

                    // A visit with pending local changes is authoritative locally until it
                    // has been pushed. Importing its remote items in that window resurrects
                    // rows this device just deleted - e.g. a swapped-out vaccine, whose old
                    // row is queued for DELETE but still present in the cloud copy - which is
                    // why Edit Vaccination can end up showing both the old and the new
                    // vaccine. Once the push lands, the remote copy no longer contains the
                    // removed rows, so nothing is lost by skipping here.
                    if (database.syncQueueDao().isUnsynced("VACCINATION", remoteItem.vaccinationId)) {
                        itemsSkippedUnsyncedVisit++
                        continue
                    }

                    // FOREIGN KEY CHECK: vaccine and batch (both CASCADE FKs) must exist
                    // locally, or the insert would violate the constraint and silently
                    // fail the whole transaction.
                    val vaccineExists = vaccineDao.getVaccineById(remoteItem.vaccineId) != null
                    val batchExists = vaccineDao.getBatchById(remoteItem.batchId) != null
                    if (!vaccineExists || !batchExists) {
                        android.util.Log.e("VaccinationRepo", "FK Violation Avoided: Skipping vaccination_item ${remoteItem.id} - vaccineExists=$vaccineExists batchExists=$batchExists")
                        itemsSkippedMissingCatalogRef++
                        continue
                    }

                    vaccinationItemDao.insertItems(listOf(remoteItem))
                    itemsImported++
                    acceptedByVisit.getOrPut(remoteItem.vaccinationId) { mutableSetOf() }.add(remoteItem.id)
                }

                // Self-heal: for visits whose local state is fully synced the remote item
                // set is authoritative. A swapped-out vaccine that a previous pull
                // resurrected while the DELETE was still queued (see the guard above) has no
                // remote row anymore - drop the straggler.
                for ((visitId, acceptedIds) in acceptedByVisit) {
                    if (database.syncQueueDao().isUnsynced("VACCINATION", visitId)) continue
                    val local = vaccinationItemDao.getItemsForVaccination(visitId).first()
                    val resurrected = local.filter { it.id !in acceptedIds }
                    if (resurrected.isEmpty()) continue
                    val userName = sessionManager.getCurrentUserName()
                    val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
                    vaccinationItemDao.deleteItemsForVaccination(visitId, now, userName)
                    vaccinationItemDao.insertItems(
                        items.filter { it.vaccinationId == visitId && it.id in acceptedIds }
                    )
                    itemsHealed += resurrected.size
                }
            }

            android.util.Log.i("VaccinationRepo", """
                Vaccination Items Sync Complete:
                - Total Downloaded: $totalItemsDownloaded
                - Successfully Imported: $itemsImported
                - Skipped (Missing Visit Locally): $itemsSkippedMissingVisit
                - Skipped (Visit Has Unsynced Local Changes): $itemsSkippedUnsyncedVisit
                - Skipped (Missing Vaccine/Batch Locally): $itemsSkippedMissingCatalogRef
                - Healed (Resurrected Rows Removed): $itemsHealed
            """.trimIndent())
        }
    }

    /**
     * Writes the visit header and reconciles its vaccination_items one row at a time
     * (see EditReconciler): unchanged rows are not touched, changed rows are deleted
     * and recreated under a new id, removed rows are deleted, added rows are created.
     * Create path (no existing visit) inserts the incoming rows as-is.
     *
     * @return true when any item row was created or deleted.
     */
    override suspend fun addVaccination(vaccination: Vaccination, transactionGroupId: String? ): Boolean {
        var itemsChanged = false
        database.withTransaction {
            val existing = vaccinationDao.getVaccinationById(vaccination.id)

            // Receipt numbers are assigned by the database (patient_visits trigger), never
            // here. A brand-new record is saved with a blank receiptNumber and picks up its
            // real "NEO-YY/YY-NNNNNN" number once this visit is pushed to Supabase; see
            // SyncRepositoryImpl's post-upsert read-back. Editing must never touch it, so
            // the editor (which doesn't model these fields) carries the persisted values.
            // inventoryStatus too: it is set once at creation and the editor rebuilding
            // Vaccination with its default "PENDING" would otherwise wipe "COMPLETED" -
            // and make every save of a completed visit look like a parent change.
            val carried = if (existing == null) vaccination else vaccination.copy(
                receiptNumber = vaccination.receiptNumber.ifBlank { existing.receiptNumber },
                notes = vaccination.notes.ifBlank { existing.notes },
                inventoryStatus = existing.inventoryStatus
            )
            val userName = sessionManager.getCurrentUserName()
            val entity = carried.copy(
                createdBy = if (existing == null) userName else (existing.createdBy ?: vaccination.createdBy ?: userName),
                updatedBy = userName
            ).toEntity(isSynced = false)
                .let { if (existing == null) it else it.copy(
                    paymentId = existing.paymentId,
                    materialsUsed = existing.materialsUsed
                ) }

            val itemPlan = EditReconciler.classifyItems(
                existing = if (existing == null) emptyList()
                    else vaccinationItemDao.getItemsForVaccination(vaccination.id).first(),
                edited = vaccination.items.map { it.copy(vaccinationId = vaccination.id) }
            )
            itemsChanged = itemPlan.anyChange

            // Parent row is kept (same id, relationships intact) and written only when
            // something it carries actually changed: its own business fields, or the
            // denormalized item snapshot (plus any child-row change, because a pending
            // VACCINATION queue entry is what tells applyDownloadedVaccinationItems to
            // skip a pull while item DELETEs/CREATEs are still queued). A no-op save
            // writes nothing and queues nothing.
            val parentChanged = existing == null || itemPlan.anyChange || businessDiffers(existing, entity)
            if (parentChanged) {
                vaccinationDao.insertVaccination(entity)
                syncRepository.enqueue(
                    entityName = "VACCINATION",
                    entityId = vaccination.id,
                    operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE,
                    priority = SyncPriority.HIGH,
                    transactionGroupId = transactionGroupId
                )
                auditLogger.recordLog(
                    module = "PATIENT",
                    entityType = "VACCINATION",
                    entityId = vaccination.id,
                    action = if (existing == null) "VACCINATION" else "VACCINATION_UPDATED",
                    patientId = vaccination.patientId,
                    remarks = "Vaccines: ${vaccination.items.joinToString(", ") { it.vaccineName }}",
                    transactionGroupId = transactionGroupId
                )
            }

            // Item-level plan: untouched rows get no local write and no sync op.
            if (itemPlan.deleteIds.isNotEmpty()) {
                val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
                vaccinationItemDao.deleteItemsByIds(itemPlan.deleteIds, now, userName)
                itemPlan.deleteIds.forEach { oldId ->
                    syncRepository.enqueue(
                        entityName = "VACCINATION_ITEM",
                        entityId = oldId,
                        operation = SyncOperation.UPDATE,
                        priority = SyncPriority.MEDIUM,
                        transactionGroupId = transactionGroupId
                    )
                }
            }
            if (itemPlan.inserts.isNotEmpty()) {
                vaccinationItemDao.insertItems(itemPlan.inserts)
                itemPlan.inserts.forEach { item ->
                    syncRepository.enqueue(
                        entityName = "VACCINATION_ITEM",
                        entityId = item.id,
                        operation = SyncOperation.CREATE,
                        priority = SyncPriority.MEDIUM,
                        transactionGroupId = transactionGroupId
                    )
                }
            }
        }
        WidgetUtils.updateWidget(appContext)
        return itemsChanged
    }

    // Timestamps/audit columns and the editor-managed identity fields are not business
    // state - normalizing them away keeps a no-op save from looking like a change.
    private fun businessDiffers(old: VisitEntity, new: VisitEntity): Boolean =
        old.copy(
            createdAt = new.createdAt,
            updatedAt = new.updatedAt,
            isSynced = new.isSynced,
            createdBy = new.createdBy,
            updatedBy = new.updatedBy
        ) != new

    // Deletion failure/retry contract (see project spec "Deletion Failure and Retry
    // Behavior"):
    //
    // - Everything below runs inside one Room transaction, so a failure at any step rolls
    //   back every local change made by this call - no partial inventory restore, no
    //   partially-deleted reminders/items, no misleading "success". Nothing here swallows
    //   an exception from a *required* step; only the final soft state (inventoryStatus
    //   reconciliation elsewhere) is allowed to be best-effort, and this method has none of
    //   that kind of step.
    // - The very first line is also what makes a retry (a second call for the same id,
    //   whether from a UI double-tap or a caller retrying after a prior failure) safe:
    //   getActiveVaccinationById only returns a row that still exists, and this is a hard
    //   SQL DELETE, so once one call's transaction commits, every later call for the same
    //   id finds nothing and becomes a no-op. Concurrent calls are serialized by Room/SQLite
    //   the same way any two writers to the same database are, so there is no window where
    //   two calls both see the row as present.
    // - A remote sync failure afterward (network, Supabase down, auth) never undoes any of
    //   this: nothing here is reverted by SyncRepositoryImpl on a failed upload. The queued
    //   DELETE/CREATE/UPDATE operations below simply stay PENDING (or FAILED, still visible
    //   on the Sync screen and retryable) until they succeed - see SyncRepositoryImpl for
    //   the retry/backoff and DELETE-idempotency guarantees that apply once they're queued.
    suspend fun deleteVaccination(id: String) {
        val transactionGroupId = java.util.UUID.randomUUID().toString()
        database.withTransaction {
            val existing = vaccinationDao.getActiveVaccinationById(id) ?: return@withTransaction

            val user = sessionManager.getCurrentUserName()

            // 1. Replenish inventory atomically, from the inventory_deductions ledger -
            // the actual record of what this vaccination deducted (batch + quantity per
            // item) - rather than the denormalized batchIds string on the visit row, which
            // has no quantity and does not distinguish a batch that was actually deducted
            // from one whose deduction failed (see ClinicalVaccinationService, which writes
            // a COMPLETED/FAILED row per item for exactly this reason; VaccinationEditEngine
            // reverses stock the same way). A failure here throws out of this whole
            // transaction - it is never caught/logged-and-continued, so a batch that can't
            // be replenished (e.g. deleted from the catalog) blocks the deletion instead of
            // silently leaving stock short.
            val completedDeductions = inventoryDeductionDao.getCompletedForVaccination(id)
            for ((batchId, quantity) in completedQuantityByBatch(completedDeductions)) {
                inventoryRepository.reverseDeduction(
                    batchId = batchId,
                    quantity = quantity,
                    user = user,
                    visitId = id,
                    patientId = existing.patientId,
                    transactionGroupId = transactionGroupId
                )
            }

            // 2. Clean up the deduction ledger itself. Local-only table (no remote
            // counterpart, never appears in SyncRepositoryImpl's entity map), so this needs
            // no sync op - and deleting it here, in the same transaction as the reversal
            // above, is what keeps a reversal from ever being applied twice: if this
            // transaction is retried after a prior attempt's local commit, the rows read in
            // step 1 are already gone, so there is nothing left to reverse.
            inventoryDeductionDao.deleteForVaccination(id)

            // 2b. (Removed under soft-delete) The visit row itself is no longer physically
            // deleted, so nothing here can block a hard delete via FK - and unlink the
            // inventory_transactions audit trail is no longer needed, visit_id stays.

            // 3. Clean up reminders tied to this visit. Without this, a reminder that
            // already synced to Supabase is left behind there after the visit is deleted -
            // a later refreshReminders() pull then re-downloads that now-parentless
            // reminder locally, and any subsequent create/update sync for it permanently
            // fails with a foreign key violation (its originalVisitId no longer exists).
            val remindersForVisit = dueReminderDao.getRemindersByVisitId(id)
            val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
            for (reminder in remindersForVisit) {
                dueReminderDao.deleteReminderById(reminder.id, now, user)
                syncRepository.enqueue(
                    entityName = "REMINDERS",
                    // Remote identity for a reminder is serverId when known, else the local
                    // id (the two are always the same value for a reminder this app created
                    // and has synced at least once - see ReminderEntity.toRemote/toLocal -
                    // but serverId is the field that actually records "synced", so prefer
                    // it). The local row is already gone above, so this must be captured
                    // now rather than re-read later.
                    entityId = reminder.serverId ?: reminder.id,
                    operation = SyncOperation.UPDATE,
                    priority = SyncPriority.LOW,
                    transactionGroupId = transactionGroupId
                )
            }

            // 4. Soft-delete the vaccination's line items.
            val items = vaccinationItemDao.getItemsForVaccination(id).first()
            if (items.isNotEmpty()) {
                vaccinationItemDao.deleteItemsByIds(items.map { it.id }, now, user)
                items.forEach { item ->
                    syncRepository.enqueue(
                        entityName = "VACCINATION_ITEM",
                        entityId = item.id,
                        operation = SyncOperation.UPDATE,
                        priority = SyncPriority.MEDIUM,
                        transactionGroupId = transactionGroupId
                    )
                }
            }

            // 5. Finance: As of the soft-delete migration, we soft-delete the associated finance record too.
            // (Previously we unlinked visit_id but kept the finance transaction active.)
            val visitFinanceTxns = financeDao.getTransactionsByVisitId(id)
            for (txn in visitFinanceTxns) {
                financeDao.deleteTransactionById(txn.id, now, user)
                syncRepository.enqueue(
                    entityName = "FINANCE",
                    entityId = txn.id,
                    operation = SyncOperation.UPDATE,
                    priority = SyncPriority.MEDIUM,
                    transactionGroupId = transactionGroupId
                )
            }

            // 6. Soft-delete the visit itself
            vaccinationDao.deleteVaccination(id, now, user)

            syncRepository.enqueue(
                entityName = "VACCINATION",
                entityId = id,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM,
                transactionGroupId = transactionGroupId
            )

            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "VACCINATION",
                entityId = id,
                action = "SOFT_DELETED",
                patientId = existing.patientId,
                remarks = "Vaccines: ${existing.vaccineNames}",
                transactionGroupId = transactionGroupId
            )
        }
        WidgetUtils.updateWidget(appContext)
    }

    override suspend fun transferVaccinations(duplicateId: String, masterId: String) {
        vaccinationDao.updatePatientId(duplicateId, masterId)
    }
}

