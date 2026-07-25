package com.clinic.neochild.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.clinic.neochild.data.local.database.AppDatabase
import com.clinic.neochild.data.local.dao.*
import com.clinic.neochild.data.local.entity.*
import com.clinic.neochild.domain.model.*
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.ReminderStats
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.notification.ReminderScheduler
import com.clinic.neochild.core.utils.*
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import com.clinic.neochild.core.logger.AuditLogger
import com.clinic.neochild.domain.logic.ReminderEngine
import com.clinic.neochild.domain.model.PendingRequirement
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
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
    private val firestore: FirebaseFirestore,
    private val reminderDao: ReminderDao,
    private val dueReminderDao: DueReminderDao,
    private val vaccinationDao: VaccinationDao,
    private val patientDao: PatientDao,
    private val auditLogDao: AuditLogDao,
    private val syncRepository: SyncRepository,
    private val reminderScheduler: ReminderScheduler,
    private val auditLogger: AuditLogger,
    @ApplicationContext private val context: Context
) : ReminderRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun logReminderUndoableChange(
        reminder: ReminderEntity?,
        action: String,
        remarks: String? = null,
        newValue: String? = null,
        explicitEntityId: String? = null
    ) {
        val oldValueJson = reminder?.let { json.encodeToString(it) }
        val entityId = explicitEntityId ?: if (reminder != null) {
            "${reminder.patientId}||${reminder.originalVisitId}||${reminder.vaccineName}"
        } else "UNKNOWN"
        
        auditLogger.recordLog(
            module = "PATIENT",
            entityType = "REMINDER",
            entityId = entityId,
            patientId = reminder?.patientId,
            action = action,
            oldValue = oldValueJson,
            newValue = newValue,
            remarks = remarks
        )
    }

    /**
     * Shared logic to combine raw entities into a processed list of vaccinations.
     * Uses the unified reminder_states table.
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
            processed.filter { it.status == ReminderStatus.ACTIVE || it.status == ReminderStatus.RESCHEDULED }
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

    override fun getDueToday(): Flow<List<Vaccination>> = getProcessedDueFlow().map { (list, _) ->
        PatientUtils.filterVaccinationsByPeriod(list, "Today")
    }

    override fun getDueTomorrow(): Flow<List<Vaccination>> = getProcessedDueFlow().map { (list, _) ->
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time
        val tomorrowStr = PatientUtils.formatDate(tomorrow)
        PatientUtils.filterVaccinationsByPeriod(list, "This Week").filter { v ->
            v.nextDueDate == tomorrowStr
        }
    }

    override fun getOverdue(): Flow<List<Vaccination>> = getProcessedDueFlow().map { (list, _) ->
        PatientUtils.filterVaccinationsByPeriod(list, "Overdue")
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
                cat is DateCategory.Overdue || cat is DateCategory.Yesterday || cat is DateCategory.GracePeriod
            },
            completedToday = reminders.count { it.status == "COMPLETED" && (it.completionDate ?: 0) >= todayStart },
            rescheduledToday = reminders.count { it.status == "RESCHEDULED" && it.updatedAt >= todayStart },
            externalToday = reminders.count { it.status == "EXTERNAL" && it.updatedAt >= todayStart },
            dismissedToday = reminders.count { it.status == "DISMISSED" && (it.dismissalDate ?: 0) >= todayStart },
            notificationsSentToday = reminders.count { it.notificationSent && it.lastReminderTime >= todayStart }
        )
    }

    private fun processDueListInternal(
        vaccEntities: List<VisitEntity>,
        reminderEntities: List<ReminderEntity>
    ): List<Vaccination> {
        val allVaccinations = vaccEntities.map { it.toVaccination() }
        val potential = ReminderEngine.getPotentialRequirements(allVaccinations)
        
        val reminderMap = reminderEntities.associateBy { "${it.patientId}_${it.originalVisitId}_${it.vaccineName}" }
        val result = mutableListOf<Vaccination>()

        // 1. Group potential requirements by patient and due date
        val groupedPotential = potential.groupBy { it.patientId to PatientUtils.formatDate(it.dueDate) }

        groupedPotential.forEach { (key, requirements) ->
            val (patientId, dueDateStr) = key
            
            val terminalStates = requirements.map { req ->
                val rKey = "${req.patientId}_${req.originalVisitId}_${req.vaccineName}"
                reminderMap[rKey]
            }

            // If any in group are ACTIVE or RESCHEDULED, they should appear in the "Due" pool
            val activeReminders = terminalStates.filter { it == null || (it.status != "COMPLETED" && it.status != "DISMISSED" && it.status != "EXTERNAL") }
            
            if (activeReminders.isNotEmpty()) {
                val status = if (activeReminders.any { it?.status == "RESCHEDULED" }) ReminderStatus.RESCHEDULED else ReminderStatus.ACTIVE
                val vaccineNames = requirements.map { it.vaccineName }.distinct()
                val firstReq = requirements.first()
                
                allVaccinations.find { it.id == firstReq.originalVisitId }?.copy(
                    nxtVaccineNames = vaccineNames,
                    nextDueDate = dueDateStr,
                    isDone = false,
                    status = status,
                    performedBy = activeReminders.firstOrNull { it?.performedBy?.isNotBlank() == true }?.performedBy ?: ""
                )?.let { result.add(it) }
            }
        }

        // 2. Process Terminal States (Completed, Dismissed, External) - Grouped by patient, date and status
        val terminalReminders = reminderEntities.filter { it.status != "ACTIVE" && it.status != "RESCHEDULED" }
        val groupedTerminal = terminalReminders.groupBy { Triple(it.patientId, it.dueDate, it.status) }

        groupedTerminal.forEach { (key, group) ->
            val (patientId, dueDate, statusStr) = key
            val status = try { ReminderStatus.valueOf(statusStr) } catch (_: Exception) { ReminderStatus.ACTIVE }
            
            val firstState = group.first()
            val vaccination = allVaccinations.find { it.id == firstState.originalVisitId }
            if (vaccination != null) {
                result.add(vaccination.copy(
                    nxtVaccineNames = group.map { it.vaccineName }.distinct(),
                    nextDueDate = dueDate,
                    isDone = status == ReminderStatus.COMPLETED || status == ReminderStatus.EXTERNAL,
                    status = status,
                    dateGiven = when (status) {
                        ReminderStatus.COMPLETED -> PatientUtils.formatDateTime(Date(firstState.completionDate ?: 0))
                        ReminderStatus.EXTERNAL -> firstState.externalDate ?: ""
                        ReminderStatus.DISMISSED -> PatientUtils.formatDateTime(Date(firstState.dismissalDate ?: 0))
                        else -> ""
                    },
                    performedBy = firstState.performedBy ?: "",
                    notes = group.mapNotNull { it.notes ?: it.dismissalReason }.distinct().joinToString(", ")
                ))
            }
        }

        return result
    }

    override fun getCompletedDueRecords(): Flow<List<CompletedDueRecord>> = callbackFlow {
        val listener = firestore.collection("completed_due_vaccinations")
            .addSnapshotListener { snapshot, _ ->
                val records = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(CompletedDueRecord::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(records)
            }
        awaitClose { listener.remove() }
    }

    override fun getDismissedDueRecords(): Flow<List<DismissedDueRecord>> = callbackFlow {
        val listener = firestore.collection("dismissed_due_vaccinations")
            .addSnapshotListener { snapshot, _ ->
                val records = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(DismissedDueRecord::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(records)
            }
        awaitClose { listener.remove() }
    }

    override fun getOtherEstablishmentDueRecords(): Flow<List<OtherEstablishmentDueRecord>> = callbackFlow {
        val listener = firestore.collection("other_establishment_due_vaccinations")
            .addSnapshotListener { snapshot, _ ->
                val records = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(OtherEstablishmentDueRecord::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(records)
            }
        awaitClose { listener.remove() }
    }

    private suspend fun enqueueReminderSync(
        entityName: String,
        patientId: String,
        visitId: String,
        vaccineName: String,
        operation: SyncOperation,
        priority: SyncPriority
    ) {
        val syncId = "${patientId}||${visitId}||${vaccineName}"
        syncRepository.enqueue(entityName, syncId, operation, priority)
    }

    override suspend fun scheduleFollowUp(
        patientId: String,
        originalVisitId: String,
        vaccineNames: List<String>,
        dueDate: String,
        notes: String,
        priority: String,
        reminderEnabled: Boolean,
        performedBy: String
    ) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                vaccineNames.forEach { name ->
                    dueReminderDao.clearAllStates(patientId, originalVisitId, name)
                    
                    val reminder = ReminderEntity(
                        patientId = patientId,
                        originalVisitId = originalVisitId,
                        vaccineName = name,
                        dueDate = dueDate,
                        reminderDate = dueDate,
                        status = "ACTIVE",
                        priority = priority,
                        reminderEnabled = reminderEnabled,
                        notes = notes,
                        isSynced = false
                    )
                    dueReminderDao.insertReminder(reminder)
                    
                    logReminderUndoableChange(
                        reminder = null, 
                        action = "SCHEDULED",
                        remarks = "Follow-up scheduled by $performedBy",
                        newValue = dueDate,
                        explicitEntityId = "${patientId}||${originalVisitId}||${name}"
                    )

                    enqueueReminderSync("REMINDER_STATE", patientId, originalVisitId, name, SyncOperation.CREATE, SyncPriority.MEDIUM)
                }
            }
            if (reminderEnabled) triggerImmediateCheck()
        }
    }

    override suspend fun markRequirementSatisfied(requirement: PendingRequirement, performedBy: String, linkedVaccinationId: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                if (existing != null) {
                    logReminderUndoableChange(existing, "COMPLETED", "${requirement.vaccineName} marked done by $performedBy")
                    dueReminderDao.moveDueToCompleted(existing, performedBy, "Requirement satisfied")
                    enqueueReminderSync("REMINDER_STATE", existing.patientId, existing.originalVisitId, existing.vaccineName, SyncOperation.UPDATE, SyncPriority.MEDIUM)
                } else {
                    val completed = ReminderEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        dueDate = PatientUtils.formatDate(requirement.dueDate),
                        status = "COMPLETED",
                        completionDate = System.currentTimeMillis(),
                        performedBy = performedBy,
                        notes = "Requirement satisfied"
                    )
                    logReminderUndoableChange(null, "COMPLETED", "${requirement.vaccineName} marked done by $performedBy", explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}")
                    dueReminderDao.insertReminder(completed)
                    enqueueReminderSync("REMINDER_STATE", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, SyncOperation.CREATE, SyncPriority.MEDIUM)
                }

                // Firestore: Write to separate collection
                val now = Calendar.getInstance()
                val record = CompletedDueRecord(
                    patientId = requirement.patientId,
                    originalDueDate = PatientUtils.formatDate(requirement.dueDate),
                    completedDate = PatientUtils.formatDate(now.time),
                    completedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time),
                    completedBy = performedBy,
                    linkedVaccinationId = linkedVaccinationId ?: "",
                    remarks = "Requirement satisfied"
                )
                firestore.collection("completed_due_vaccinations").add(record)
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun reschedule(requirement: PendingRequirement, newDate: String, reminderDate: String, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                val updated = existing?.copy(
                    dueDate = newDate,
                    reminderDate = reminderDate,
                    status = "RESCHEDULED",
                    updatedAt = System.currentTimeMillis(),
                    notes = if (reason.isNotBlank()) "${existing.notes ?: ""}\nRescheduled: $reason" else existing.notes,
                    isSynced = false
                ) ?: ReminderEntity(
                    patientId = requirement.patientId,
                    originalVisitId = requirement.originalVisitId,
                    vaccineName = requirement.vaccineName,
                    dueDate = newDate,
                    reminderDate = reminderDate,
                    status = "RESCHEDULED",
                    notes = reason,
                    isSynced = false
                )

                dueReminderDao.insertReminder(updated)

                logReminderUndoableChange(
                    reminder = existing, 
                    action = "RESCHEDULED",
                    remarks = "Rescheduled: $reason",
                    newValue = newDate,
                    explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}"
                )
                
                enqueueReminderSync("REMINDER_STATE", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, SyncOperation.UPDATE, SyncPriority.MEDIUM)
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun markVaccinatedElsewhere(
        requirement: PendingRequirement,
        hospitalName: String,
        vaccinatedDate: String,
        notes: String,
        performedBy: String
    ) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // 1. Record Visit (Vaccinated elsewhere is still a visit record)
                val visit = VisitEntity(
                    id = UUID.randomUUID().toString(),
                    patientId = requirement.patientId,
                    dateGiven = vaccinatedDate,
                    doctor = performedBy,
                    vaccineNames = requirement.vaccineName,
                    notes = notes,
                    source = "EXTERNAL",
                    isDone = true
                )
                vaccinationDao.insertVaccination(visit)
                syncRepository.enqueue("VACCINATION", visit.id, SyncOperation.CREATE, SyncPriority.MEDIUM)

                // 2. Update Reminder State
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                logReminderUndoableChange(
                    reminder = existing,
                    action = "EXTERNAL",
                    remarks = "Vaccinated elsewhere: $hospitalName",
                    explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}"
                )

                if (existing != null) {
                    dueReminderDao.moveDueToExternal(existing, hospitalName, vaccinatedDate, performedBy, notes)
                } else {
                    val external = ReminderEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        dueDate = PatientUtils.formatDate(requirement.dueDate),
                        status = "EXTERNAL",
                        externalDate = vaccinatedDate,
                        source = hospitalName,
                        performedBy = performedBy,
                        notes = notes
                    )
                    dueReminderDao.insertReminder(external)
                }
                
                enqueueReminderSync("REMINDER_STATE", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, SyncOperation.UPDATE, SyncPriority.MEDIUM)
                
                // Firestore: Write to separate collection
                val now = Calendar.getInstance()
                val record = OtherEstablishmentDueRecord(
                    patientId = requirement.patientId,
                    originalDueDate = PatientUtils.formatDate(requirement.dueDate),
                    vaccinatedDate = vaccinatedDate,
                    hospitalName = hospitalName,
                    recordedBy = performedBy,
                    recordedDate = PatientUtils.formatDate(now.time),
                    recordedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time),
                    remarks = notes
                )
                firestore.collection("other_establishment_due_vaccinations").add(record)
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun dismissReminder(requirement: PendingRequirement, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                
                logReminderUndoableChange(
                    reminder = existing,
                    action = "DISMISSED",
                    remarks = "Dismissed: $reason",
                    explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}"
                )

                if (existing != null) {
                    dueReminderDao.moveDueToDismissed(existing, performedBy, reason)
                } else {
                    val dismissed = ReminderEntity(
                        patientId = requirement.patientId,
                        originalVisitId = requirement.originalVisitId,
                        vaccineName = requirement.vaccineName,
                        dueDate = PatientUtils.formatDate(requirement.dueDate),
                        status = "DISMISSED",
                        dismissalDate = System.currentTimeMillis(),
                        performedBy = performedBy,
                        dismissalReason = reason
                    )
                    dueReminderDao.insertReminder(dismissed)
                }

                // Firestore: Write to separate collection
                val now = Calendar.getInstance()
                val record = DismissedDueRecord(
                    patientId = requirement.patientId,
                    originalDueDate = PatientUtils.formatDate(requirement.dueDate),
                    dismissedDate = PatientUtils.formatDate(now.time),
                    dismissedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time),
                    dismissedBy = performedBy,
                    dismissReason = reason,
                    remarks = ""
                )
                firestore.collection("dismissed_due_vaccinations").add(record)
                
                enqueueReminderSync("REMINDER_STATE", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, SyncOperation.UPDATE, SyncPriority.MEDIUM)
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun restoreReminder(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                if (existing != null) {
                    logReminderUndoableChange(existing, "RESTORED", explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}")
                    val restored = existing.copy(
                        status = "ACTIVE",
                        updatedAt = System.currentTimeMillis(),
                        isSynced = false
                    )
                    dueReminderDao.insertReminder(restored)

                    enqueueReminderSync("REMINDER_STATE", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, SyncOperation.UPDATE, SyncPriority.MEDIUM)
                }
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun deleteReminder(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getReminderByStableId(requirement.patientId, requirement.originalVisitId, requirement.vaccineName)
                if (existing != null) {
                    logReminderUndoableChange(existing, "DELETED", explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}")
                    dueReminderDao.softDeleteReminder(existing.id)
                    
                    enqueueReminderSync("REMINDER_STATE", existing.patientId, existing.originalVisitId, existing.vaccineName, SyncOperation.UPDATE, SyncPriority.LOW)
                }
            }
            triggerImmediateCheck()
        }
    }

    override fun getPatientFollowUps(patientId: String): Flow<List<ReminderEntity>> {
        return dueReminderDao.getDueRemindersForPatient(patientId)
    }

    override suspend fun undoAction(auditId: Long, performedBy: String) {
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
                        if (parts.size == 3) {
                            enqueueReminderSync("REMINDER_STATE", parts[0], parts[1], parts[2], SyncOperation.UPDATE, SyncPriority.HIGH)
                        }
                    } else if (log.action == "SCHEDULED") {
                        // Undoing a creation means deletion
                        val parts = log.entityId.split("||")
                        if (parts.size == 3) {
                            dueReminderDao.deleteReminder(parts[0], parts[1], parts[2])
                            enqueueReminderSync("REMINDER_STATE", parts[0], parts[1], parts[2], SyncOperation.DELETE, SyncPriority.HIGH)
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
                    timestamp = log.timestamp,
                    notes = log.remarks,
                    isSynced = log.isSynced
                )
            }
        }
    }

    override suspend fun refreshReminders() {
        withContext(Dispatchers.IO) {
            try {
                val snap = firestore.collection("reminders").get().await()
                val entities = snap.documents.mapNotNull { 
                    FirestoreMappers.toReminderEntity(it)
                }
                database.withTransaction {
                    for (remote in entities) {
                        // Check if we have a local version and if it's unsynced
                        val local = dueReminderDao.getReminderByStableId(
                            remote.patientId, 
                            remote.originalVisitId, 
                            remote.vaccineName
                        )
                        
                        if (local == null || local.isSynced) {
                            // Safe to overwrite or insert
                            // We preserve the local autoincrement id if it exists to avoid row replacement
                            val toSave = remote.copy(
                                id = local?.id ?: 0L,
                                isSynced = true
                            )
                            dueReminderDao.insertReminder(toSave)
                        } else {
                            // Local has unsynced changes, keep it for now
                            // The sync engine will eventually push local changes to Firestore
                        }
                    }
                }
                android.util.Log.d("ReminderRepo", "Refreshed ${entities.size} reminders")
            } catch (e: Exception) {
                android.util.Log.e("ReminderRepo", "Refresh failed", e)
            }
        }
    }

    override suspend fun markCompleted(id: Long, timestamp: Long) {
        withContext(Dispatchers.IO) {
            val existing = dueReminderDao.getReminderById(id)
            if (existing != null) {
                logReminderUndoableChange(existing, "COMPLETED", "Marked done via notification", explicitEntityId = "${existing.patientId}||${existing.originalVisitId}||${existing.vaccineName}")
                markRequirementSatisfied(
                    PendingRequirement(existing.patientId, existing.vaccineName, PatientUtils.parseDate(existing.dueDate) ?: Date(), existing.originalVisitId),
                    "SYSTEM_NOTIFICATION"
                )
            }
        }
    }

    override suspend fun insertReminder(reminder: ReminderEntity): Long {
        return dueReminderDao.insertReminder(reminder)
    }

    override suspend fun transferReminders(duplicateId: String, masterId: String) {
        dueReminderDao.updatePatientId(duplicateId, masterId)
    }

    override fun triggerImmediateCheck() {
        reminderScheduler.runNow()
        WidgetUtils.updateWidget(context)
    }
}
