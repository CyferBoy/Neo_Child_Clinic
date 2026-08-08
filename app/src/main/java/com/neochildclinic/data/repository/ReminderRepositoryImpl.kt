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
import com.neochildclinic.domain.logic.ReminderEngine
import com.neochildclinic.domain.model.PendingRequirement
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
     * Shared logic to combine raw entities into a processed list of vaccinations.
     * Uses the unified reminders table.
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
            completedToday = reminders.count { it.status == "COMPLETED" && com.neochildclinic.core.utils.PatientUtils.isoToLong(it.completionDate) >= todayStart },
            rescheduledToday = reminders.count { it.status == "RESCHEDULED" && com.neochildclinic.core.utils.PatientUtils.isoToLong(it.updatedAt) >= todayStart },
            externalToday = reminders.count { it.status == "EXTERNAL" && com.neochildclinic.core.utils.PatientUtils.isoToLong(it.updatedAt) >= todayStart },
            dismissedToday = reminders.count { it.status == "DISMISSED" && com.neochildclinic.core.utils.PatientUtils.isoToLong(it.dismissalDate) >= todayStart },
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
                    // Next vaccines/due date are now derived from followUps list
                    followUps = requirements.map { req ->
                        FollowUpRequirement(
                            nextVaccineName = req.vaccineName,
                            dueDate = dueDateStr
                        )
                    },
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
                    followUps = group.map { 
                        FollowUpRequirement(nextVaccineName = it.vaccineName, dueDate = dueDate)
                    },
                    status = status,
                    dateGiven = when (status) {
                        ReminderStatus.COMPLETED -> PatientUtils.formatDateTime(Date(com.neochildclinic.core.utils.PatientUtils.isoToLong(firstState.completionDate)))
                        ReminderStatus.EXTERNAL -> firstState.notes?.substringAfter("Date: ")?.substringBefore("\n") ?: ""
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

    override fun getCompletedDueRecords(): Flow<List<CompletedDueRecord>> = flow {
        try {
            val records = postgrest.from("completed_due_vaccinations").select().decodeList<CompletedDueRecord>()
            emit(records)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override fun getDismissedDueRecords(): Flow<List<DismissedDueRecord>> = flow {
        try {
            val records = postgrest.from("dismissed_due_vaccinations").select().decodeList<DismissedDueRecord>()
            emit(records)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override fun getOtherEstablishmentDueRecords(): Flow<List<OtherEstablishmentDueRecord>> = flow {
        try {
            val records = postgrest.from("other_establishment_due_vaccinations").select().decodeList<OtherEstablishmentDueRecord>()
            emit(records)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    private suspend fun enqueueReminderSync(
        entityName: String,
        patientId: String,
        visitId: String,
        vaccineName: String,
        type: String,
        operation: SyncOperation,
        priority: SyncPriority,
        transactionGroupId: String? = null
    ) {
        val syncId = "${patientId}||${visitId}||${vaccineName}||${type}"
        syncRepository.enqueue(entityName, syncId, operation, priority, transactionGroupId)
    }

    override suspend fun scheduleFollowUp(
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
        // Type is mandatory for a Due Vaccination entry; vaccine selection is optional.
        if (type.isBlank() || dueDate.isBlank()) return
        
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // Grouping logic: all vaccines for this visit/date/type go into ONE row.
                // Vaccine selection is optional -- groupedNames may be blank.
                val groupedNames = vaccineNames.distinct().joinToString(", ")
                
                // Clear any existing state for these specific vaccines individually if they exist (cleanup)
                // Note: we now cleanup based on type too if possible, but stable lookup needs all parts.
                
                val reminder = ReminderEntity(
                    patientId = patientId,
                    originalVisitId = originalVisitId,
                    vaccineName = groupedNames,
                    dueDate = dueDate,
                    reminderDate = dueDate,
                    status = "ACTIVE",
                    priority = priority,
                    reminderEnabled = reminderEnabled,
                    type = type,
                    nxtVaccineId = nxtVaccineId.ifEmpty { null },
                    notes = notes,
                    isSynced = false
                )
                dueReminderDao.insertReminder(reminder)
                
                val displayLabel = if (groupedNames.isBlank()) type else "$type ($groupedNames)"
                logReminderUndoableChange(
                    reminder = null, 
                    action = "SCHEDULED",
                    remarks = "Follow-up ($displayLabel) scheduled by $performedBy",
                    newValue = dueDate,
                    explicitEntityId = "${patientId}||${originalVisitId}||$groupedNames||$type"
                )

                enqueueReminderSync("REMINDERS", patientId, originalVisitId, groupedNames, type, SyncOperation.CREATE, SyncPriority.MEDIUM)
            }
            if (reminderEnabled) triggerImmediateCheck()
        }
    }

    override suspend fun markRequirementSatisfied(requirement: PendingRequirement, performedBy: String, linkedVaccinationId: String?, transactionGroupId: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                // Smart Lookup: Find any reminder whose vaccineName contains the administered vaccine
                val allReminders = dueReminderDao.getDueRemindersForPatient(requirement.patientId).first()
                val targetReminder = allReminders.find { 
                    it.originalVisitId == requirement.originalVisitId && 
                    it.vaccineName.split(", ").contains(requirement.vaccineName) 
                }
                
                if (targetReminder != null) {
                    val remainingVaccines = targetReminder.vaccineName.split(", ")
                        .filter { it != requirement.vaccineName }
                    
                    if (remainingVaccines.isEmpty()) {
                        // All vaccines in this reminder are done
                        logReminderUndoableChange(targetReminder, "COMPLETED", "${requirement.vaccineName} marked done by $performedBy", transactionGroupId = transactionGroupId)
                        dueReminderDao.moveDueToCompleted(targetReminder, performedBy, "Requirement satisfied")
                        enqueueReminderSync("REMINDERS", targetReminder.patientId, targetReminder.originalVisitId, targetReminder.vaccineName, targetReminder.type, SyncOperation.UPDATE, SyncPriority.MEDIUM, transactionGroupId = transactionGroupId)
                    } else {
                        // Partially satisfied: Update the row with remaining vaccines
                        val updatedName = remainingVaccines.joinToString(", ")
                        val updated = targetReminder.copy(
                            vaccineName = updatedName,
                            updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                            isSynced = false
                        )
                        dueReminderDao.insertReminder(updated)
                        logReminderUndoableChange(targetReminder, "PARTIAL", "Administered ${requirement.vaccineName}, remaining: $updatedName", transactionGroupId = transactionGroupId)
                        enqueueReminderSync("REMINDERS", targetReminder.patientId, targetReminder.originalVisitId, updatedName, targetReminder.type, SyncOperation.UPDATE, SyncPriority.MEDIUM, transactionGroupId = transactionGroupId)
                    }
                } else {
                    // Fallback for direct matches or legacy data
                    val existing = dueReminderDao.getAllDueReminders().first().find { 
                        it.patientId == requirement.patientId && 
                        it.originalVisitId == requirement.originalVisitId && 
                        it.vaccineName == requirement.vaccineName 
                    }
                    if (existing != null) {
                        dueReminderDao.moveDueToCompleted(existing, performedBy, "Requirement satisfied")
                        enqueueReminderSync("REMINDERS", existing.patientId, existing.originalVisitId, existing.vaccineName, existing.type, SyncOperation.UPDATE, SyncPriority.MEDIUM)
                    }
                }

                // Supabase audit record
                val now = Calendar.getInstance()
                val record = CompletedDueRecord(
                    patientId = requirement.patientId,
                    originalDueDate = PatientUtils.formatDate(requirement.dueDate),
                    completedDate = PatientUtils.formatDate(now.time),
                    completedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time),
                    completedBy = performedBy,
                    linkedVaccinationId = linkedVaccinationId ?: "",
                    remarks = "Administered ${requirement.vaccineName}"
                )
                postgrest.from("completed_due_vaccinations").insert(record)
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun reschedule(requirement: PendingRequirement, newDate: String, reminderDate: String, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getAllDueReminders().first().find {
                    it.patientId == requirement.patientId &&
                    it.originalVisitId == requirement.originalVisitId &&
                    it.vaccineName == requirement.vaccineName
                }
                
                val updated = existing?.copy(
                    dueDate = newDate,
                    reminderDate = reminderDate,
                    status = "RESCHEDULED",
                    updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
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
                    explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}||${existing?.type ?: ""}"
                )
                
                enqueueReminderSync("REMINDERS", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, existing?.type ?: "", SyncOperation.UPDATE, SyncPriority.MEDIUM)
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
                    status = ReminderStatus.EXTERNAL
                )
                vaccinationDao.insertVaccination(visit)
                syncRepository.enqueue("VACCINATION", visit.id, SyncOperation.CREATE, SyncPriority.MEDIUM)

                // 2. Update Reminder State
                val existing = dueReminderDao.getAllDueReminders().first().find {
                    it.patientId == requirement.patientId &&
                    it.originalVisitId == requirement.originalVisitId &&
                    it.vaccineName == requirement.vaccineName
                }
                
                logReminderUndoableChange(
                    reminder = existing,
                    action = "EXTERNAL",
                    remarks = "Vaccinated elsewhere: $hospitalName",
                    explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}||${existing?.type ?: ""}"
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
                
                enqueueReminderSync("REMINDERS", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, existing?.type ?: "", SyncOperation.UPDATE, SyncPriority.MEDIUM)
                
                // Supabase: Write to separate table
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
                postgrest.from("other_establishment_due_vaccinations").insert(record)
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun dismissReminder(requirement: PendingRequirement, reason: String, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getAllDueReminders().first().find {
                    it.patientId == requirement.patientId &&
                    it.originalVisitId == requirement.originalVisitId &&
                    it.vaccineName == requirement.vaccineName
                }
                
                logReminderUndoableChange(
                    reminder = existing,
                    action = "DISMISSED",
                    remarks = "Dismissed: $reason",
                    explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}||${existing?.type ?: ""}"
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
                        dismissalDate = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                        performedBy = performedBy,
                        dismissalReason = reason
                    )
                    dueReminderDao.insertReminder(dismissed)
                }

                enqueueReminderSync("REMINDERS", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, existing?.type ?: "", SyncOperation.UPDATE, SyncPriority.MEDIUM)

                // Supabase: Write to separate table
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
                postgrest.from("dismissed_due_vaccinations").insert(record)
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun restoreReminder(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getAllReminders().first().find {
                    it.patientId == requirement.patientId &&
                    it.originalVisitId == requirement.originalVisitId &&
                    it.vaccineName == requirement.vaccineName
                }
                if (existing != null) {
                    logReminderUndoableChange(existing, "RESTORED", explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}||${existing.type}")
                    val restored = existing.copy(
                        status = "ACTIVE",
                        updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                        isSynced = false
                    )
                    dueReminderDao.insertReminder(restored)

                    enqueueReminderSync("REMINDERS", requirement.patientId, requirement.originalVisitId, requirement.vaccineName, existing.type, SyncOperation.UPDATE, SyncPriority.MEDIUM)
                }
            }
            triggerImmediateCheck()
        }
    }

    override suspend fun deleteReminder(requirement: PendingRequirement, performedBy: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = dueReminderDao.getAllReminders().first().find {
                    it.patientId == requirement.patientId &&
                    it.originalVisitId == requirement.originalVisitId &&
                    it.vaccineName == requirement.vaccineName
                }
                if (existing != null) {
                    logReminderUndoableChange(existing, "DELETED", explicitEntityId = "${requirement.patientId}||${requirement.originalVisitId}||${requirement.vaccineName}||${existing.type}")
                    dueReminderDao.softDeleteReminder(existing.id)
                    
                    enqueueReminderSync("REMINDERS", existing.patientId, existing.originalVisitId, existing.vaccineName, existing.type, SyncOperation.UPDATE, SyncPriority.LOW)
                }
            }
            triggerImmediateCheck()
        }
    }

    override fun getPatientFollowUps(patientId: String): Flow<List<ReminderEntity>> {
        return dueReminderDao.getDueRemindersForPatient(patientId)
    }

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
                            enqueueReminderSync("REMINDERS", parts[0], parts[1], parts[2], parts[3], SyncOperation.UPDATE, SyncPriority.HIGH)
                        }
                    } else if (log.action == "SCHEDULED") {
                        // Undoing a creation means deletion
                        val parts = log.entityId.split("||")
                        if (parts.size == 4) {
                            dueReminderDao.deleteReminder(parts[0], parts[1], parts[2], parts[3])
                            enqueueReminderSync("REMINDERS", parts[0], parts[1], parts[2], parts[3], SyncOperation.DELETE, SyncPriority.HIGH)
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
                markRequirementSatisfied(
                    PendingRequirement(existing.patientId, existing.vaccineName, PatientUtils.parseDate(existing.dueDate) ?: Date(), existing.originalVisitId),
                    "SYSTEM_NOTIFICATION"
                )
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
