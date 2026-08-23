package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.repository.InventoryRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.data.cache.MemoryCache

@Singleton
class VaccinationRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val auth: Auth,
    private val syncRepository: SyncRepository,
    private val financeRepository: com.neochildclinic.domain.repository.FinanceRepository,
    private val inventoryRepository: InventoryRepository,
    private val auditLogger: AuditLogger,
    private val memoryCache: MemoryCache
) : VaccinationRepository {

    private val vaccinationDao = database.vaccinationDao()
    private val vaccinationItemDao = database.vaccinationItemDao()
    private val inventoryDeductionDao = database.inventoryDeductionDao()
    private val patientDao = database.patientDao()
    private val vaccineDao = database.vaccineDao()
    private val dueReminderDao = database.dueReminderDao()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val allVaccinations: Flow<List<Vaccination>> = 
        vaccinationDao.getAllVaccinations().flatMapLatest { list ->
            if (list.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val flows = list.map { entity ->
                vaccinationItemDao.getItemsForVaccination(entity.id).map { items ->
                    entity.toVaccination().copy(items = items.map { it.toDomain() })
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
                    entity.toVaccination().copy(items = items.map { it.toDomain() })
                }
            }
            combine(flows) { it.toList() }
        }

    override fun getVaccinationCardsForPatient(patientId: String): Flow<List<com.neochildclinic.data.local.entity.PatientVaccinationCardEntity>> =
        vaccinationDao.getVaccinationCardsForPatient(patientId)

    override suspend fun getVaccinationById(id: String): Vaccination? {
        memoryCache.getVaccination(id)?.let { return it }
        return withContext(Dispatchers.IO) {
            val entity = vaccinationDao.getVaccinationById(id) ?: return@withContext null
            val items = vaccinationItemDao.getItemsForVaccination(id).first()
            entity.toVaccination().copy(items = items.map { it.toDomain() }).also {
                memoryCache.putVaccination(it)
            }
        }
    }

    override suspend fun refreshVaccinations() {
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
                        if (local == null || local.isSynced) {
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
            var itemsSkippedMissingCatalogRef = 0

            database.withTransaction {
                for (remoteItem in items) {
                    // FOREIGN KEY CHECK: the visit this item belongs to must exist locally.
                    val visitExists = vaccinationDao.getVaccinationById(remoteItem.vaccinationId) != null
                    if (!visitExists) {
                        itemsSkippedMissingVisit++
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
                }
            }

            android.util.Log.i("VaccinationRepo", """
                Vaccination Items Sync Complete:
                - Total Downloaded: $totalItemsDownloaded
                - Successfully Imported: $itemsImported
                - Skipped (Missing Visit Locally): $itemsSkippedMissingVisit
                - Skipped (Missing Vaccine/Batch Locally): $itemsSkippedMissingCatalogRef
            """.trimIndent())
        }
    }

    override suspend fun addVaccination(vaccination: Vaccination, transactionGroupId: String?) {
        database.withTransaction {
            val existing = vaccinationDao.getVaccinationById(vaccination.id)
            vaccinationDao.insertVaccination(vaccination.toEntity(isSynced = false))

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
                    incoming.toEntity().copy(
                        id = matching.id,
                        vaccinationId = vaccination.id
                    )
                } else {
                    incoming.toEntity().copy(
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

            memoryCache.putVaccination(vaccination.copy(items = itemEntities.map { it.toDomain() }))

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
    }

    override suspend fun deleteVaccination(id: String) {
        database.withTransaction {
            val existing = vaccinationDao.getActiveVaccinationById(id) ?: return@withTransaction
            
            memoryCache.invalidateVaccination(id)

            // Financial transactions are historical records and must remain after a clinical record is soft-deleted.
            // 1. Identify batches used in this vaccination
            val batchIds = existing.batchIds.split(",").filter { it.isNotBlank() }
            val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"

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
                dueReminderDao.softDeleteReminder(reminder.id)
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
            
            try {
                auditLogger.recordLog(
                    module = "PATIENT",
                    entityType = "VACCINATION",
                    entityId = id,
                    action = "DELETED",
                    patientId = existing.patientId,
                    remarks = "Vaccines: ${existing.vaccineNames}"
                )
            } catch (e: Exception) {
                android.util.Log.e("VaccinationRepo", "Audit log failed but deletion proceeded", e)
            }
        }
    }

    override suspend fun markAsDone(id: String) {
        database.withTransaction {
            val current = vaccinationDao.getActiveVaccinationById(id)
            if (current != null) {
                val updated = current.copy(status = com.neochildclinic.domain.model.ReminderStatus.COMPLETED, isSynced = false)
                vaccinationDao.insertVaccination(updated)
                
                syncRepository.enqueue(
                    entityName = "VACCINATION",
                    entityId = id,
                    operation = SyncOperation.UPDATE,
                    priority = SyncPriority.MEDIUM
                )
                
                auditLogger.recordLog(
                    module = "PATIENT",
                    entityType = "VACCINATION",
                    entityId = id,
                    action = "COMPLETED",
                    patientId = current.patientId,
                    remarks = "Vaccines: ${current.vaccineNames}"
                )
            }
        }
    }

    override fun getTodayCount(date: String): Flow<Int> = vaccinationDao.getCountByDate(date)
    override fun getTodayRevenue(date: String): Flow<Double?> = vaccinationDao.getRevenueByDate(date)
    override fun getTodayCash(date: String): Flow<Double?> = vaccinationDao.getCashByDate(date)
    override fun getTodayOnline(date: String): Flow<Double?> = vaccinationDao.getOnlineByDate(date)
    override fun getMonthlyCount(pattern: String): Flow<Int> = vaccinationDao.getMonthlyCount(pattern)
    override fun getMonthlyRevenue(pattern: String): Flow<Double?> = vaccinationDao.getMonthlyRevenue(pattern)
    override fun getVaccineNamesForMonth(pattern: String): Flow<List<String>> = vaccinationDao.getVaccineNamesForMonth(pattern)

    override suspend fun transferVaccinations(duplicateId: String, masterId: String) {
        vaccinationDao.updatePatientId(duplicateId, masterId)
    }
}

