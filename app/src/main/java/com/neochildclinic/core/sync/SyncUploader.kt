package com.neochildclinic.core.sync

import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.domain.model.SyncOperation
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject

// Decoder used for remote rows pulled back into the local DB (downloadAndReplaceLocal).
// ignoreUnknownKeys/coerceInputValues: the remote row may carry columns Room doesn't know
// (server-side only, e.g. receipt_number triggers) or values Room types coerce resiliently.
private val syncRemoteJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

// Single source of truth for "what is a syncable entity". Every name-keyed when switch
// (entity table, priority, local fetch, updated-at extractor, markUploaded column, remote
// download) used to enumerate the ~20 entity names in lockstep across parallel branches;
// adding one entity meant editing five call sites, and a missed branch degraded silently.
// Now a new entity is registered once, here, and the lookups below fail loudly (Illegal-
// ArgumentException) on an unknown name instead of defaulting (e.g. priority -> 100).
//
//   tableName         remote Supabase table
//   priority          FK-ordering weight (1 = parent, 5 = leaf); see orderPendingIntoGroups
//   fetch             load the local row for upload (null => nothing to upload)
//   updatedAt         extract the row's last-updated value for remote-newer conflict checks
//   download          write a remote-newer row back into Room (no-op default preserves the
//                     legacy behavior of tables that never downloaded: no branch ran)
//   pkColumn          local PK column for the markUploaded UPDATE
//   remotePkColumn    the column PostgREST filters on for a remote DELETE. Usually the same as
//                     pkColumn, but NOT for inventory_transactions, whose local column is
//                     `transactionId` while the remote column is `id`: filtering on the local
//                     name made PostgREST reject the delete (PGRST100), so hard-deletes never
//                     reached the server and the stock ledger kept rows the device deleted.
//   syncedSqlColumn   isSynced column name for markUploaded; null => no such column, skip
internal data class SyncEntityDescriptor(
    val tableName: String,
    val priority: Int,
    val fetch: suspend (AppDatabase, String) -> Any?,
    val updatedAt: (Any) -> String,
    val download: suspend (AppDatabase, Map<String, JsonElement>) -> Unit = { _, _ -> Unit },
    val pkColumn: String = "id",
    val remotePkColumn: String? = null,
    val syncedSqlColumn: String? = "isSynced"
) {
    /** Column to filter a remote DELETE on. Falls back to the local PK name. */
    val deleteFilterColumn: String get() = remotePkColumn ?: pkColumn
}

