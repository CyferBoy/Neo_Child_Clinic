package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.*
import kotlinx.coroutines.flow.Flow

// Remote-deletion propagation (RemotePrune wiring, Phase 2 Batch 1 / NCC-023).
// reminders keeps its own PK; the remote PK lives in serverId — see RemotePrune.ReminderRef.
data class ReminderSyncRef(val localId: String, val serverId: String?, val synced: Boolean)

@Dao
interface DueReminderDao {

    // Unified Reminder Queries.
    // Every read query below filters is_deleted = 0, including the paginated variants: the
    // statistics "Upcoming" queries already did, so without it a soft-deleted reminder
    // counted in Due today/Overdue but not in Upcoming, and the paged twins of the
    // getAll* queries would disagree with them. Nothing in app/src/main writes
    // is_deleted = 1 today, but migration 31->32 deliberately leaves reminders out of its
    // hard delete, so a row from a pre-v31 client can still arrive soft-deleted.
    
    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' AND reminderEnabled = 1 AND is_deleted = 0 ORDER BY dueDate ASC")
    fun getAllDueReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'COMPLETED' AND reminderEnabled = 0 AND is_deleted = 0 ORDER BY completionDate DESC")
    fun getAllCompletedReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'DISMISSED' AND reminderEnabled = 1 AND is_deleted = 0 ORDER BY dismissalDate DESC")
    fun getAllDismissedReminders(): Flow<List<ReminderEntity>>

