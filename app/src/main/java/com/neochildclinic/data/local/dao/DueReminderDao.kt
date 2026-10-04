package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DueReminderDao {
    
    // Unified Reminder Queries
    
    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' AND reminderEnabled = 1 ORDER BY dueDate ASC")
    fun getAllDueReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'COMPLETED' AND reminderEnabled = 0 ORDER BY completionDate DESC")
    fun getAllCompletedReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'DISMISSED' AND reminderEnabled = 1 ORDER BY dismissalDate DESC")
    fun getAllDismissedReminders(): Flow<List<ReminderEntity>>

    // --- Pagination (large-data scalability pass) ---
    // Due/Completed/Dismissed are explicitly called out in the spec; `status` is indexed
    // (see ReminderEntity). Additive - existing Flow methods above are untouched.
    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' AND reminderEnabled = 1 ORDER BY dueDate ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getDueRemindersPage(limit: Int, offset: Int): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status = 'COMPLETED' AND reminderEnabled = 0 ORDER BY completionDate DESC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getCompletedRemindersPage(limit: Int, offset: Int): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status = 'DISMISSED' AND reminderEnabled = 1 ORDER BY dismissalDate DESC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getDismissedRemindersPage(limit: Int, offset: Int): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND vaccineName = :vaccineName AND type = :type LIMIT 1")
    suspend fun getDueReminder(patientId: String, visitId: String, vaccineName: String, type: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE originalVisitId = :visitId")
    suspend fun getRemindersByVisitId(visitId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE patientId = :patientId AND originalVisitId = :visitId AND dueDate = :dueDate AND vaccineName = :vaccineName AND type = :type LIMIT 1")
    suspend fun getReminderByUniqueEvent(patientId: String, visitId: String, dueDate: String, vaccineName: String, type: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE patientId = :pId AND originalVisitId = :vId AND vaccineName = :name AND type = :type LIMIT 1")
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

    @Query("SELECT * FROM reminders")
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

    // Brand drill-down. nxt_vaccine_id is a comma-joined TEXT column, so there is no join
    // table to filter against - the id must be matched as a whole delimited element.
    // The surrounding commas are what make this exact rather than a substring match:
    // ',a1b,' matches 'a1' and 'a1b' but never 'a1b2'. COALESCE covers rows written before
    // the column existed (NULL/empty), which cannot match any id and so are excluded -
    // the same outcome calculateUpcomingVaccineNeeds produced for those rows.
    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' AND reminderEnabled = 1 AND category = 'VACCINATION' AND is_deleted = 0 AND (',' || COALESCE(nxt_vaccine_id, '') || ',') LIKE '%,' || :vaccineId || ',%' ORDER BY dueDate ASC")
    fun getUpcomingVaccinationsByVaccineId(vaccineId: String): Flow<List<ReminderEntity>>

    // Aggregate form of the same predicate, used by the Upcoming statistics section so the
    // card counts and the drill-down rows come from one query definition.
    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' AND reminderEnabled = 1 AND category = 'VACCINATION' AND is_deleted = 0")
    fun getUpcomingVaccinations(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE patientId = :patientId")
    fun getDueRemindersForPatient(patientId: String): Flow<List<ReminderEntity>>

    @Transaction
    suspend fun getLocalPriority(pId: String, vId: String, name: String, type: String): Int {
        val reminder = getReminderByStableId(pId, vId, name, type) ?: return 0
        return when (reminder.status) {
            "COMPLETED" -> 3
            "DISMISSED" -> 2
            "ACTIVE" -> 1
            else -> 0
        }
    }

    @Transaction
    suspend fun isLocalUnsynced(pId: String, vId: String, name: String, type: String): Boolean {
        return getReminderByStableId(pId, vId, name, type)?.isSynced == false
    }

    @Transaction
    suspend fun clearAllStates(patientId: String, visitId: String, vaccineName: String, type: String) {
        deleteReminder(patientId, visitId, vaccineName, type)
    }

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

}
