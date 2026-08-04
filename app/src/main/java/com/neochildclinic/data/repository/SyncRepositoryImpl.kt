package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.WasteRecord
import com.neochildclinic.core.model.SyncItem
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.model.SyncStatus
import com.neochildclinic.domain.manager.SyncManager
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.SyncState
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.*
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
        priority: SyncPriority
    ) {
        if (entityId.isBlank() || entityId == "kotlin.Unit" || entityId == "Unit" || entityId == "null") {
            return
        }

        syncDao.enqueue(
            SyncQueueEntity(
                entityName = entityName,
                entityId = entityId,
                operation = operation.name,
                priority = priority.name
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
        syncDao.requeueStaleSyncingItems(staleBeforeMillis = System.currentTimeMillis() - 5 * 60 * 1000)

        val pending = syncDao.getItemsByStatus(SyncStatus.PENDING.name)
        if (pending.isEmpty()) {
            _syncState.value = SyncState.IDLE
            return
        }

        _syncState.value = SyncState.SYNCING
        var hasError = false

        for (item in pending) {
            try {
                syncDao.updateStatus(item.queueId, SyncStatus.SYNCING.name)
                uploadEntity(item)
                syncDao.updateStatus(item.queueId, SyncStatus.SYNCED.name)
                syncDao.deleteItem(item) 
            } catch (e: Exception) {
                hasError = true
                val isNetworkError = e is java.io.IOException || e.message?.contains("network", ignoreCase = true) == true
                if (isNetworkError && item.retryCount < 5) {
                    syncDao.incrementRetryCount(item.queueId, e.message ?: "Network error")
                    syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
                } else {
                    syncDao.markFailed(item.queueId, SyncStatus.FAILED.name, e.message ?: "Sync failed")
                }
            }
        }
        
        _syncState.value = if (hasError) SyncState.ERROR else SyncState.IDLE
    }

    private suspend fun uploadEntity(item: SyncQueueEntity) {
        val table = when (item.entityName) {
            "PATIENT" -> "patients"
            "VACCINATION", "VISIT" -> "vaccinations"
            "WASTE" -> "waste_records"
            "REMINDER_STATE", "DUE_REMINDER", "COMPLETED_REMINDER", "DISMISSED_REMINDER", "EXTERNAL_REMINDER" -> "reminders"
            "VACCINE" -> "vaccines"
            "BATCH" -> "vaccine_batches"
            "TRANSACTION" -> "transactions"
            "PATIENT_NOTE" -> "patient_notes"
            "FINANCE" -> "finance_transactions"
            "STAFF" -> "staff"
            "USER" -> "users"
            "BORROW" -> "borrow_records"
            "AUDIT_LOG" -> "audit_logs"
            "CONSULTATION" -> "consultations"
            "INVENTORY_TRANSACTION" -> "inventory_transactions"
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

        val finalData = fetchEntityData(item)
        if (finalData != null) {
            // Last-write-wins check
            val localUpdatedAt = getEntityUpdatedAt(finalData)
            
            try {
                val remoteData = postgrest.from(table).select {
                    filter {
                        eq("id", item.entityId)
                    }
                }.decodeSingleOrNull<Map<String, kotlinx.serialization.json.JsonElement>>()
                
                if (remoteData != null) {
                    val remoteUpdatedAt = remoteData["updated_at"]?.toString()?.toLongOrNull() ?: 0L
                    if (remoteUpdatedAt > localUpdatedAt) {
                        // Remote is newer, skip upload
                        return
                    }
                }
            } catch (e: Exception) {
                // If select fails (e.g. 404/no record), proceed with upsert
            }

            postgrest.from(table).upsert(finalData)
        }
    }

    private fun getEntityUpdatedAt(data: Any?): Long {
        return when (data) {
            is Patient -> data.updatedAt
            is Vaccination -> data.updatedAt
            is WasteRecord -> data.updatedAt
            is ConsultationEntity -> data.updatedAt
            is ReminderEntity -> data.updatedAt
            is VaccineEntity -> data.lastUpdated
            is VaccineBatchEntity -> data.updatedAt
            else -> 0L
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
                    val patient = database.patientDao().getPatientById(entityId)?.toPatient()
                    // Strip 'TEMP-' prefix before uploading to Firestore
                    if (patient?.patientClinicId?.startsWith("TEMP-") == true) {
                        patient.copy(patientClinicId = "")
                    } else {
                        patient
                    }
                }
                "VACCINATION", "VISIT" -> database.vaccinationDao().getVaccinationById(entityId)?.toVaccination()
                "WASTE" -> database.wasteDao().getWasteById(entityId)?.toDomain()
                "REMINDER_STATE" -> database.dueReminderDao().getReminderById(entityId.toLongOrNull() ?: -1L)
                "VACCINE" -> database.vaccineDao().getVaccineById(entityId)
                "BATCH" -> database.vaccineDao().getBatchById(entityId)
                "TRANSACTION" -> database.vaccineDao().getTransactionById(entityId.toLongOrNull() ?: -1L)
                "FINANCE" -> database.financeDao().getTransactionById(entityId.toLongOrNull() ?: -1L)
                "AUDIT_LOG" -> database.auditLogDao().getLogById(entityId.toLongOrNull() ?: -1L)
                "STAFF" -> database.staffDao().getStaffById(entityId)
                "USER" -> database.staffDao().getUserById(entityId)
                "CONSULTATION" -> database.consultationDao().getConsultationById(entityId)
                "BORROW" -> database.borrowDao().getRecordById(entityId)
                "PATIENT_NOTE" -> database.patientNotesDao().getNoteById(entityId.toLongOrNull() ?: -1L)
                "INVENTORY_TRANSACTION" -> database.vaccineDao().getTransactionById(entityId.toLongOrNull() ?: -1L)
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