// Built once, no AppDatabase in scope - every accessor takes the DB as a parameter, so the
// registry is a plain static map safe to share and to unit-test priority/ordering against.
internal val SYNC_ENTITY_REGISTRY: Map<String, SyncEntityDescriptor> = mapOf(

    "PATIENT" to SyncEntityDescriptor(
        tableName = "patients",
        priority = 1,
        fetch = { db, id ->
            val entity = db.patientDao().getPatientById(id)
            // Strip 'TEMP-' prefix before uploading
            if (entity?.patientClinicId?.startsWith("TEMP-") == true) {
                entity.copy(patientClinicId = null)
            } else {
                entity
            }
        },
        updatedAt = { (it as? PatientEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<PatientEntity>(JsonObject(m))
            db.patientDao().upsertPatient(entity.copy(isSynced = true))
        }
    ),

    "VACCINATION" to SyncEntityDescriptor(
        tableName = "patient_visits",
        priority = 2,
        fetch = { db, id -> db.vaccinationDao().getVaccinationById(id) },
        updatedAt = { (it as? VisitEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<VisitEntity>(JsonObject(m))
            db.vaccinationDao().upsertVaccination(entity.copy(isSynced = true))
        }
    ),

    "VISIT" to SyncEntityDescriptor(
        tableName = "patient_visits",
        priority = 2,
        fetch = { db, id -> db.vaccinationDao().getVaccinationById(id) },
        updatedAt = { (it as? VisitEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<VisitEntity>(JsonObject(m))
            db.vaccinationDao().upsertVaccination(entity.copy(isSynced = true))
        }
    ),

    "VACCINATION_ITEM" to SyncEntityDescriptor(
        tableName = "vaccination_items",
        priority = 3,
        // No updatedAt column - item rows are replaced via DELETE+CREATE under one visit,
        // so last-write-wins conflict checks on items are meaningless (excluded upstream).
        fetch = { db, id -> db.vaccinationItemDao().getItemById(id) },
        updatedAt = { "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<VaccinationItemEntity>(JsonObject(m))
            db.vaccinationItemDao().insertItems(listOf(entity))
        },
        syncedSqlColumn = null
    ),

    "WASTE" to SyncEntityDescriptor(
        tableName = "waste_records",
        priority = 3,
        fetch = { db, id -> db.wasteDao().getWasteById(id) },
        updatedAt = { (it as? WasteEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<WasteEntity>(JsonObject(m))
            db.wasteDao().insertWaste(entity.copy(isSynced = true))
        }
    ),

    "REMINDERS" to SyncEntityDescriptor(
        tableName = "reminders",
        priority = 5,
        // REMINDERS upload has its own specialized path in uploadEntity (server-generated
        // IDs); fetch/download here serve the download-and-replace conflict side only.
        fetch = { db, id -> db.dueReminderDao().getReminderById(id) },
        updatedAt = { (it as? ReminderEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val remote = syncRemoteJson.decodeFromJsonElement<RemoteReminder>(JsonObject(m))
            val local = db.dueReminderDao().getReminderByStableId(
                remote.patientId,
                remote.originalVisitId,
                remote.vaccineName,
                remote.type
            )
            db.dueReminderDao().insertReminder(remote.toLocal(localId = local?.id))
        }
    ),

    "VACCINE" to SyncEntityDescriptor(
        tableName = "vaccines",
        priority = 1,
        fetch = { db, id -> db.vaccineDao().getVaccineById(id) },
        updatedAt = { (it as? VaccineEntity)?.lastUpdated ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<VaccineEntity>(JsonObject(m))
            db.vaccineDao().upsertVaccine(entity)
        },
        syncedSqlColumn = null
    ),

    "BATCH" to SyncEntityDescriptor(
        tableName = "vaccine_batches",
        priority = 2,
        fetch = { db, id -> db.vaccineDao().getBatchById(id) },
        updatedAt = { (it as? VaccineBatchEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<VaccineBatchEntity>(JsonObject(m))
            db.vaccineDao().upsertBatch(entity)
        },
        syncedSqlColumn = null
    ),

    "TRANSACTION" to SyncEntityDescriptor(
        tableName = "inventory_transactions",
        priority = 4,
        fetch = { db, id -> db.vaccineDao().getTransactionById(id) },
        updatedAt = { (it as? InventoryTransactionEntity)?.timestamp ?: "" },
        pkColumn = "transactionId",
        remotePkColumn = "id"
    ),

    "INVENTORY_TRANSACTION" to SyncEntityDescriptor(
        tableName = "inventory_transactions",
        priority = 4,
        fetch = { db, id -> db.vaccineDao().getTransactionById(id) },
        updatedAt = { (it as? InventoryTransactionEntity)?.timestamp ?: "" },
        pkColumn = "transactionId",
        remotePkColumn = "id"
    ),

    "PATIENT_NOTE" to SyncEntityDescriptor(
        tableName = "patient_notes",
        priority = 5,
        fetch = { db, id -> db.patientNotesDao().getNoteById(id) },
        updatedAt = { (it as? PatientNotesEntity)?.timestamp ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<PatientNotesEntity>(JsonObject(m))
            db.patientNotesDao().insertNote(entity.copy(isSynced = true))
        }
    ),

    "FINANCE" to SyncEntityDescriptor(
        tableName = "finance_transactions",
        priority = 4,
        fetch = { db, id -> db.financeDao().getTransactionById(id) },
        // No FinanceEntity branch in the legacy getEntityUpdatedAt switch (fell to else -> "");
        // kept blank: finance has no local timestamp, so the conflict check cannot order the
        // rows and the local edit uploads instead of being silently dropped (BUG-005 fix).
        updatedAt = { "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<FinanceEntity>(JsonObject(m))
            db.financeDao().insertTransaction(entity.copy(isSynced = true))
        }
    ),

    "EXPENSE" to SyncEntityDescriptor(
        tableName = "expenses",
        priority = 4,
        fetch = { db, id -> db.expenseDao().getExpenseById(id) },
        updatedAt = { (it as? ExpenseEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<ExpenseEntity>(JsonObject(m))
            db.expenseDao().insertExpense(entity.copy(isSynced = true))
        }
    ),

    "PROFILE" to SyncEntityDescriptor(
        tableName = "profiles",
        // No branch in the legacy getEntityPriority switch - it defaulted to 100 (sorted
        // last for CREATE/UPDATE, first for DELETE). Preserved exactly.
        priority = 100,
        fetch = { db, id -> db.profileDao().getProfileById(id) },
        updatedAt = { "" },
        // profiles never downloads: a conflict check reaching here falls through the legacy
        // when-less path too (no branch matched), so the no-op default is the same behavior.
        syncedSqlColumn = null
    ),

    "STAFF" to SyncEntityDescriptor(
        tableName = "profiles",
        priority = 100,
        fetch = { db, id -> db.profileDao().getProfileById(id) },
        updatedAt = { "" },
        syncedSqlColumn = null
    ),

    "BORROW" to SyncEntityDescriptor(
        tableName = "borrow_records",
        priority = 3,
        fetch = { db, id -> db.borrowDao().getRecordById(id) },
        // A returned borrow is a local status update. borrow_records does not have a
        // client-side updated_at field, so using the original borrowed date makes the
        // conflict check incorrectly treat the remote row as newer and download the old
        // is_returned=false row. Use the current timestamp while this pending update is
        // being uploaded so the explicit return wins.
        updatedAt = {
            val b = it as? BorrowEntity
            if (b == null) "" else if (b.isReturned) {
                com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
            } else {
                b.borrowedDate
            }
        },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<BorrowEntity>(JsonObject(m))
            db.borrowDao().upsertRecord(entity.copy(isSynced = true))
        }
    ),

    "BORROW_RETURN" to SyncEntityDescriptor(
        tableName = "borrow_returns",
        priority = 4,
        fetch = { db, id -> db.borrowReturnDao().getById(id) },
        updatedAt = {
            val b = it as? BorrowReturnEntity
            if (b == null) "" else b.createdAt.ifBlank { b.returnedDate }
        },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<BorrowReturnEntity>(JsonObject(m))
            db.borrowReturnDao().insert(entity.copy(isSynced = true))
        },
        syncedSqlColumn = "is_synced"
    ),

    "AUDIT_LOG" to SyncEntityDescriptor(
        tableName = "audit_logs",
        priority = 5,
        fetch = { db, id -> db.auditLogDao().getLogById(id) },
        updatedAt = { (it as? AuditLogEntity)?.timestamp ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<AuditLogEntity>(JsonObject(m))
            db.auditLogDao().insertLog(entity.copy(isSynced = true))
        }
    ),

    "CONSULTATION" to SyncEntityDescriptor(
        tableName = "consultations",
        priority = 3,
        fetch = { db, id -> db.consultationDao().getConsultationById(id) },
        updatedAt = { (it as? ConsultationEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<ConsultationEntity>(JsonObject(m))
            db.consultationDao().insertConsultation(entity.copy(isSynced = true))
        }
    ),

    "CONSULTATION_TODO" to SyncEntityDescriptor(
        tableName = "consultation_todos",
        priority = 3,
        fetch = { db, id -> db.patientTodoDao().getConsultationTodoById(id) },
        updatedAt = { (it as? ConsultationTodoEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<ConsultationTodoEntity>(JsonObject(m))
            db.patientTodoDao().insertConsultation(entity.copy(isSynced = true))
        },
        syncedSqlColumn = "is_synced"
    ),

    "VACCINATION_TODO" to SyncEntityDescriptor(
        tableName = "vaccination_todos",
        priority = 3,
        fetch = { db, id -> db.patientTodoDao().getVaccinationTodoById(id) },
        updatedAt = { (it as? VaccinationTodoEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<VaccinationTodoEntity>(JsonObject(m))
            db.patientTodoDao().insertVaccination(entity.copy(isSynced = true))
        },
        syncedSqlColumn = "is_synced"
    ),

    "PERSONAL_REMINDER" to SyncEntityDescriptor(
        tableName = "personal_vaccine_reminders",
        priority = 5,
        fetch = { db, id -> db.personalReminderDao().getById(id) },
        updatedAt = { (it as? PersonalReminderEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<PersonalReminderEntity>(JsonObject(m))
            db.personalReminderDao().insert(entity.copy(isSynced = true))
        },
        syncedSqlColumn = "is_synced"
    ),

    "DOCTOR_WEEKLY_SLOT" to SyncEntityDescriptor(
        tableName = "doctor_weekly_slots",
        priority = 2,
        fetch = { db, id -> db.doctorAvailabilityDao().getWeeklySlotById(id) },
        updatedAt = { (it as? DoctorWeeklySlotEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<DoctorWeeklySlotEntity>(JsonObject(m))
            db.doctorAvailabilityDao().upsertWeeklySlot(entity.copy(isSynced = true))
        },
        syncedSqlColumn = "is_synced"
    ),

    "DOCTOR_SLOT_EXCEPTION" to SyncEntityDescriptor(
        tableName = "doctor_slot_exceptions",
        priority = 3,
        fetch = { db, id -> db.doctorAvailabilityDao().getExceptionById(id) },
        updatedAt = { (it as? DoctorSlotExceptionEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<DoctorSlotExceptionEntity>(JsonObject(m))
            db.doctorAvailabilityDao().upsertException(entity.copy(isSynced = true))
        },
        syncedSqlColumn = "is_synced"
    )
)

// Per-entity SyncRepositoryImpl core: every remote write/read mapping, conflict check, and
// remote-into-local download for the sync queue. Owns no queue state itself - the batch
// orchestration (grouping, session gate, retries/FAILED classification) stays in
// SyncRepositoryImpl and drives this class item by item.
class SyncUploader @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest
) {

    // After a successful upload, flip the local row's isSynced so pull guards of the form
    // (local == null || local.isSynced) stop permanently skipping remote updates for it.
    // Tables without an isSynced column (syncedSqlColumn == null in the registry: profiles/
    // vaccines/vaccine_batches/vaccination_items) rely on queue-only guards and are skipped
    // here. DELETE ops are skipped: the local row is already gone.
    suspend fun markUploaded(item: SyncQueueEntity) {
        if (item.operation == SyncOperation.DELETE.name) return
        val descriptor = SYNC_ENTITY_REGISTRY[item.entityName] ?: return
        val syncedColumn = descriptor.syncedSqlColumn ?: return
        try {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE ${descriptor.tableName} SET $syncedColumn = 1 WHERE ${descriptor.pkColumn} = ?",
                arrayOf(item.entityId)
            )
        } catch (e: Exception) {
            android.util.Log.w("SyncRepositoryImpl", "Failed to mark ${descriptor.tableName}.${item.entityId} synced", e)
        }
    }

    // Groups items by their target table and issues one `id IN (...)` SELECT per table,
    // instead of the one SELECT per item this replaces (large-data scalability pass,
    // Section 6: Batched Synchronization). REMINDERS and DELETE items are excluded by the
    // caller because neither ever reached the per-item conflict-check this replaces:
    // REMINDERS resolves its own identity/return path earlier in uploadEntity, and DELETE
    // returns before the conflict-check block too - so behavior for both is unchanged.
    // A read failure for one table's batch is logged and simply leaves that table's items
    // out of the map, matching the previous per-item try/catch, which also proceeded with
    // a plain upsert whenever the live check failed.
    suspend fun fetchRemoteConflictData(
        items: List<SyncQueueEntity>
    ): Map<String, Map<String, JsonElement>> {
        if (items.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Map<String, JsonElement>>()
        val byTable = items.groupBy { SYNC_ENTITY_REGISTRY[it.entityName]?.tableName }

        for ((table, tableItems) in byTable) {
            if (table == null) continue
            val ids = tableItems.map { it.entityId }.distinct()
            if (ids.isEmpty()) continue

            try {
                // Project only the columns the conflict consumer reads (remoteData["id"],
                // remoteData["updated_at"]). updated_at exists on every public table
                // (migration 20260816 add-if-not-exists + set_updated_at trigger), so the
                // projection cannot 400 like the legacy `last_updated` fallback could - that
                // column exists nowhere in the schema, the constraint branch was dead code;
                // the projected select drops it alongside every other unused field of the
                // formerly full-row read.
                val rows = postgrest.from(table).select(columns = Columns.list("id", "updated_at")) {
                    filter { isIn("id", ids) }
                }.decodeList<Map<String, JsonElement>>()

                for (row in rows) {
                    val id = row["id"]?.toString()?.trim('"') ?: continue
                    result["$table:$id"] = row
                }
            } catch (e: Exception) {
                android.util.Log.w("SyncRepositoryImpl", "Batched conflict-check read failed for $table", e)
            }
        }

        return result
    }

    suspend fun uploadEntity(
        item: SyncQueueEntity,
        remoteConflictData: Map<String, Map<String, JsonElement>>
    ) {
        val descriptor = SYNC_ENTITY_REGISTRY[item.entityName]
            ?: throw IllegalArgumentException("Unknown entity: ${item.entityName}")
        val table = descriptor.tableName

        if (item.operation == SyncOperation.DELETE.name) {
            // REMINDERS: entityId is serverId ?: localId captured at enqueue time — the
            // local row is already hard-deleted, so never re-read it here. Other entities
            // use local UUID == remote PK.
            //
            // Idempotency: a DELETE must be safe to run more than once (a retried request
            // whose first attempt actually succeeded server-side but whose response was
            // lost to a timeout/dropped connection, or two overlapping sync attempts
            // racing on the same row). A normal "delete where id = X, zero rows matched" is
            // not an error at all under PostgREST - it just deletes nothing and returns
            // normally - so most of the time there is nothing to catch here. The explicit
            // catch below is only for the case where the backend does surface a "not
            // found"-shaped response for it: that still means the desired end state (the
            // row is absent) was already reached, so it's treated as success rather than
            // failing/retrying a delete that already worked. Any other error (permission
            // denial, a genuine network/server failure, etc.) is rethrown unchanged and
            // handled by the normal retry/failure path in processNextItems.
            try {
                // Filter on the REMOTE column name (descriptor.deleteFilterColumn), not the
                // local one: inventory_transactions is `transactionId` in Room but `id` in
                // Postgres, and the old filter sent the local name to PostgREST, which answered
                // PGRST100 (400). The catch below only treats 404 as success, so the error was
                // rethrown and the item failed permanently - the remote row survived.
                postgrest.from(table).delete {
                    filter { eq(descriptor.deleteFilterColumn, item.entityId) }
                }
            } catch (e: io.github.jan.supabase.exceptions.RestException) {
                if (e.statusCode != 404) throw e
            }
            return
        }

        // Specialized logic for REMINDERS to handle server-generated IDs
        if (item.entityName == "REMINDERS") {
            val localReminder = database.dueReminderDao().getReminderById(item.entityId)
            if (localReminder != null) {
                // Always upsert on the primary key (the locally generated UUID), for both CREATE
                // and UPDATE. The old CREATE-only insert path was not idempotent: if the insert
                // committed server-side but the reply was lost, serverId stayed null, the retry
                // inserted again, PostgREST returned 409, and the item failed permanently.
                // It was also silent for an UPDATE whose row had no serverId - neither branch
                // matched, the item was marked uploaded and deleted, and the edit was never pushed.
                postgrest.from(table).upsert(localReminder.toRemote())
                database.dueReminderDao().updateServerId(localReminder.id, localReminder.id)
                return
            }
        }

        val localData = descriptor.fetch(database, item.entityId)
        if (localData != null) {
            val localUpdatedAt = descriptor.updatedAt(localData)

            // Conflict check: was resolved with its own SELECT per item before this change;
            // now reads from the batch-fetched map built once per sync group in
            // processNextItems (see fetchRemoteConflictData). REMINDERS never reaches this
            // point (it returns earlier above), so the key is always table:entityId.
            val remoteData = remoteConflictData["$table:${item.entityId}"]
            if (remoteData != null) {
                val remoteUpdatedAtStr = remoteData["updated_at"]?.toString()?.replace("\"", "")
                    ?: remoteData["last_updated"]?.toString()?.replace("\"", "")

                val remoteUpdatedAt = com.neochildclinic.core.common.PatientUtils.isoToLong(remoteUpdatedAtStr)
                val localUpdatedAtLong = com.neochildclinic.core.common.PatientUtils.isoToLong(localUpdatedAt)

                // BUG-005: localUpdatedAtLong == 0 means the local entity never stamped a
                // timestamp (FINANCE/VACCINATION_ITEM/PROFILE don't maintain one), so its
                // age is unknown - downloading here silently discarded the local edit and
                // marked it synced. Unknown age must lose to nothing: let the edit upload.
                //
                // Pending-queue guard (docs/SYNC.md conflict policy: "local row has a
                // pending queue entry -> local is authoritative until pushed"): while some
                // OTHER unacknowledged edit for this entity is still queued, the local row
                // must never be replaced by the remote copy. Downloading here would destroy
                // the queued local edit: the download overwrites it locally and the queue row
                // is then deleted as "uploaded", so the edit is lost with no trace.
                //
                // The in-flight item is excluded (NCC-020). It is SYNCING right now, not
                // SYNCED, so testing it too made this whole branch unreachable - the remote
                // never won, and the conflict policy below never ran for anybody.
                if (remoteUpdatedAt > localUpdatedAtLong && localUpdatedAtLong > 0 &&
                    !database.syncQueueDao()
                        .isUnsyncedExcluding(item.entityName, item.entityId, item.queueId)
                ) {
                    // REMOTE IS NEWER: Sync back to local (Self-healing)
                    descriptor.download(database, remoteData)
                    return
                }
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
                is VaccineBatchEntity -> uploadBatch(table, localData, item.operation == SyncOperation.CREATE.name)
                // visitId is nullable on both of these and gets explicitly set back to
                // null by VaccinationRepositoryImpl.deleteVaccination()'s clearVisitLink()
                // step, to sever the link before the visit itself is hard-deleted (see
                // that function's step 2b). A plain upsert(localData) here would run the
                // Postgrest client's default Json encoder, which - like the encoder used
                // everywhere else in this app - omits a null field from the outgoing JSON
                // rather than sending it as null (the exact behavior uploadBatch's
                // remaining_quantity comment above documents and relies on). PostgREST
                // treats an omitted key as "leave this column untouched", so a plain
                // upsert() here would silently fail to clear visit_id remotely: the local
                // row shows visitId = null, but the FK to patient_visits stays intact on
                // Supabase, and the visit's own DELETE later fails with
                // "..._visit_id_fkey" (23503) even though the unlink "succeeded". Force an
                // explicit-nulls encode so a genuine null on this field is actually sent.
                // A stock-ledger row is append-only. A CREATE retry must be INSERT ... ON
                // CONFLICT DO NOTHING, not the default upsert's INSERT ... ON CONFLICT DO
                // UPDATE: the latter fires the stock trigger's UPDATE branch on every lost-
                // response retry. The 20261021002000 trigger fix makes that branch idempotent for
                // identical rows, but insert-only keeps retries out of it entirely.
                is InventoryTransactionEntity -> uploadWithExplicitNulls(
                    table = table,
                    data = localData,
                    serializer = InventoryTransactionEntity.serializer(),
                    insertOnly = item.operation == SyncOperation.CREATE.name
                )
                is FinanceEntity -> uploadWithExplicitNulls(table, localData, FinanceEntity.serializer())
                // audit_logs is append-only on the server: SELECT + INSERT policies only, no
                // UPDATE and no DELETE (see 20260929000000_security_baseline.sql). upsert()
                // compiles to INSERT ... ON CONFLICT DO UPDATE, which needs the UPDATE policy
                // as soon as the id exists - so any retry after a lost response failed RLS
                // (42501), was classified permanent, and the audit entry was lost. Insert with
                // duplicate-tolerance instead: append-only stays append-only.
                is AuditLogEntity -> postgrest.from(table).upsert(localData) {
                    ignoreDuplicates = true
                }
                // profiles has no client-side INSERT policy at all (only the manage-staff
                // edge function, using the service role, is allowed to create rows there -
                // see the migration comment in 20260816_security_hardening.sql). upsert()
                // compiles to INSERT ... ON CONFLICT DO UPDATE, and Postgres RLS requires
                // BOTH the INSERT and UPDATE policies to pass for that statement even when
                // the row already exists and only the UPDATE arm will run - so with no
                // INSERT policy at all, every profile sync failed RLS, including ordinary
                // self-edits (name/phone) that the UPDATE-only policy would otherwise allow
                // fine. A plain UPDATE only needs the UPDATE policy, matching how
                // PatientViewModel already (correctly) writes to profiles elsewhere.
                is ProfileEntity -> postgrest.from(table).update(localData) {
                    filter { eq("id", localData.id) }
                }
                is ConsultationEntity -> postgrest.from(table).upsert(localData)
                is ConsultationTodoEntity -> postgrest.from(table).upsert(localData)
                is VaccinationTodoEntity -> postgrest.from(table).upsert(localData)
                is BorrowEntity -> postgrest.from(table).upsert(localData)
                is BorrowReturnEntity -> postgrest.from(table).upsert(localData)
                is PatientNotesEntity -> postgrest.from(table).upsert(localData)
                is PersonalReminderEntity -> postgrest.from(table).upsert(localData)
                is ExpenseEntity -> postgrest.from(table).upsert(localData)
                is DoctorWeeklySlotEntity -> postgrest.from(table).upsert(localData)
                is DoctorSlotExceptionEntity -> postgrest.from(table).upsert(localData)
            }
        } else {
            // The local row this queue item was queued to push is gone, so there is
            // nothing to upload - but that is never what a CREATE/UPDATE enqueue meant,
            // and silently dropping it here hid bugs (audit finding 5: deleted line items
            // were enqueued as UPDATE after their local rows were hard-deleted). DELETE
            // never reaches this branch (it returns above and stays idempotent).
            android.util.Log.w(
                "SyncRepositoryImpl",
                "No local row for ${item.entityName} ${item.entityId} (${item.operation}); nothing uploaded"
            )
        }
    }

    // vaccine_batches.remaining_quantity is maintained server-side by the
    // tr_update_batch_stock trigger, which adjusts it by NEW.quantity for every
    // inventory_transactions row this app inserts (positive for purchases/restocks,
    // negative for deductions - see InventoryRepositoryImpl). If this upsert also sent
    // remaining_quantity, every change would be applied twice: once directly by the app,
    // once again by the trigger reacting to the paired transaction row. So:
    //  - on CREATE, send remaining_quantity = 0 (satisfies any NOT NULL constraint) and
    //    let the paired PURCHASE transaction - inserted right after, same sync group -
    //    bring it up to purchaseQuantity via the trigger. The insert is ON CONFLICT DO
    //    NOTHING: a CREATE replayed after a lost response must not overwrite the stock
    //    the PURCHASE already applied (SQL-008).
    //  - on every other operation, omit remaining_quantity entirely so the column is left
    //    untouched by this upsert; whatever accompanying transaction row was inserted for
    //    that change is what the trigger reacts to.
    // Room keeps computing remaining_quantity locally as before, for offline-first display.
    private suspend fun uploadBatch(table: String, localData: VaccineBatchEntity, isCreate: Boolean) {
        val upsert = batchUpsert(localData, isCreate)
        postgrest.from(table).upsert(upsert.fields) {
            if (upsert.insertOnly) ignoreDuplicates = true
        }
    }

    // Re-encodes with explicitNulls = true before upserting, so a Kotlin property that is
    // genuinely null (e.g. FinanceEntity/InventoryTransactionEntity.visitId after
    // clearVisitLink()) is sent to Postgrest as "column": null instead of being dropped
    // from the JSON body entirely. Needed anywhere a nullable column must be actively
    // cleared - as opposed to uploadBatch's remaining_quantity, which deliberately wants
    // the opposite (omit the key so the column is left alone).
    private val explicitNullsJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    private suspend fun <T> uploadWithExplicitNulls(
        table: String,
        data: T,
        serializer: kotlinx.serialization.KSerializer<T>,
        insertOnly: Boolean = false
    ) {
        // Cast to JsonObject to match the exact call shape uploadBatch already uses below
        // (postgrest.from(table).upsert(JsonObject(...))) - a data class always encodes to
        // a JSON object, never another JsonElement subtype, so this is safe.
        val payload = explicitNullsJson.encodeToJsonElement(serializer, data) as JsonObject
        postgrest.from(table).upsert(payload) {
            if (insertOnly) ignoreDuplicates = true
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
            android.util.Log.e("SyncRepositoryImpl", "Could not read back receipt number for ${localData.id}", e)
        }
    }
}

/**
 * Payload and retry mode for a vaccine_batches write.
 *
 * CREATE must be INSERT ... ON CONFLICT DO NOTHING. The queue item is only deleted after a
 * successful response, so a response lost after the INSERT committed replays the CREATE; a
 * DO UPDATE would write remaining_quantity = 0 over the stock the paired PURCHASE already
 * added, and the PURCHASE retry is insert-only, so nothing restores it (SQL-008).
 */
internal data class BatchUpsert(val fields: JsonObject, val insertOnly: Boolean)

internal fun batchUpsert(localData: VaccineBatchEntity, isCreate: Boolean): BatchUpsert {
    val fields = (kotlinx.serialization.json.Json.encodeToJsonElement(
        VaccineBatchEntity.serializer(), localData
    ) as JsonObject).toMutableMap()
    if (isCreate) {
        fields["remaining_quantity"] = JsonPrimitive(0)
    } else {
        // A plain upsert is correct here: the paired transaction row carries the change.
        fields.remove("remaining_quantity")
    }
    return BatchUpsert(JsonObject(fields), insertOnly = isCreate)
}