package com.neochildclinic.data.repository

import android.util.Log
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.session.SessionManager
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.dao.PersonalReminderDao
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import com.neochildclinic.domain.model.PersonalReminderStatus
import com.neochildclinic.domain.repository.SyncRepository
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonalReminderRepositoryImpl @Inject constructor(
    database: AppDatabase,
    private val syncRepository: SyncRepository,
    private val postgrest: Postgrest,
    private val sessionManager: SessionManager,
    private val auditLogger: com.neochildclinic.core.logger.AuditLogger
) {

    private val dao: PersonalReminderDao = database.personalReminderDao()

    companion object {
        private const val ENTITY_NAME = "PERSONAL_REMINDER"
    }

    fun getActiveReminders(): Flow<List<PersonalReminderEntity>> = dao.getActiveReminders()
    fun getCompletedReminders(): Flow<List<PersonalReminderEntity>> = dao.getCompletedReminders()
    fun getCancelledReminders(): Flow<List<PersonalReminderEntity>> = dao.getCancelledReminders()
    suspend fun getById(id: String): PersonalReminderEntity? = dao.getById(id)

    suspend fun createReminder(reminder: PersonalReminderEntity) {
        val userName = sessionManager.getCurrentUserName()
        val now = PatientUtils.getCurrentIsoTimestamp()
        dao.insert(
            reminder.copy(
                createdAt = now,
                updatedAt = now,
                createdBy = userName,
                updatedBy = userName,
                isSynced = false
            )
        )
        syncRepository.enqueue(ENTITY_NAME, reminder.id, SyncOperation.CREATE, SyncPriority.MEDIUM)
        auditLogger.log(
            module = "REMINDER",
            entityType = "PERSONAL_REMINDER",
            entityId = reminder.id,
            action = "CREATED",
            remarks = "${reminder.patientName} - ${reminder.vaccineLabel ?: "Other"}"
        )
    }

    suspend fun updateReminder(reminder: PersonalReminderEntity) {
        val userName = sessionManager.getCurrentUserName()
        dao.insert(
            reminder.copy(
                updatedAt = PatientUtils.getCurrentIsoTimestamp(),
                updatedBy = userName,
                isSynced = false
            )
        )
        syncRepository.enqueue(ENTITY_NAME, reminder.id, SyncOperation.UPDATE, SyncPriority.MEDIUM)
        auditLogger.log(
            module = "REMINDER",
            entityType = "PERSONAL_REMINDER",
            entityId = reminder.id,
            action = "UPDATED",
            remarks = "${reminder.patientName} - ${reminder.vaccineLabel ?: "Other"}"
        )
    }

    // Every transition below is only ever invoked from an explicit user action in the
    // UI (see PersonalReminderViewModel) - nothing in this repository infers a status
    // change from vaccination, payment, or inventory activity.

    suspend fun markReady(id: String) {
        updateStatus(id, PersonalReminderStatus.READY)
    }

    suspend fun markPending(id: String) {
        updateStatus(id, PersonalReminderStatus.PENDING)
    }

    suspend fun markCompleted(id: String) {
        val existing = dao.getById(id) ?: return
        val userName = sessionManager.getCurrentUserName()
        val now = PatientUtils.getCurrentIsoTimestamp()
        dao.insert(
            existing.copy(
                status = PersonalReminderStatus.COMPLETED.name,
                completedAt = now,
                updatedAt = now,
                updatedBy = userName,
                isSynced = false
            )
        )
        syncRepository.enqueue(ENTITY_NAME, id, SyncOperation.UPDATE, SyncPriority.MEDIUM)
        auditLogger.log(
            module = "REMINDER",
            entityType = "PERSONAL_REMINDER",
            entityId = id,
            action = "COMPLETED",
            remarks = "${existing.patientName} - ${existing.vaccineLabel ?: "Other"}"
        )
    }

    suspend fun cancel(id: String) {
        val existing = dao.getById(id) ?: return
        val userName = sessionManager.getCurrentUserName()
        val now = PatientUtils.getCurrentIsoTimestamp()
        dao.insert(
            existing.copy(
                status = PersonalReminderStatus.CANCELLED.name,
                cancelledAt = now,
                updatedAt = now,
                updatedBy = userName,
                isSynced = false
            )
        )
        syncRepository.enqueue(ENTITY_NAME, id, SyncOperation.UPDATE, SyncPriority.MEDIUM)
        auditLogger.log(
            module = "REMINDER",
            entityType = "PERSONAL_REMINDER",
            entityId = id,
            action = "CANCELLED",
            remarks = "${existing.patientName} - ${existing.vaccineLabel ?: "Other"}"
        )
    }

    private suspend fun updateStatus(id: String, status: PersonalReminderStatus) {
        val existing = dao.getById(id) ?: return
        val userName = sessionManager.getCurrentUserName()
        dao.insert(
            existing.copy(
                status = status.name,
                updatedAt = PatientUtils.getCurrentIsoTimestamp(),
                updatedBy = userName,
                isSynced = false
            )
        )
        syncRepository.enqueue(ENTITY_NAME, id, SyncOperation.UPDATE, SyncPriority.MEDIUM)
        auditLogger.log(
            module = "REMINDER",
            entityType = "PERSONAL_REMINDER",
            entityId = id,
            action = status.name,
            remarks = "${existing.patientName} - ${existing.vaccineLabel ?: "Other"}"
        )
    }

    suspend fun deleteReminder(id: String) {
        val existing = dao.getById(id)
        dao.delete(id)
        syncRepository.enqueue(ENTITY_NAME, id, SyncOperation.DELETE, SyncPriority.MEDIUM)
        auditLogger.log(
            module = "REMINDER",
            entityType = "PERSONAL_REMINDER",
            entityId = id,
            action = "DELETED",
            remarks = existing?.let { "${it.patientName} - ${it.vaccineLabel ?: "Other"}" }
        )
    }

    suspend fun refresh() {
        try {
            val remote = postgrest.from("personal_vaccine_reminders").select()
                .decodeList<PersonalReminderEntity>()
            Log.d("PersonalReminder", "Remote refresh: fetched ${remote.size} reminders")
            remote.forEach { r ->
                val local = dao.getById(r.id)
                if (local == null || local.isSynced) {
                    dao.insert(r.copy(isSynced = true))
                }
            }
        } catch (e: Exception) {
            Log.e("PersonalReminder", "Refresh failed", e)
            throw e
        }
    }
}
