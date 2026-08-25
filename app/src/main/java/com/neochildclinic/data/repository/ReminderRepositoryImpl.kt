package com.neochildclinic.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.dao.*
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.domain.model.*
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.ReminderStats
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.notification.ReminderScheduler
import com.neochildclinic.core.utils.*
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import io.github.jan.supabase.postgrest.Postgrest
import com.neochildclinic.core.utils.WidgetUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-ready implementation of [ReminderRepository].
 * Manages the lifecycle of vaccination reminders, audits, and synchronization.
 */
@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val dueReminderDao: DueReminderDao,
    private val vaccinationDao: VaccinationDao,
    private val patientDao: PatientDao,
    private val auditLogDao: AuditLogDao,
    private val syncRepository: SyncRepository,
    private val reminderScheduler: ReminderScheduler,
    private val auditLogger: AuditLogger,
    private val sessionManager: com.neochildclinic.core.session.SessionManager,
    @ApplicationContext private val context: Context
) : ReminderRepository {

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    private suspend fun logReminderUndoableChange(
        reminder: ReminderEntity?,
        action: String,
        remarks: String? = null,
        newValue: String? = null,
        explicitEntityId: String? = null,
        transactionGroupId: String? = null
    ) {
        val oldValueJson = reminder?.let { json.encodeToString(it) }
        val entityId = explicitEntityId ?: if (reminder != null) {
            "${reminder.patientId}||${reminder.originalVisitId}||${reminder.vaccineName}||${reminder.type}"
        } else "UNKNOWN"
        
        auditLogger.recordLog(
            module = "PATIENT",
            entityType = "REMINDER",
            entityId = entityId,
            patientId = reminder?.patientId,
            action = action,
            oldValue = oldValueJson,
            newValue = newValue,
            remarks = remarks,
            transactionGroupId = transactionGroupId
        )
    }

    /**
     * Authoritative logic to combine raw entities into a processed list of vaccinations.
     * The `reminders` table is the sole source of truth for Next Vaccinations.
     */
    private fun getProcessedDueFlow(): Flow<Pair<List<Vaccination>, List<PatientEntity>>> {
        return combine(
            vaccinationDao.getAllVaccinations(),
            dueReminderDao.getAllReminders(),
            patientDao.getAllPatients()
        ) { vaccEntities, reminderEntities, patientEntities ->
            val processed = processDueListInternal(vaccEntities, reminderEntities)
            processed to patientEntities
        }
    }

    override fun getDueList(
        searchQuery: String,
        filterStatus: List<ReminderStatus>?
    ): Flow<List<Vaccination>> = getProcessedDueFlow().map { (processed, patientEntities) ->
        val patientMap = patientEntities.associateBy { it.id }
        
        val stateFiltered = if (filterStatus == null || filterStatus.isEmpty()) {
            processed.filter { it.status == ReminderStatus.ACTIVE }
        } else {
            processed.filter { filterStatus.contains(it.status) }
        }

        stateFiltered.filter { vacc ->
            val patient = patientMap[vacc.patientId]
            val matchesSearch = if (searchQuery.isBlank()) true else {
                patient?.name?.contains(searchQuery, ignoreCase = true) == true ||
                patient?.phone?.contains(searchQuery) == true ||
                vacc.nxtVaccineNames.any { it.contains(searchQuery, ignoreCase = true) }
            }
            matchesSearch
        }
    }

    override fun getDashboardStats(): Flow<ReminderStats> = combine(
        vaccinationDao.getAllVaccinations(),
        dueReminderDao.getAllReminders(),
        patientDao.getAllPatients()
    ) { vaccs, reminders, _ ->
        val dueList = processDueListInternal(vaccs, reminders)
        
        val todayCal = DateClassifier.getTodayStart()
        val todayStart = todayCal.timeInMillis

        ReminderStats(
            dueToday = dueList.count { 
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Today
            },
            dueTomorrow = dueList.count { DateClassifier.classify(it.nextDueDate, todayCal) is DateCategory.Tomorrow },
            overdue = dueList.count { 
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Overdue
            },
            completedToday = reminders.count { it.status == "COMPLETED" && com.neochildclinic.core.utils.PatientUtils.isoToLong(it.completionDate) >= todayStart },
            dismissedToday = reminders.count { it.status == "DISMISSED" && com.neochildclinic.core.utils.PatientUtils.isoToLong(it.dismissalDate) >= todayStart },
            notificationsSentToday = reminders.count { it.notificationSent && it.lastReminderTime >= todayStart }
        )
    }

    private fun processDueListInternal(
        vaccEntities: List<VisitEntity>,
        reminderEntities: List<ReminderEntity>
    ): List<Vaccination> {
        val allVaccinations = vaccEntities.map { it.toVaccination() }
        val result = mutableListOf<Vaccination>()

        // The reminders table is the source of truth for the Due section.
        val activeReminders = reminderEntities.filter { it.status == "ACTIVE" && it.reminderEnabled }
        val groupedActive = activeReminders.groupBy { it.patientId to it.dueDate }

        groupedActive.forEach { (key, group) ->
            val (patientId, dueDate) = key
            val firstInGroup = group.first()
            val status = ReminderStatus.ACTIVE
            
            // Link to original visit if available, otherwise create a shell
            val baseVaccination = allVaccinations.find { it.id == firstInGroup.originalVisitId } ?: Vaccination(
                id = UUID.randomUUID().toString(),
                patientId = patientId,
                visitType = "VACCINATION",
                status = status
            )

            result.add(baseVaccination.copy(
                nextVaccinations = group.map {
                    NextVaccinationSummary(
                        reminderId = it.id,
                        type = it.type,
                        vaccineNames = it.vaccineName.split(",").map(String::trim).filter(String::isNotBlank),
                        dueDate = it.dueDate
                    )
                },
                status = status,
                performedBy = firstInGroup.performedBy ?: ""
            ))
        }

        // Completed and dismissed records remain in reminders and are grouped for display.
        val terminalReminders = reminderEntities.filter {
            (it.status == "COMPLETED" && !it.reminderEnabled) ||
                (it.status == "DISMISSED" && it.reminderEnabled)
        }
        val groupedTerminal = terminalReminders.groupBy { Triple(it.patientId, it.dueDate, it.status) }

        groupedTerminal.forEach { (key, group) ->
            val (patientId, dueDate, statusStr) = key
            val status = try { ReminderStatus.valueOf(statusStr) } catch (_: Exception) { ReminderStatus.ACTIVE }
            
            val firstState = group.first()
            val vaccination = allVaccinations.find { it.id == firstState.originalVisitId }
            if (vaccination != null) {
                result.add(vaccination.copy(
                    nextVaccinations = group.map {
                        NextVaccinationSummary(
                            reminderId = it.id,
                            type = it.type,
                            vaccineNames = it.vaccineName.split(",").map(String::trim).filter(String::isNotBlank),
                            dueDate = it.dueDate
                        )
                    },
                    status = status,
                    dateGiven = when (status) {
                        ReminderStatus.COMPLETED -> PatientUtils.formatDateTime(Date(com.neochildclinic.core.utils.PatientUtils.isoToLong(firstState.completionDate)))
                        ReminderStatus.DISMISSED -> PatientUtils.formatDateTime(Date(com.neochildclinic.core.utils.PatientUtils.isoToLong(firstState.dismissalDate)))
                        else -> ""
                    },
                    performedBy = firstState.performedBy ?: "",
                    notes = group.mapNotNull { it.notes ?: it.dismissalReason }.distinct().joinToString(", ")
                ))
            }
        }

        return result
    }

    private suspend fun enqueueReminderSync(
        entityName: String,
        reminderId: String,
        operation: SyncOperation,
        priority: SyncPriority,
        transactionGroupId: String? = null
    ) {
        syncRepository.enqueue(entityName, reminderId, operation, priority, transactionGroupId)
    }

    override suspend fun saveNextVaccination(
        patientId: String,
        originalVisitId: String,
        type: String,
        vaccineNames: List<String>,
        nxtVaccineId: List<String>,
        dueDate: String,
        notes: String,
        priority: String,
        reminderEnabled: Boolean,
        performedBy: String
    ) {
        // Type is mandatory for a Next Vaccination entry; vaccine selection is optional.
        if (type.isBlank() || dueDate.isBlank()) return
        
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val groupedNames = vaccineNames.distinct().joinToString(", ")
                val existing = dueReminderDao.getReminderByUniqueEvent(
                    patientId, originalVisitId, dueDate, groupedNames, type
                )
                val now = PatientUtils.getCurrentIsoTimestamp()
                val userName = sessionManager.getCurrentUserName()
                val reminder = if (existing != null) {
                    existing.copy(
                        dueDate = dueDate,
                        status = "ACTIVE",
                        priority = priority,
                        reminderEnabled = reminderEnabled,
                        category = "VACCINATION",
                        type = type,
                        nxtVaccineId = nxtVaccineId.distinct().ifEmpty { null },
                        notes = notes,
                        updatedAt = now,
                        isSynced = false,
                        createdBy = existing.createdBy ?: userName,
                        updatedBy = userName
                    )
                } else {
                    ReminderEntity(
                        id = UUID.randomUUID().toString(),
                        serverId = null,
                        patientId = patientId,
                        originalVisitId = originalVisitId,
                        vaccineName = groupedNames,
                        dueDate = dueDate,
                        status = "ACTIVE",
                        priority = priority,
                        reminderEnabled = reminderEnabled,
                        category = "VACCINATION",
                        type = type,
                        nxtVaccineId = nxtVaccineId.distinct().ifEmpty { null },
                        notes = notes,
                        createdAt = now,
                        updatedAt = now,
                        isSynced = false,
                        createdBy = userName,
                        updatedBy = userName
                    )
                }
                dueReminderDao.insertReminder(reminder)

                val displayLabel = if (groupedNames.isBlank()) type else groupedNames
                logReminderUndoableChange(
                    reminder = reminder,
                    action = "SCHEDULED",
                    remarks = "Next Vaccination ($displayLabel) scheduled by $userName",
                    newValue = dueDate
                )

                val operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE
                enqueueReminderSync("REMINDERS", reminder.id, operation, SyncPriority.MEDIUM)
            }
            if (reminderEnabled) triggerImmediateCheck()
        }
    }

    override suspend fun markReminderCompleted(reminder: ReminderEntity, performedBy: String, linkedVaccinationId: String?, transactionGroupId: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderById(reminder.id) ?: return@withTransaction
                val userName = sessionManager.getCurrentUserName()
                logReminderUndoableChange(existing, "COMPLETED", "Reminder marked done by $userName", transactionGroupId = transactionGroupId)
                dueReminderDao.moveDueToCompleted(existing.copy(updatedBy = userName), userName, "Reminder completed")
                enqueueReminderSync("REMINDERS", existing.id, SyncOperation.UPDATE, SyncPriority.MEDIUM, transactionGroupId = transactionGroupId)

            }
            triggerImmediateCheck()
        }
    }

    override suspend fun reschedule(reminder: ReminderEntity, newDate: String, reminderDate: String, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderById(reminder.id) ?: return@withTransaction
                val userName = sessionManager.getCurrentUserName()
                val updated = existing.copy(
                    dueDate = newDate,
                    status = "ACTIVE",
                    updatedAt = PatientUtils.getCurrentIsoTimestamp(),
                    isSynced = false,
                    updatedBy = userName
                )
                dueReminderDao.insertReminder(updated)
                logReminderUndoableChange(updated, "RESCHEDULED", "Rescheduled: $reason by $userName", newValue = newDate)
                enqueueReminderSync("REMINDERS", updated.id, SyncOperation.UPDATE, SyncPriority.MEDIUM)
            }
            triggerImmediateCheck()
        }
    }


    override suspend fun dismissReminder(reminder: ReminderEntity, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderById(reminder.id) ?: return@withTransaction
                val userName = sessionManager.getCurrentUserName()
                logReminderUndoableChange(existing, "DISMISSED", "Dismissed: $reason by $userName")
                dueReminderDao.moveDueToDismissed(existing.copy(updatedBy = userName), userName, reason)
                enqueueReminderSync("REMINDERS", existing.id, SyncOperation.UPDATE, SyncPriority.MEDIUM)

            }
            triggerImmediateCheck()
        }
    }

    override suspend fun restoreReminder(reminder: ReminderEntity, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderById(reminder.id) ?: return@withTransaction
                val userName = sessionManager.getCurrentUserName()
                val restored = existing.copy(
                    status = "ACTIVE",
                    reminderEnabled = true,
                    updatedAt = PatientUtils.getCurrentIsoTimestamp(),
                    isSynced = false,
                    updatedBy = userName
                )
                logReminderUndoableChange(existing, "RESTORED", "Restored by $userName")
                dueReminderDao.insertReminder(restored)
                enqueueReminderSync("REMINDERS", restored.id, SyncOperation.UPDATE, SyncPriority.MEDIUM)
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun updateReminderForEdit(reminder: ReminderEntity, performedBy: String, transactionGroupId: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderById(reminder.id) ?: return@withTransaction
                val now = PatientUtils.getCurrentIsoTimestamp()
                val userName = sessionManager.getCurrentUserName()
                val updated = reminder.copy(
                    id = existing.id,
                    createdAt = existing.createdAt,
                    updatedAt = now,
                    performedBy = existing.performedBy,
                    isSynced = false,
                    createdBy = existing.createdBy ?: reminder.createdBy ?: userName,
                    updatedBy = userName
                )
                dueReminderDao.updateReminder(updated)
                logReminderUndoableChange(
                    reminder = updated,
                    action = "UPDATED",
                    remarks = "Next Vaccination reminder updated by $userName",
                    newValue = "dueDate=${updated.dueDate}; type=${updated.type}; vaccines=${updated.vaccineName}",
                    transactionGroupId = transactionGroupId
                )
                enqueueReminderSync(
                    "REMINDERS",
                    updated.id,
                    SyncOperation.UPDATE,
                    SyncPriority.MEDIUM,
                    transactionGroupId = transactionGroupId
                )
            }
        }
    }

    override suspend fun deleteReminder(reminder: ReminderEntity, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderById(reminder.id) ?: return@withTransaction
                logReminderUndoableChange(existing, "DELETED", "Deleted by $performedBy")
                dueReminderDao.softDeleteReminder(existing.id)
                enqueueReminderSync("REMINDERS", existing.id, SyncOperation.DELETE, SyncPriority.LOW)
            }
            triggerImmediateCheck()
        }
    }

    override fun getPatientReminders(patientId: String): Flow<List<ReminderEntity>> {
        return dueReminderDao.getDueRemindersForPatient(patientId)
    }


    override fun getAllReminders(): Flow<List<ReminderEntity>> = dueReminderDao.getAllReminders()

    override suspend fun getRemindersByVisitId(visitId: String): List<ReminderEntity> {
        return dueReminderDao.getRemindersByVisitId(visitId)
    }

    override suspend fun getReminderById(id: String): ReminderEntity? = dueReminderDao.getReminderById(id)

    override suspend fun undoAction(auditId: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            val log = auditLogDao.getLogById(auditId) ?: return@withContext
            if (log.entityType != "REMINDER") return@withContext

            database.withTransaction {
                try {
                    val previousState = log.oldValue?.let { json.decodeFromString<ReminderEntity>(it) }
                    
                    if (previousState != null) {
                        // Restore state from snapshot
                        dueReminderDao.insertReminder(previousState.copy(isSynced = false))
                        
                        auditLogger.recordLog(
                            module = "PATIENT",
                            entityType = "REMINDER",
                            entityId = log.entityId,
                            action = "UNDO",
                            patientId = log.patientId,
                            remarks = "Undid action: ${log.action} by $performedBy"
                        )
                        
                        val parts = log.entityId.split("||")
                        if (parts.size == 4) {
                            enqueueReminderSync("REMINDERS", previousState.id, SyncOperation.UPDATE, SyncPriority.HIGH)
                        }
                    } else if (log.action == "SCHEDULED") {
                        // Undoing a creation means deletion
                        val parts = log.entityId.split("||")
                        if (parts.size == 4) {
                            val existing = dueReminderDao.getReminderByStableId(parts[0], parts[1], parts[2], parts[3])
                            if (existing != null) {
                                dueReminderDao.deleteReminder(parts[0], parts[1], parts[2], parts[3])
                                enqueueReminderSync("REMINDERS", existing.id, SyncOperation.DELETE, SyncPriority.HIGH)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ReminderRepo", "Undo failed", e)
                }
            }
            triggerImmediateCheck()
        }
    }

    override fun getAuditTrail(patientId: String): Flow<List<ReminderAuditEntity>> {
        return auditLogDao.getLogsForPatient(patientId).map { logs ->
            logs.map { log ->
                ReminderAuditEntity(
                    patientId = log.patientId ?: "",
                    originalVisitId = log.entityId,
                    vaccineName = log.remarks ?: "",
                    action = log.action,
                    oldStatus = log.oldValue,
                    newStatus = log.newValue ?: "",
                    oldDate = null,
                    newDate = log.newValue,
                    priority = null,
                    reminderEnabled = null,
                    performedBy = log.user,
                    timestamp = com.neochildclinic.core.utils.PatientUtils.isoToLong(log.timestamp),
                    notes = log.remarks,
                    isSynced = log.isSynced
                )
            }
        }
    }

    override suspend fun refreshReminders() {
        withContext(Dispatchers.IO) {
            try {
                val entities = postgrest.from("reminders").select().decodeList<RemoteReminder>()
                database.withTransaction {
                    for (remote in entities) {
                        // Guard against reminders whose parent visit no longer exists
                        // locally. A visit deletion that didn't (or hadn't yet) cleaned up
                        // its Supabase-side reminder leaves an orphaned remote row - pulling
                        // it back down here would just resurrect it locally with no valid
                        // parent, and any future sync attempt for it would permanently fail
                        // with a foreign key violation. Skip it instead.
                        val visitExists = database.vaccinationDao().getVaccinationById(remote.originalVisitId) != null
                        if (!visitExists) {
                            android.util.Log.e("ReminderRepo", "Skipping orphaned remote reminder for missing visit ${remote.originalVisitId}")
                            continue
                        }

                        // Check if we have a local version and if it's unsynced
                        val local = dueReminderDao.getReminderByStableId(
                            remote.patientId, 
                            remote.originalVisitId, 
                            remote.vaccineName,
                            remote.type
                        )
                        
                        if (local == null || local.isSynced) {
                            // Safe to overwrite or insert
                            // We preserve the local autoincrement id if it exists to avoid row replacement
                            val toSave = remote.toLocal(localId = local?.id)
                            dueReminderDao.insertReminder(toSave)
                        } else {
                            // Local has unsynced changes, keep it for now
                            // The sync engine will eventually push local changes to Supabase
                        }
                    }
                }
                android.util.Log.d("ReminderRepo", "Refreshed ${entities.size} reminders")
            } catch (e: Exception) {
                android.util.Log.e("ReminderRepo", "Refresh failed", e)
            }
        }
    }

    override suspend fun markCompleted(id: String, timestamp: Long) {
        withContext(Dispatchers.IO) {
            val existing = dueReminderDao.getReminderById(id)
            if (existing != null) {
                logReminderUndoableChange(existing, "COMPLETED", "Marked done via notification", explicitEntityId = "${existing.patientId}||${existing.originalVisitId}||${existing.vaccineName}||${existing.type}")
                markReminderCompleted(existing, "SYSTEM_NOTIFICATION")
            }
        }
    }

    override suspend fun insertReminder(reminder: ReminderEntity): String {
        dueReminderDao.insertReminder(reminder)
        return reminder.id
    }

    override suspend fun transferReminders(duplicateId: String, masterId: String) {
        dueReminderDao.updatePatientId(duplicateId, masterId)
    }

    override fun triggerImmediateCheck() {
        reminderScheduler.runNow()
        WidgetUtils.updateWidget(context)
    }
}