    // --- Pagination (large-data scalability pass) ---
    // Due/Completed/Dismissed are explicitly called out in the spec; `status` is indexed
    // (see ReminderEntity). Additive - existing Flow methods above are untouched.
    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' AND reminderEnabled = 1 AND is_deleted = 0 ORDER BY dueDate ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getDueRemindersPage(limit: Int, offset: Int): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status = 'COMPLETED' AND reminderEnabled = 0 AND is_deleted = 0 ORDER BY completionDate DESC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getCompletedRemindersPage(limit: Int, offset: Int): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status = 'DISMISSED' AND reminderEnabled = 1 AND is_deleted = 0 ORDER BY dismissalDate DESC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getDismissedRemindersPage(limit: Int, offset: Int): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName AND type = :type AND is_deleted = 0 LIMIT 1")
    suspend fun getDueReminder(patientId: String, visitId: String, vaccineName: String, type: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE originalVisitId = :visitId AND is_deleted = 0")
    suspend fun getRemindersByVisitId(visitId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getReminderById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND dueDate = :dueDate AND vaccineName = :vaccineName AND type = :type AND is_deleted = 0 LIMIT 1")
    suspend fun getReminderByUniqueEvent(patientId: String, visitId: String, dueDate: String, vaccineName: String, type: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE patientId = :pId AND originalVisitId = :vId AND vaccineName = :name AND type = :type AND is_deleted = 0 LIMIT 1")
    suspend fun getReminderByStableId(pId: String, vId: String, name: String, type: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName AND type = :type")
    suspend fun deleteReminder(patientId: String, visitId: String, vaccineName: String, type: String)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: String)

    @Query("DELETE FROM reminders WHERE patientId = :patientId")
    suspend fun deleteRemindersByPatientId(patientId: String)

    @Query("UPDATE reminders SET serverId = :serverId, isSynced = 1 WHERE id = :localId")
    suspend fun updateServerId(localId: String, serverId: String)

    @Query("UPDATE reminders SET patientId = :masterId, isSynced = 0 WHERE patientId = :duplicateId")
    suspend fun updatePatientId(duplicateId: String, masterId: String)

    @Query("SELECT * FROM reminders WHERE is_deleted = 0")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    // --- Upcoming Vaccination drill-down (Statistics -> Vaccination -> Upcoming) ---
    //
    // These three queries are the SINGLE definition of "upcoming vaccination" for both the
    // statistics counts and the drill-down detail list, so a card reading "12" always opens
    // exactly 12 rows (count/detail parity). The predicate deliberately mirrors
    // getAllDueReminders() above (status + reminderEnabled) and adds the two rules the old
    // Kotlin-side filter() applied by hand:
    //   category = 'VACCINATION' - excludes non-vaccination reminder sources
    //   is_deleted = 0           - soft-deleted rows must never be counted
    // Room needs literal SQL, so the predicate is repeated verbatim in all three - keep them
    // in sync (UpcomingVaccinationRulesTest asserts they agree with the Kotlin rules).
    //
    // `type` is trimmed the same way calculateUpcomingVaccineNeeds does it (blank -> "Other"),
    // so the caller passes the already-normalised group key and this stays an equality match.
    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' AND reminderEnabled = 1 AND category = 'VACCINATION' AND is_deleted = 0 AND type = :type ORDER BY dueDate ASC")
    fun getUpcomingVaccinationsByType(type: String): Flow<List<ReminderEntity>>

    // Brand drill-down. nxt_vaccine_id is NOT comma-joined - the List<String>? TypeConverter
    // writes it as JSON (`["vac-a"]`, or `["vac-a","vac-b"]`), so the old comma formula
    // (',' || nxt_vaccine_id || ',') LIKE '%,vac-a,%' could never match anything and this query
    // returned 0 rows for every brand. The entity comment claiming "the comma-separated
    // convention already used for list-like columns elsewhere" describes
    // patient_visits.vaccine_ids, which is a plain TEXT column joined with joinToString - not
    // this one. The androidTest suite found it the first time it ever ran.
    //
    // Matching is done on the JSON text itself: the needle is the id wrapped in its own quotes,
    // so '"vac-a"' is found inside ["vac-a"] and inside ["vac-b","vac-a"], but never inside
    // ["vac-a2"] - the closing quote has to be there. That is exact without needing delimiters.
    //
    // instr() rather than LIKE because the id becomes part of the pattern: a '%' or '_' in an id
    // would be a wildcard under LIKE, but instr() has no pattern semantics at all. It is also a
    // plain scalar function, so Room generates the cursor normally.
    //
    // SQLite's own json_each() would be the more literal expression of this, but Room cannot
    // resolve a table-valued function: it fails at runtime with "There is no table with name
    // json_each". It parses fine, so this only shows up when the query actually runs - which is
    // why androidTest caught it and compilation did not.
    //
    // NULL needs no special case (instr(NULL, x) is NULL, and NULL > 0 is not true). A row still
    // holding the old comma format simply fails to match, rather than raising.
    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' AND reminderEnabled = 1 AND category = 'VACCINATION' AND is_deleted = 0 AND instr(COALESCE(nxt_vaccine_id, ''), '\"' || :vaccineId || '\"') > 0 ORDER BY dueDate ASC")
    fun getUpcomingVaccinationsByVaccineId(vaccineId: String): Flow<List<ReminderEntity>>

    // Aggregate form of the same predicate, used by the Upcoming statistics section so the
    // card counts and the drill-down rows come from one query definition.
    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' AND reminderEnabled = 1 AND category = 'VACCINATION' AND is_deleted = 0")
    fun getUpcomingVaccinations(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE patientId = :patientId AND is_deleted = 0")
    fun getDueRemindersForPatient(patientId: String): Flow<List<ReminderEntity>>

    @Transaction
    suspend fun moveDueToCompleted(reminder: ReminderEntity, completedBy: String, notes: String? = null) {
        val updated = reminder.copy(
            status = "COMPLETED",
            reminderEnabled = false,
            completionDate = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
            performedBy = completedBy,
            notes = notes ?: reminder.notes,
            updatedAt = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
            isSynced = false
        )
        insertReminder(updated)
    }

    @Transaction
    suspend fun moveDueToDismissed(reminder: ReminderEntity, dismissedBy: String, reason: String? = null) {
        val updated = reminder.copy(
            status = "DISMISSED",
            reminderEnabled = true,
            dismissalDate = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
            performedBy = dismissedBy,
            dismissalReason = reason,
            updatedAt = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
            isSynced = false
        )
        insertReminder(updated)
    }


    // Remote-deletion propagation (RemotePrune wiring, Phase 2 Batch 1 / NCC-023).
    @Query("SELECT id AS localId, serverId AS serverId, isSynced AS synced FROM reminders")
    suspend fun getReminderSyncRefs(): List<ReminderSyncRef>

}
