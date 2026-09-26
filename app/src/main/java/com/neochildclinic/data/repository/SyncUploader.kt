package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.core.model.SyncOperation
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

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
//   syncedSqlColumn   isSynced column name for markUploaded; null => no such column, skip
internal data class SyncEntityDescriptor(
    val tableName: String,
    val priority: Int,
    val fetch: suspend (AppDatabase, String) -> Any?,
    val updatedAt: (Any) -> String,
    val download: suspend (AppDatabase, Map<String, JsonElement>) -> Unit = { _, _ -> Unit },
    val pkColumn: String = "id",
    val syncedSqlColumn: String? = "isSynced"
)

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
            db.patientDao().insertPatient(entity.copy(isSynced = true))
        }
    ),

    "VACCINATION" to SyncEntityDescriptor(
        tableName = "patient_visits",
        priority = 2,
        fetch = { db, id -> db.vaccinationDao().getVaccinationById(id) },
        updatedAt = { (it as? VisitEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<VisitEntity>(JsonObject(m))
            db.vaccinationDao().insertVaccination(entity.copy(isSynced = true))
        }
    ),

    "VISIT" to SyncEntityDescriptor(
        tableName = "patient_visits",
        priority = 2,
        fetch = { db, id -> db.vaccinationDao().getVaccinationById(id) },
        updatedAt = { (it as? VisitEntity)?.updatedAt ?: "" },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<VisitEntity>(JsonObject(m))
            db.vaccinationDao().insertVaccination(entity.copy(isSynced = true))
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
            db.vaccineDao().insertVaccine(entity)
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
            db.vaccineDao().insertBatch(entity)
        },
        syncedSqlColumn = null
    ),

    "TRANSACTION" to SyncEntityDescriptor(
        tableName = "inventory_transactions",
        priority = 4,
        fetch = { db, id -> db.vaccineDao().getTransactionById(id) },
        updatedAt = { (it as? InventoryTransactionEntity)?.timestamp ?: "" },
        pkColumn = "transactionId"
    ),

    "INVENTORY_TRANSACTION" to SyncEntityDescriptor(
        tableName = "inventory_transactions",
        priority = 4,
        fetch = { db, id -> db.vaccineDao().getTransactionById(id) },
        updatedAt = { (it as? InventoryTransactionEntity)?.timestamp ?: "" },
        pkColumn = "transactionId"
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
        // preserved so the conflict check still favours the remote row for finance.
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
                com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
            } else {
                b.borrowedDate
            }
        },
        download = { db, m ->
            val entity = syncRemoteJson.decodeFromJsonElement<BorrowEntity>(JsonObject(m))
            db.borrowDao().insertRecord(entity.copy(isSynced = true))
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
internal class SyncUploader(
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
                val rows = postgrest.from(table).select {
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
                postgrest.from(table).delete {
                    filter { eq("id", item.entityId) }
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

        val localData = try {
            descriptor.fetch(database, item.entityId)
        } catch (e: Exception) {
            // Same containment the legacy fetchEntityData provided: a local DB read failure
            // yields "nothing to upload" (null) rather than failing the whole group.
            android.util.Log.e("SyncRepositoryImpl", "Error fetching data for sync: ${item.entityName} ID ${item.entityId}", e)
            null
        }
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

                val remoteUpdatedAt = com.neochildclinic.core.utils.PatientUtils.isoToLong(remoteUpdatedAtStr)
                val localUpdatedAtLong = com.neochildclinic.core.utils.PatientUtils.isoToLong(localUpdatedAt)

                if (remoteUpdatedAt > localUpdatedAtLong) {
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
                is InventoryTransactionEntity -> uploadWithExplicitNulls(table, localData, InventoryTransactionEntity.serializer())
                is FinanceEntity -> uploadWithExplicitNulls(table, localData, FinanceEntity.serializer())
                is AuditLogEntity -> postgrest.from(table).upsert(localData)
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
    //    bring it up to purchaseQuantity via the trigger.
    //  - on every other operation, omit remaining_quantity entirely so the column is left
    //    untouched by this upsert; whatever accompanying transaction row was inserted for
    //    that change is what the trigger reacts to.
    // Room keeps computing remaining_quantity locally as before, for offline-first display.
    private suspend fun uploadBatch(table: String, localData: VaccineBatchEntity, isCreate: Boolean) {
        val fullJson = kotlinx.serialization.json.Json.encodeToJsonElement(
            VaccineBatchEntity.serializer(), localData
        ) as JsonObject
        val fields = fullJson.toMutableMap()
        if (isCreate) {
            fields["remaining_quantity"] = JsonPrimitive(0)
        } else {
            fields.remove("remaining_quantity")
        }
        postgrest.from(table).upsert(JsonObject(fields))
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
        serializer: kotlinx.serialization.KSerializer<T>
    ) {
        // Cast to JsonObject to match the exact call shape uploadBatch already uses below
        // (postgrest.from(table).upsert(JsonObject(...))) - a data class always encodes to
        // a JSON object, never another JsonElement subtype, so this is safe.
        val payload = explicitNullsJson.encodeToJsonElement(serializer, data) as JsonObject
        postgrest.from(table).upsert(payload)
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