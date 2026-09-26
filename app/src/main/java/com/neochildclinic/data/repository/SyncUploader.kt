package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.core.model.SyncOperation
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

// Per-entity SyncRepositoryImpl core: every remote write/read mapping, conflict check, and
// remote-into-local download for the sync queue. Owns no queue state itself - the batch
// orchestration (grouping, session gate, retries/FAILED classification) stays in
// SyncRepositoryImpl and drives this class item by item.
internal class SyncUploader(
    private val database: AppDatabase,
    private val postgrest: Postgrest
) {

    // Shared entityName -> Supabase table mapping, used both by uploadEntity (which still
    // throws on an unrecognized entityName, exactly as before) and by the batched
    // conflict-check prefetch below (which just skips grouping for entries it doesn't
    // recognize, since uploadEntity will throw on them anyway when its turn comes).
    private fun entityTable(entityName: String): String? = when (entityName) {
        "PATIENT" -> "patients"
        "VACCINATION", "VISIT" -> "patient_visits"
        "VACCINATION_ITEM" -> "vaccination_items"
        "WASTE" -> "waste_records"
        "REMINDERS" -> "reminders"
        "VACCINE" -> "vaccines"
        "BATCH" -> "vaccine_batches"
        "TRANSACTION", "INVENTORY_TRANSACTION" -> "inventory_transactions"
        "PATIENT_NOTE" -> "patient_notes"
        "FINANCE" -> "finance_transactions"
        "EXPENSE" -> "expenses"
        "PROFILE", "STAFF" -> "profiles"
        "BORROW" -> "borrow_records"
        "BORROW_RETURN" -> "borrow_returns"
        "AUDIT_LOG" -> "audit_logs"
        "CONSULTATION" -> "consultations"
        "CONSULTATION_TODO" -> "consultation_todos"
        "VACCINATION_TODO" -> "vaccination_todos"
        "PERSONAL_REMINDER" -> "personal_vaccine_reminders"
        "DOCTOR_WEEKLY_SLOT" -> "doctor_weekly_slots"
        "DOCTOR_SLOT_EXCEPTION" -> "doctor_slot_exceptions"
        else -> null
    }

    // After a successful upload, flip the local row's isSynced so pull guards of the form
    // (local == null || local.isSynced) stop permanently skipping remote updates for it.
    // Tables without an isSynced column (profiles/vaccines/vaccine_batches/
    // vaccination_items) rely on queue-only guards and are skipped here. DELETE ops are
    // skipped: the local row is already gone.
    suspend fun markUploaded(item: SyncQueueEntity) {
        if (item.operation == SyncOperation.DELETE.name) return
        // Tables without an isSynced column (profiles/vaccines/vaccine_batches/
        // vaccination_items) rely on queue-only guards and are skipped here.
        val table = entityTable(item.entityName) ?: return
        when (item.entityName) {
            "PROFILE", "STAFF", "VACCINE", "BATCH", "VACCINATION_ITEM" -> return
            else -> {}
        }
        val pk = if (item.entityName == "TRANSACTION" || item.entityName == "INVENTORY_TRANSACTION") "transactionId" else "id"
        val col = when (item.entityName) {
            "BORROW_RETURN", "CONSULTATION_TODO", "VACCINATION_TODO", "PERSONAL_REMINDER",
            "DOCTOR_WEEKLY_SLOT", "DOCTOR_SLOT_EXCEPTION" -> "is_synced"
            else -> "isSynced"
        }
        try {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE $table SET $col = 1 WHERE $pk = ?",
                arrayOf(item.entityId)
            )
        } catch (e: Exception) {
            android.util.Log.w("SyncRepositoryImpl", "Failed to mark $table.${item.entityId} synced", e)
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
    ): Map<String, Map<String, kotlinx.serialization.json.JsonElement>> {
        if (items.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Map<String, kotlinx.serialization.json.JsonElement>>()
        val byTable = items.groupBy { entityTable(it.entityName) }

        for ((table, tableItems) in byTable) {
            if (table == null) continue
            val ids = tableItems.map { it.entityId }.distinct()
            if (ids.isEmpty()) continue

            try {
                val rows = postgrest.from(table).select {
                    filter { isIn("id", ids) }
                }.decodeList<Map<String, kotlinx.serialization.json.JsonElement>>()

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
        remoteConflictData: Map<String, Map<String, kotlinx.serialization.json.JsonElement>>
    ) {
        val table = entityTable(item.entityName)
            ?: throw IllegalArgumentException("Unknown entity: ${item.entityName}")

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

        val localData = fetchEntityData(item)
        if (localData != null) {
            val localUpdatedAt = getEntityUpdatedAt(localData)

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
                    downloadAndReplaceLocal(item.entityName, remoteData)
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
            fields["remaining_quantity"] = kotlinx.serialization.json.JsonPrimitive(0)
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

    suspend fun downloadAndReplaceLocal(entityName: String, remoteMap: Map<String, kotlinx.serialization.json.JsonElement>) {
        val json = kotlinx.serialization.json.Json { 
            ignoreUnknownKeys = true 
            coerceInputValues = true
        }
        val element = JsonObject(remoteMap)
        
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
            "BORROW_RETURN" -> {
                val entity = json.decodeFromJsonElement<BorrowReturnEntity>(element)
                database.borrowReturnDao().insert(entity.copy(isSynced = true))
            }
            "WASTE" -> {
                val entity = json.decodeFromJsonElement<WasteEntity>(element)
                database.wasteDao().insertWaste(entity.copy(isSynced = true))
            }
            "PERSONAL_REMINDER" -> {
                val entity = json.decodeFromJsonElement<PersonalReminderEntity>(element)
                database.personalReminderDao().insert(entity.copy(isSynced = true))
            }
            "EXPENSE" -> {
                val entity = json.decodeFromJsonElement<ExpenseEntity>(element)
                database.expenseDao().insertExpense(entity.copy(isSynced = true))
            }
            "DOCTOR_WEEKLY_SLOT" -> {
                val entity = json.decodeFromJsonElement<DoctorWeeklySlotEntity>(element)
                database.doctorAvailabilityDao().upsertWeeklySlot(entity.copy(isSynced = true))
            }
            "DOCTOR_SLOT_EXCEPTION" -> {
                val entity = json.decodeFromJsonElement<DoctorSlotExceptionEntity>(element)
                database.doctorAvailabilityDao().upsertException(entity.copy(isSynced = true))
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
            // A returned borrow is a local status update. borrow_records does not
            // have a client-side updated_at field, so using the original borrowed
            // date makes the conflict check incorrectly treat the remote row as newer
            // and download the old is_returned=false row. Use the current timestamp
            // while this pending update is being uploaded so the explicit return wins.
            is BorrowEntity -> if (data.isReturned) {
                com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
            } else {
                data.borrowedDate
            }
            is BorrowReturnEntity -> data.createdAt.ifBlank { data.returnedDate }
            is PersonalReminderEntity -> data.updatedAt
            is ExpenseEntity -> data.updatedAt
            is DoctorWeeklySlotEntity -> data.updatedAt
            is DoctorSlotExceptionEntity -> data.updatedAt
            else -> ""
        }
    }

    suspend fun fetchEntityData(item: SyncQueueEntity): Any? {
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
                "BORROW_RETURN" -> database.borrowReturnDao().getById(entityId)
                "PATIENT_NOTE" -> database.patientNotesDao().getNoteById(entityId)
                "PERSONAL_REMINDER" -> database.personalReminderDao().getById(entityId)
                "EXPENSE" -> database.expenseDao().getExpenseById(entityId)
                "DOCTOR_WEEKLY_SLOT" -> database.doctorAvailabilityDao().getWeeklySlotById(entityId)
                "DOCTOR_SLOT_EXCEPTION" -> database.doctorAvailabilityDao().getExceptionById(entityId)
                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepositoryImpl", "Error fetching data for sync: ${item.entityName} ID $entityId", e)
            null
        }
    }
}