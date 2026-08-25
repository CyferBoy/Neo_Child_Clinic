package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.core.model.SyncItem
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.model.SyncStatus
import com.neochildclinic.core.model.SyncErrorDetails
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
        // For CREATE/UPDATE: Parent (Priority 1) before Child (Priority 10)
        // For DELETE: Child (Priority 10 -> -10) before Parent (Priority 1 -> -1)
        val sortedQueue = pending.sortedWith(
            compareBy(
                { 
                    val basePriority = getEntityPriority(it.entityName)
                    if (it.operation == SyncOperation.DELETE.name) -basePriority else basePriority 
                }, 
                { it.createdAt }
            )
        )

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
                        syncDao.incrementRetryCount(item.queueId, buildSyncErrorDetails(e))
                        syncDao.updateStatus(item.queueId, SyncStatus.PENDING.name)
                    } else {
                        syncDao.markFailed(item.queueId, SyncStatus.FAILED.name, buildSyncErrorDetails(e))
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


    private fun buildSyncErrorDetails(error: Throwable): String {
        val reason = error.message?.takeIf { it.isNotBlank() } ?: "Sync failed"
        var current: Throwable? = error

        repeat(8) {
            val throwable = current ?: return@repeat
            val response = invokeGetter(throwable, "getResponse")
            if (response != null) {
                val request = invokeGetter(response, "getRequest")
                val url = sanitizeUrl(invokeGetter(request, "getUrl")?.toString())
                val requestHeaders = extractSafeHeaders(invokeGetter(request, "getHeaders"))
                    .mapKeys { "Request-${it.key}" }
                val responseHeaders = extractSafeHeaders(invokeGetter(response, "getHeaders"))
                    .mapKeys { "Response-${it.key}" }
                return SyncErrorDetails(
                    reason = reason,
                    url = url,
                    headers = responseHeaders + requestHeaders
                ).encode()
            }
            current = throwable.cause
        }

        return SyncErrorDetails(reason = reason).encode()
    }

    private fun invokeGetter(target: Any?, methodName: String): Any? {
        if (target == null) return null
        return runCatching {
            target.javaClass.methods
                .firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
                ?.invoke(target)
        }.getOrNull()
    }

    private fun extractSafeHeaders(headersObject: Any?): Map<String, String> {
        if (headersObject == null) return emptyMap()

        // Fail-closed allowlist: only header names known to be non-sensitive are ever
        // stored. A denylist would silently start leaking anything new the HTTP client
        // adds in a future version (e.g. a new auth-adjacent header) until someone
        // remembers to block it by name - an allowlist can't make that mistake.
        val safeNames = setOf(
            "content-type", "content-length", "date", "server", "connection",
            "cache-control", "vary", "transfer-encoding", "x-client-info",
            "x-request-id", "retry-after", "accept", "accept-encoding"
        )

        val entries = runCatching {
            val entriesMethod = headersObject.javaClass.methods
                .firstOrNull { it.name == "entries" && it.parameterTypes.isEmpty() }
            @Suppress("UNCHECKED_CAST")
            entriesMethod?.invoke(headersObject) as? Iterable<Any?>
        }.getOrNull() ?: return emptyMap()

        return buildMap {
            entries.forEach { entry ->
                val pair = entry as? Pair<*, *>
                val name = pair?.first?.toString() ?: return@forEach
                if (name.lowercase() !in safeNames) return@forEach
                val value = when (val raw = pair.second) {
                    is Iterable<*> -> raw.joinToString(",")
                    else -> raw?.toString().orEmpty()
                }
                put(name, value)
            }
        }
    }

    // Query strings can carry secrets (e.g. a Storage signed URL's token=...), so only
    // the scheme/host/path is ever kept - never the query string.
    private fun sanitizeUrl(rawUrl: String?): String? {
        if (rawUrl == null) return null
        val queryIndex = rawUrl.indexOf('?')
        return if (queryIndex >= 0) rawUrl.substring(0, queryIndex) else rawUrl
    }

    private fun getEntityPriority(entityName: String): Int {
        return when (entityName) {
            "PATIENT", "VACCINE" -> 1
            "VACCINATION", "VISIT", "BATCH" -> 2
            "VACCINATION_ITEM", "CONSULTATION", "CONSULTATION_TODO", "VACCINATION_TODO", "WASTE", "BORROW" -> 3
            "INVENTORY_TRANSACTION", "FINANCE" -> 4
            "REMINDERS", "PATIENT_NOTE", "AUDIT_LOG" -> 5
            else -> 100
        }
    }

    private suspend fun uploadEntity(item: SyncQueueEntity) {
        val table = when (item.entityName) {
            "PATIENT" -> "patients"
            "VACCINATION", "VISIT" -> "patient_visits"
            "VACCINATION_ITEM" -> "vaccination_items"
            "WASTE" -> "waste_records"
            "REMINDERS" -> "reminders"
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
            "CONSULTATION_TODO" -> "consultation_todos"
            "VACCINATION_TODO" -> "vaccination_todos"
            else -> throw IllegalArgumentException("Unknown entity: ${item.entityName}")
        }

        if (item.operation == SyncOperation.DELETE.name) {
            if (item.entityName == "REMINDERS") {
                val serverId = database.dueReminderDao().getReminderById(item.entityId)?.serverId
                if (serverId != null) {
                    postgrest.from(table).delete {
                        filter { eq("id", serverId) }
                    }
                }
            } else {
                postgrest.from(table).delete {
                    filter { eq("id", item.entityId) }
                }
            }
            return
        }

        // Specialized logic for REMINDERS to handle server-generated IDs
        if (item.entityName == "REMINDERS") {
            val localReminder = database.dueReminderDao().getReminderById(item.entityId)
            if (localReminder != null) {
                if (item.operation == SyncOperation.CREATE.name && localReminder.serverId == null) {
                    // CREATE: use the locally generated UUID as the Supabase primary key.
                    // This keeps Room and Supabase IDs identical and never sends id = NULL.
                    postgrest.from(table).insert(localReminder.toRemote())
                    database.dueReminderDao().updateServerId(localReminder.id, localReminder.id)
                } else if (localReminder.serverId != null || item.operation == SyncOperation.CREATE.name) {
                    // UPDATE or CREATE where the remote identity is already known.
                    postgrest.from(table).upsert(localReminder.toRemote())
                }
                return
            }
        }

        val localData = fetchEntityData(item)
        if (localData != null) {
            val localUpdatedAt = getEntityUpdatedAt(localData)
            
            try {
                // Fetch remote metadata for conflict detection
                val remoteId = if (item.entityName == "REMINDERS") {
                    (localData as? ReminderEntity)?.serverId?.toString()
                } else {
                    item.entityId
                }

                if (remoteId != null) {
                    val remoteData = postgrest.from(table).select {
                        filter { eq("id", remoteId) }
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
                }
            } catch (e: Exception) {
                // Proceed with upsert if check fails
            }

            // EXPLICIT CASTING: Supabase upsert<T> requires the concrete type at compile time
            // to find the correct serializer. Passing 'Any' will fail.
            when (localData) {
                is PatientEntity -> postgrest.from(table).upsert(localData)
                is VisitEntity -> uploadVisit(table, localData)
                is VaccinationItemEntity -> postgrest.from(table).upsert(localData)
                is WasteEntity -> postgrest.from(table).upsert(localData)
                is ReminderEntity -> postgrest.from(table).upsert(localData.toRemote())
                is VaccineEntity -> postgrest.from(table).upsert(localData)
                is VaccineBatchEntity -> postgrest.from(table).upsert(localData)
                is InventoryTransactionEntity -> postgrest.from(table).upsert(localData)
                is FinanceEntity -> postgrest.from(table).upsert(localData)
                is AuditLogEntity -> postgrest.from(table).upsert(localData)
                is ProfileEntity -> postgrest.from(table).upsert(localData)
                is ConsultationEntity -> postgrest.from(table).upsert(localData)
                is ConsultationTodoEntity -> postgrest.from(table).upsert(localData)
                is VaccinationTodoEntity -> postgrest.from(table).upsert(localData)
                is BorrowEntity -> postgrest.from(table).upsert(localData)
                is PatientNotesEntity -> postgrest.from(table).upsert(localData)
            }
        }
    }

    // patient_visits.receipt_number is assigned by a database trigger (never by this app -
    // see 20260824_receipt_numbering.sql), so a freshly created visit is upserted with a
    // blank receiptNumber. Asking Postgrest to return the row lets us copy the
    // server-generated "NEO-YY/YY-NNNNNN" number back into Room right away, instead of
    // waiting for a later download to fill it in.
    private suspend fun uploadVisit(table: String, localData: VisitEntity) {
        if (localData.receiptNumber.isNotBlank()) {
            // Already has its number (normal edit path) - a plain upsert is enough.
            postgrest.from(table).upsert(localData)
            return
        }

        // Let a genuine upsert failure (network, RLS, etc.) propagate normally so the queue
        // item is retried/marked failed like any other entity - only the read-back below is
        // best-effort.
        val result = postgrest.from(table).upsert(localData) { select() }
        try {
            val savedRow = result.decodeSingleOrNull<VisitEntity>()
            if (savedRow != null && savedRow.receiptNumber.isNotBlank()) {
                database.vaccinationDao().updateReceiptNumber(localData.id, savedRow.receiptNumber)
            }
        } catch (e: Exception) {
            // The upsert itself already succeeded at this point; decoding the returned row
            // is only used to mirror the DB-assigned number locally right away, so don't fail
            // the sync item over it. The number will still be picked up on the next
            // download/refresh.
            android.util.Log.e("SyncRepository", "Could not read back receipt number for ${localData.id}", e)
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
            "REMINDERS" -> {
                val remote = json.decodeFromJsonElement<RemoteReminder>(element)
                val local = database.dueReminderDao().getReminderByStableId(
                    remote.patientId, 
                    remote.originalVisitId, 
                    remote.vaccineName,
                    remote.type
                )
                database.dueReminderDao().insertReminder(remote.toLocal(localId = local?.id))
            }
            "CONSULTATION" -> {
                val entity = json.decodeFromJsonElement<ConsultationEntity>(element)
                database.consultationDao().insertConsultation(entity.copy(isSynced = true))
            }
            "CONSULTATION_TODO" -> {
                val entity = json.decodeFromJsonElement<ConsultationTodoEntity>(element)
                database.patientTodoDao().insertConsultation(entity.copy(isSynced = true))
            }
            "VACCINATION_TODO" -> {
                val entity = json.decodeFromJsonElement<VaccinationTodoEntity>(element)
                database.patientTodoDao().insertVaccination(entity.copy(isSynced = true))
            }
            "BORROW" -> {
                val entity = json.decodeFromJsonElement<BorrowEntity>(element)
                database.borrowDao().insertRecord(entity.copy(isSynced = true))
            }
            "WASTE" -> {
                val entity = json.decodeFromJsonElement<WasteEntity>(element)
                database.wasteDao().insertWaste(entity.copy(isSynced = true))
            }
        }
    }

    private fun getEntityUpdatedAt(data: Any?): String {
        return when (data) {
            is PatientEntity -> data.updatedAt ?: ""
            is VisitEntity -> data.updatedAt ?: ""
            is WasteEntity -> data.updatedAt
            is ConsultationEntity -> data.updatedAt ?: ""
            is ConsultationTodoEntity -> data.updatedAt
            is VaccinationTodoEntity -> data.updatedAt
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
                "REMINDERS" -> database.dueReminderDao().getReminderById(entityId)
                "VACCINE" -> database.vaccineDao().getVaccineById(entityId)
                "BATCH" -> database.vaccineDao().getBatchById(entityId)
                "TRANSACTION", "INVENTORY_TRANSACTION" -> database.vaccineDao().getTransactionById(entityId)
                "FINANCE" -> database.financeDao().getTransactionById(entityId)
                "AUDIT_LOG" -> database.auditLogDao().getLogById(entityId)
                "PROFILE", "STAFF" -> database.profileDao().getProfileById(entityId)
                "CONSULTATION" -> database.consultationDao().getConsultationById(entityId)
                "CONSULTATION_TODO" -> database.patientTodoDao().getConsultationTodoById(entityId)
                "VACCINATION_TODO" -> database.patientTodoDao().getVaccinationTodoById(entityId)
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
