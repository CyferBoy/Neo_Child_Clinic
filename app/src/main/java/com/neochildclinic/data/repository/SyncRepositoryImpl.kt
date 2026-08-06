package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.core.model.SyncItem
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.model.SyncStatus
import com.neochildclinic.domain.manager.SyncManager
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.SyncState
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val syncManager: SyncManager
) : SyncRepository {

    private val syncDao = database.syncQueueDao()
    
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    override suspend fun enqueue(
        entityName: String,
        entityId: String,
        operation: SyncOperation,
        priority: SyncPriority,
        transactionGroupId: String?
    ) {
        if (entityId.isBlank() || entityId == "kotlin.Unit" || entityId == "Unit" || entityId == "null") {
            return
        }

        syncDao.enqueue(
            SyncQueueEntity(
                entityName = entityName,
                entityId = entityId,
                operation = operation.name,
                priority = priority.name,
                transactionGroupId = transactionGroupId
            )
        )
        syncManager.scheduleSync()
    }

    override fun getPendingCount(): Flow<Int> = syncDao.getPendingCount()

    override fun getSyncQueue(): Flow<List<SyncItem>> = 
        syncDao.getAllItems().map { list -> list.map { it.toDomain() } }

    override suspend fun clearSyncedItems() {
        syncDao.clearSynced()
    }

    override suspend fun processNextItems() {
        syncDao.cleanCorruptedItems()
        syncDao.requeueStaleSyncingItems(
            staleBefore = com.neochildclinic.core.utils.PatientUtils.getIsoTimestampMinutesAgo(5)
        )

        val pending = syncDao.getItemsByStatus(SyncStatus.PENDING.name)
        if (pending.isEmpty()) {
            _syncState.value = SyncState.IDLE
            return
        }

        _syncState.value = SyncState.SYNCING
        
        // 1. Sort the queue to respect FK dependencies
        val sortedQueue = pending.sortedWith(compareBy({ getEntityPriority(it.entityName) }, { it.createdAt }))

        // 2. Group by transactionGroupId
        val groups = sortedQueue.groupBy { it.transactionGroupId ?: java.util.UUID.randomUUID().toString() }

        var hasError = false

        for ((groupId, groupItems) in groups) {
            try {
                database.withTransaction {
                    for (item in groupItems) {
                        syncDao.updateStatus(item.queueId, SyncStatus.SYNCING.name)
                    }
                }
                
                // Process items in group sequentially
                for (item in groupItems) {
                    uploadEntity(item)
                    syncDao.updateStatus(item.queueId, SyncStatus.SYNCED.name)
                    syncDao.deleteItem(item)
                }
            } catch (e: Exception) {
                hasError = true
                android.util.Log.e("SyncRepository", "Group sync failed: $groupId", e)
                
                val isNetworkError = e is java.io.IOException || e.message?.contains("network", ignoreCase = true) == true
                
                for (item in groupItems) {
                    if (isNetworkError && item.retryCount < 5) {
                        syncDao.incrementRetryCount(item.queueId, e.message ?: "Network error")
                        syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
                    } else {
                        syncDao.markFailed(item.queueId, SyncStatus.FAILED.name, e.message ?: "Sync failed")
                    }
                }
            }
        }
        
        _syncState.value = if (hasError) SyncState.ERROR else SyncState.IDLE
        
        // 3. Loop if more items arrived during processing
        if (syncDao.getPendingCountSync() > 0 && !hasError) {
            processNextItems()
        }
    }

    private fun getEntityPriority(entityName: String): Int {
        return when (entityName) {
            "PATIENT" -> 1
            "VACCINE" -> 2
            "BATCH" -> 3
            "VACCINATION", "VISIT" -> 4
            "VACCINATION_ITEM" -> 5
            "INVENTORY_TRANSACTION" -> 6
            "FINANCE" -> 7
            "AUDIT_LOG" -> 8
            "PATIENT_NOTE" -> 9
            "REMINDER_STATE" -> 10
            else -> 100
        }
    }

    private suspend fun uploadEntity(item: SyncQueueEntity) {
        val table = when (item.entityName) {
            "PATIENT" -> "patients"
            "VACCINATION", "VISIT" -> "patient_visits"
            "VACCINATION_ITEM" -> "vaccination_items"
            "WASTE" -> "waste_records"
            "REMINDER_STATE", "DUE_REMINDER", "COMPLETED_REMINDER", "DISMISSED_REMINDER", "EXTERNAL_REMINDER" -> "reminders"
            "VACCINE" -> "vaccines"
            "BATCH" -> {
                if (item.operation == SyncOperation.UPDATE.name) {
                    return 
                }
                "vaccine_batches"
            }
            "TRANSACTION", "INVENTORY_TRANSACTION" -> "inventory_transactions"
            "PATIENT_NOTE" -> "patient_notes"
            "FINANCE" -> "finance_transactions"
            "PROFILE", "STAFF" -> "profiles"
            "BORROW" -> "borrow_records"
            "AUDIT_LOG" -> "audit_logs"
            "CONSULTATION" -> "consultations"
            else -> throw IllegalArgumentException("Unknown entity: ${item.entityName}")
        }

        if (item.operation == SyncOperation.DELETE.name) {
            postgrest.from(table).delete {
                filter {
                    eq("id", item.entityId)
                }
            }
            return
        }

        val localData = fetchEntityData(item)
        if (localData != null) {
            val localUpdatedAt = getEntityUpdatedAt(localData)
            
            try {
                // Fetch remote metadata for conflict detection
                val remoteData = postgrest.from(table).select {
                    filter { eq("id", item.entityId) }
                }.decodeSingleOrNull<Map<String, kotlinx.serialization.json.JsonElement>>()
                
                if (remoteData != null) {
                    val remoteUpdatedAtStr = remoteData["updated_at"]?.toString()?.replace("\"", "")
                        ?: remoteData["last_updated"]?.toString()?.replace("\"", "")
                    
                    val remoteUpdatedAt = com.neochildclinic.core.utils.PatientUtils.isoToLong(remoteUpdatedAtStr)
                    val localUpdatedAtLong = com.neochildclinic.core.utils.PatientUtils.isoToLong(localUpdatedAt)

                    if (remoteUpdatedAt > localUpdatedAtLong) {
                        // REMOTE IS NEWER: Sync back to local (Self-healing)
                        downloadAndReplaceLocal(item.entityName, remoteData)
                        return
                    }
                }
            } catch (e: Exception) {
                // Proceed with upsert if check fails
            }

            // EXPLICIT CASTING: Supabase upsert<T> requires the concrete type at compile time
            // to find the correct serializer. Passing 'Any' will fail.
            when (localData) {
                is PatientEntity -> postgrest.from(table).upsert(localData)
                is VisitEntity -> postgrest.from(table).upsert(localData)
                is VaccinationItemEntity -> postgrest.from(table).upsert(localData)
                is WasteEntity -> postgrest.from(table).upsert(localData)
                is ReminderEntity -> postgrest.from(table).upsert(localData)
                is VaccineEntity -> postgrest.from(table).upsert(localData)
                is VaccineBatchEntity -> postgrest.from(table).upsert(localData)
                is InventoryTransactionEntity -> postgrest.from(table).upsert(localData)
                is FinanceEntity -> postgrest.from(table).upsert(localData)
                is AuditLogEntity -> postgrest.from(table).upsert(localData)
                is ProfileEntity -> postgrest.from(table).upsert(localData)
                is ConsultationEntity -> postgrest.from(table).upsert(localData)
                is BorrowEntity -> postgrest.from(table).upsert(localData)
                is PatientNotesEntity -> postgrest.from(table).upsert(localData)
            }
        }
    }

    private suspend fun downloadAndReplaceLocal(entityName: String, remoteMap: Map<String, kotlinx.serialization.json.JsonElement>) {
        val json = kotlinx.serialization.json.Json { 
            ignoreUnknownKeys = true 
            coerceInputValues = true
        }
        val element = kotlinx.serialization.json.JsonObject(remoteMap)
        
        when (entityName) {
            "PATIENT" -> {
                val entity = json.decodeFromJsonElement<PatientEntity>(element)
                database.patientDao().insertPatient(entity.copy(isSynced = true))
            }
            "VACCINATION", "VISIT" -> {
                val entity = json.decodeFromJsonElement<VisitEntity>(element)
                database.vaccinationDao().insertVaccination(entity.copy(isSynced = true))
            }
            "VACCINATION_ITEM" -> {
                val entity = json.decodeFromJsonElement<VaccinationItemEntity>(element)
                database.vaccinationItemDao().insertItems(listOf(entity))
            }
            "VACCINE" -> {
                val entity = json.decodeFromJsonElement<VaccineEntity>(element)
                database.vaccineDao().insertVaccine(entity)
            }
            "BATCH" -> {
                val entity = json.decodeFromJsonElement<VaccineBatchEntity>(element)
                database.vaccineDao().insertBatch(entity)
            }
            "FINANCE" -> {
                val entity = json.decodeFromJsonElement<FinanceEntity>(element)
                database.financeDao().insertTransaction(entity.copy(isSynced = true))
            }
            "PATIENT_NOTE" -> {
                val entity = json.decodeFromJsonElement<PatientNotesEntity>(element)
                database.patientNotesDao().insertNote(entity.copy(isSynced = true))
            }
            "AUDIT_LOG" -> {
                val entity = json.decodeFromJsonElement<AuditLogEntity>(element)
                database.auditLogDao().insertLog(entity.copy(isSynced = true))
            }
        }
    }

    private fun getEntityUpdatedAt(data: Any?): String {
        return when (data) {
            is PatientEntity -> data.updatedAt ?: ""
            is VisitEntity -> data.updatedAt ?: ""
            is WasteEntity -> data.updatedAt
            is ConsultationEntity -> data.updatedAt ?: ""
            is ReminderEntity -> data.updatedAt
            is VaccineEntity -> data.lastUpdated
            is VaccineBatchEntity -> data.updatedAt
            is AuditLogEntity -> data.timestamp
            is PatientNotesEntity -> data.timestamp
            is InventoryTransactionEntity -> data.timestamp
            is BorrowEntity -> data.borrowedDate
            else -> ""
        }
    }
    
    private suspend fun fetchEntityData(item: SyncQueueEntity): Any? {
        val entityId = item.entityId
        
        // Reminder State stable ID handling
        if (item.entityName == "REMINDER_STATE" && entityId.contains("||")) {
            val parts = entityId.split("||")
            if (parts.size == 3) {
                return database.dueReminderDao().getReminderByStableId(parts[0], parts[1], parts[2])
            }
        }

        return try {
            when (item.entityName) {
                "PATIENT" -> {
                    val entity = database.patientDao().getPatientById(entityId)
                    // Strip 'TEMP-' prefix before uploading
                    if (entity?.patientClinicId?.startsWith("TEMP-") == true) {
                        entity.copy(patientClinicId = null)
                    } else {
                        entity
                    }
                }
                "VACCINATION", "VISIT" -> database.vaccinationDao().getVaccinationById(entityId)
                "VACCINATION_ITEM" -> database.vaccinationItemDao().getItemById(entityId)
                "WASTE" -> database.wasteDao().getWasteById(entityId)
                "REMINDER_STATE" -> database.dueReminderDao().getReminderById(entityId.toLongOrNull() ?: -1L)
                "VACCINE" -> database.vaccineDao().getVaccineById(entityId)
                "BATCH" -> database.vaccineDao().getBatchById(entityId)
                "TRANSACTION", "INVENTORY_TRANSACTION" -> database.vaccineDao().getTransactionById(entityId)
                "FINANCE" -> database.financeDao().getTransactionById(entityId)
                "AUDIT_LOG" -> database.auditLogDao().getLogById(entityId)
                "PROFILE", "STAFF" -> database.profileDao().getProfileById(entityId)
                "CONSULTATION" -> database.consultationDao().getConsultationById(entityId)
                "BORROW" -> database.borrowDao().getRecordById(entityId)
                "PATIENT_NOTE" -> database.patientNotesDao().getNoteById(entityId)
                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "Error fetching data for sync: ${item.entityName} ID $entityId", e)
            null
        }
    }

    override suspend fun retryFailedItems() {
        val failed = syncDao.getItemsByStatus(SyncStatus.FAILED.name)
        for (item in failed) {
            syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
        }
        syncManager.scheduleSync()
    }

    override suspend fun deleteQueueItem(queueId: Long) {
        val item = syncDao.getItemById(queueId)
        if (item != null) {
            syncDao.deleteItem(item)
        }
    }

    override suspend fun retryItem(queueId: Long) {
        syncDao.updateStatus(queueId, SyncStatus.PENDING.name)
        syncManager.scheduleSync()
    }

    override suspend fun deleteAllFailed() {
        val failed = syncDao.getItemsByStatus(SyncStatus.FAILED.name)
        for (item in failed) {
            syncDao.deleteItem(item)
        }
    }
}
