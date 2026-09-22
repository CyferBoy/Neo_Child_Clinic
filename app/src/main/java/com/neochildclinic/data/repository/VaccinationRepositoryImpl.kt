package com.neochildclinic.data.repository

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
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.InventoryRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccinationRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val sessionManager: com.neochildclinic.core.session.SessionManager,
    private val syncRepository: SyncRepository,
    private val inventoryRepository: InventoryRepository,
    private val auditLogger: AuditLogger,
    @ApplicationContext private val appContext: Context
) {

    private val vaccinationDao = database.vaccinationDao()
    private val vaccinationItemDao = database.vaccinationItemDao()
    private val inventoryDeductionDao = database.inventoryDeductionDao()
    private val patientDao = database.patientDao()
    private val vaccineDao = database.vaccineDao()
    private val dueReminderDao = database.dueReminderDao()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allVaccinations: Flow<List<Vaccination>> = 
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
    fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>> =
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

    suspend fun refreshVaccinations() {
        withContext(Dispatchers.IO) {
            try {
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

            } catch (e: Exception) {
                android.util.Log.e("VaccinationRepo", "Cloud Refresh failed", e)
            }
        }
    }

    // Pure network fetch, no local writes - safe to run in parallel with other
    // startup sync tasks (e.g. inventory) without any ordering dependency.
    suspend fun fetchRemoteVaccinationItems(): List<VaccinationItemEntity> =
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
    suspend fun applyDownloadedVaccinationItems(items: List<VaccinationItemEntity>) {
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
                    vaccinationItemDao.deleteItemsForVaccination(visitId)
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

    suspend fun addVaccination(vaccination: Vaccination, transactionGroupId: String? = null) {
        database.withTransaction {
            val existing = vaccinationDao.getVaccinationById(vaccination.id)

            // Receipt numbers are assigned by the database (patient_visits trigger), never
            // here. A brand-new record is saved with a blank receiptNumber and picks up its
            // real "NEO-YY/YY-NNNNNN" number once this visit is pushed to Supabase; see
            // SyncRepositoryImpl's post-upsert read-back. Editing must never touch it, so an
            // already-issued number carried on `vaccination` is passed through untouched.
            val userName = sessionManager.getCurrentUserName()
            val entity = vaccination.copy(
                createdBy = if (existing == null) userName else (existing.createdBy ?: vaccination.createdBy ?: userName),
                updatedBy = userName
            ).toEntity(isSynced = false)
            vaccinationDao.insertVaccination(entity)

            // Reconcile item identity instead of deleting/recreating every row. This keeps
            // unchanged item IDs stable and queues explicit DELETE operations for removed rows.
            val existingItems = vaccinationItemDao.getItemsForVaccination(vaccination.id).first()
            val usedExistingIds = mutableSetOf<String>()

            val itemEntities = vaccination.items.map { incoming ->
                val matching = existingItems.firstOrNull { old ->
                    old.id !in usedExistingIds &&
                        old.vaccineId == incoming.vaccineId &&
                        old.batchId == incoming.batchId
                }

                if (matching != null) {
                    usedExistingIds += matching.id
                    incoming.copy(
                        id = matching.id,
                        vaccinationId = vaccination.id
                    )
                } else {
                    incoming.copy(
                        id = incoming.id.ifBlank { java.util.UUID.randomUUID().toString() },
                        vaccinationId = vaccination.id
                    )
                }
            }

            val removedItems = existingItems.filter { old ->
                old.id !in usedExistingIds && itemEntities.none { it.id == old.id }
            }

            vaccinationItemDao.deleteItemsForVaccination(vaccination.id)
            vaccinationItemDao.insertItems(itemEntities)

            val operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE
            syncRepository.enqueue(
                entityName = "VACCINATION",
                entityId = vaccination.id,
                operation = operation,
                priority = SyncPriority.HIGH,
                transactionGroupId = transactionGroupId
            )

            itemEntities.forEach { item ->
                val itemOperation = if (existingItems.any { it.id == item.id }) {
                    SyncOperation.UPDATE
                } else {
                    SyncOperation.CREATE
                }
                syncRepository.enqueue(
                    entityName = "VACCINATION_ITEM",
                    entityId = item.id,
                    operation = itemOperation,
                    priority = SyncPriority.MEDIUM,
                    transactionGroupId = transactionGroupId
                )
            }

            removedItems.forEach { item ->
                syncRepository.enqueue(
                    entityName = "VACCINATION_ITEM",
                    entityId = item.id,
                    operation = SyncOperation.DELETE,
                    priority = SyncPriority.MEDIUM,
                    transactionGroupId = transactionGroupId
                )
            }

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
        WidgetUtils.updateWidget(appContext)
    }

    suspend fun deleteVaccination(id: String) {
        database.withTransaction {
            val existing = vaccinationDao.getActiveVaccinationById(id) ?: return@withTransaction

            // Financial transactions are historical records and must remain after a clinical record is deleted.
            // 1. Identify batches used in this vaccination
            val batchIds = existing.batchIds.split(",").filter { it.isNotBlank() }
            val user = sessionManager.getCurrentUserName()

            // 2. Replenish inventory atomically
            for (batchId in batchIds) {
                try {
                    inventoryRepository.reverseDeduction(
                        batchId = batchId,
                        quantity = 1,
                        user = user,
                        visitId = id,
                        patientId = existing.patientId
                    )
                } catch (e: Exception) {
                    android.util.Log.e("VaccinationRepo", "Failed to replenish stock for batch $batchId: ${e.message}")
                }
            }

            // 3. Clean up deduction logs
            inventoryDeductionDao.deleteForVaccination(id)

            // 3b. Clean up reminders tied to this visit. Without this, a reminder that
            // already synced to Supabase is left behind there after the visit is deleted -
            // a later refreshReminders() pull then re-downloads that now-parentless
            // reminder locally, and any subsequent create/update sync for it permanently
            // fails with a foreign key violation (its originalVisitId no longer exists).
            val remindersForVisit = dueReminderDao.getRemindersByVisitId(id)
            for (reminder in remindersForVisit) {
                dueReminderDao.deleteReminderById(reminder.id)
                syncRepository.enqueue(
                    entityName = "REMINDERS",
                    entityId = reminder.id,
                    operation = SyncOperation.DELETE,
                    priority = SyncPriority.LOW
                )
            }

            // 4. Soft-delete the record
            vaccinationDao.deleteVaccination(id)
            
            syncRepository.enqueue(
                entityName = "VACCINATION",
                entityId = id,
                operation = SyncOperation.DELETE,
                priority = SyncPriority.MEDIUM
            )
            
            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "VACCINATION",
                entityId = id,
                action = "DELETED",
                patientId = existing.patientId,
                remarks = "Vaccines: ${existing.vaccineNames}"
            )
        }
        WidgetUtils.updateWidget(appContext)
    }

    suspend fun transferVaccinations(duplicateId: String, masterId: String) {
        vaccinationDao.updatePatientId(duplicateId, masterId)
    }
}

