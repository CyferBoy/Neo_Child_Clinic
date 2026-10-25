package com.neochildclinic.feature.personalreminder.data
import com.neochildclinic.feature.personalreminder.domain.repository.PersonalReminderRepository

import android.util.Log
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.data.local.dao.PersonalReminderDao
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.model.PersonalReminder
import com.neochildclinic.domain.model.PersonalReminderStatus
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.cloudRefresh
import com.neochildclinic.core.sync.fetchAllPages
import com.neochildclinic.core.sync.RemotePrune

@Singleton
class PersonalReminderRepositoryImpl @Inject constructor(
    private val dao: PersonalReminderDao,
    private val syncRepository: SyncRepository,
    private val syncQueueDao: com.neochildclinic.data.local.dao.SyncQueueDao,
    private val postgrest: Postgrest,
    private val sessionManager: SessionManager,
    private val auditLogger: com.neochildclinic.core.logger.AuditLogger
) : PersonalReminderRepository {


    companion object {
        private const val ENTITY_NAME = "PERSONAL_REMINDER"
    }

    override fun getActiveReminders(): Flow<List<PersonalReminder>> = dao.getActiveReminders().map { it.map(PersonalReminderEntity::toDomain) }
    override fun getCompletedReminders(): Flow<List<PersonalReminder>> = dao.getCompletedReminders().map { it.map(PersonalReminderEntity::toDomain) }
    override fun getCancelledReminders(): Flow<List<PersonalReminder>> = dao.getCancelledReminders().map { it.map(PersonalReminderEntity::toDomain) }
    override suspend fun getById(id: String): PersonalReminder? = dao.getById(id)?.toDomain()

    override suspend fun createReminder(reminder: PersonalReminder) {
        val userName = sessionManager.getCurrentUserName()
        val now = PatientUtils.getCurrentIsoTimestamp()
        dao.insert(
            reminder.toEntity(
                isSynced = false, createdBy = userName, updatedBy = userName
            ).copy(createdAt = now, updatedAt = now)
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

    override suspend fun updateReminder(reminder: PersonalReminder) {
        val userName = sessionManager.getCurrentUserName()
        val existing = dao.getById(reminder.id)
        dao.insert(
            reminder.toEntity(existing = existing, isSynced = false, updatedBy = userName)
                .copy(updatedAt = PatientUtils.getCurrentIsoTimestamp())
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

    override suspend fun markReady(id: String) {
        updateStatus(id, PersonalReminderStatus.READY)
    }

    override suspend fun markPending(id: String) {
        updateStatus(id, PersonalReminderStatus.PENDING)
    }

    override suspend fun markCompleted(id: String) {
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

    override suspend fun cancel(id: String) {
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

    override suspend fun deleteReminder(id: String) {
        val userName = sessionManager.getCurrentUserName()
        val now = PatientUtils.getCurrentIsoTimestamp()
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

    override suspend fun refresh() = cloudRefresh("PersonalReminder", rethrow = true) {
        val remote = fetchAllPages { from, to ->
            postgrest.from("personal_vaccine_reminders")
                .select {
                    filter { eq("is_deleted", false) }
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<PersonalReminderEntity>()
        }
        Log.d("PersonalReminder", "Remote refresh: fetched " + remote.size + " reminders")
        // One bulk queue read per refresh instead of one per row (SyncQueueDao.unsyncedIdSet
        // callers) - same semantics as the per-row isUnsynced call this replaces.
        val pendingReminderIds = syncQueueDao.unsyncedIdSet("PERSONAL_REMINDER")
        remote.forEach { r ->
            val local = dao.getById(r.id)
            // Queue-aware guard: see FinanceRepositoryImpl - isSynced alone is not enough,
            // a row edited during its own upload would be rolled back by this pull.
            if (local == null || (local.isSynced && r.id !in pendingReminderIds)) {
                dao.insert(r.copy(isSynced = true))
            }
        }

        // Remote-deletion propagation (NCC-023 / DA-01): synced reminders the remote set no
        // longer lists and no queue entry claims leave this device (patient link cascades,
        // vaccine reference is SET NULL).
        RemotePrune.computeBatches(
            localIds = dao.getPersonalReminderIds(),
            syncedIds = dao.getSyncedPersonalReminderIds().toSet(),
            remoteIds = remote.mapTo(HashSet()) { it.id },
            pendingIds = pendingReminderIds
        ).flatten().forEach { dao.delete(it) }
    }
}
