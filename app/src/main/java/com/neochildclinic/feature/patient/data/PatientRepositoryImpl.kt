package com.neochildclinic.feature.patient.data

import com.neochildclinic.feature.patient.domain.repository.PatientRepository
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.data.remote.PatientRemoteDataSource
import com.neochildclinic.data.local.dao.PatientDao
import com.neochildclinic.data.local.dao.PatientNotesDao
import com.neochildclinic.data.local.dao.DueReminderDao
import com.neochildclinic.data.local.dao.PersonalReminderDao
import com.neochildclinic.data.local.dao.ConsultationDao
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.PatientNotesEntity
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.domain.model.PatientNote
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import com.neochildclinic.feature.vaccination.domain.repository.VaccinationRepository
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.feature.patient.data.PatientIdGenerator
import com.neochildclinic.data.local.database.AppDatabase
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import com.neochildclinic.core.preferences.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.cloudRefresh

@Singleton
class PatientRepositoryImpl @Inject constructor(
    private val patientDao: PatientDao,
    private val notesDao: PatientNotesDao,
    private val dueReminderDao: DueReminderDao,
    private val personalReminderDao: PersonalReminderDao,
    private val consultationDao: ConsultationDao,
    private val vaccinationDao: VaccinationDao,
    private val syncQueueDao: SyncQueueDao,
    private val remoteDataSource: PatientRemoteDataSource,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger,
    private val idGenerator: PatientIdGenerator,
    private val preferenceManager: PreferenceManager,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context,
    private val vaccinationRepository: dagger.Lazy<VaccinationRepository>,
    private val patientCache: PatientCache,
    private val database: AppDatabase
) : PatientRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Room is the source of truth. Any write to `patients` - including ones that bypass
        // this repository (sync pull/upload, backup restore, ID migration worker) - drops the
        // in-memory copies so they can never outlive the row they mirror.
        database.invalidationTracker.addObserver(object : InvalidationTracker.Observer("patients") {
            override fun onInvalidated(tables: Set<String>) = patientCache.clear()
        })
        repositoryScope.launch {
            if (!preferenceManager.isPatientIdMigrationCompleted.first()) {
                schedulePatientIdMigration()
            }
        }
    }

    private fun schedulePatientIdMigration() {
        val request = OneTimeWorkRequestBuilder<PatientClinicIdMigrationWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PatientClinicIdMigrationWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    // Room Flow -> UI directly. Flows are never cached.
    override val allPatients: Flow<List<Patient>> = patientDao.getAllPatients()

    override suspend fun getPatientById(id: String): Patient? =
        patientCache.getOrLoad(id) { patientDao.getPatientById(id) }

    override suspend fun refreshPatients() = cloudRefresh("PatientRepo", rethrow = true) {
        val entities = remoteDataSource.fetchAllPatients()
        android.util.Log.d("PatientRepo", "Pulled ${entities.size} patients from Supabase")

        // The queue guard and the local rows (looked up by id and by clinic id) are each
        // built once, outside the loop. The deleted getPatientsByIds took one bind
        // parameter per remote patient (SQLite allows 999 on API 29/30) and had no
        // empty-pull guard; the parameterless snapshot has neither problem.
        val pendingPatientIds = syncQueueDao.unsyncedIdSet("PATIENT")
        val localPatients = patientDao.getAllPatientsSnapshot()
        val localById = localPatients.associateBy { it.id }
        // patientClinicId has a unique index, so there is at most one owner per id.
        val clinicIdOwner = localPatients
            .mapNotNull { p -> p.patientClinicId?.takeIf { it.isNotBlank() }?.let { it to p.id } }
            .toMap().toMutableMap()

        for (entity in entities) {
            try {
                val existingLocal = localById[entity.id]
                val localClinicId = when {
                    entity.patientClinicId?.isNotBlank() == true && !entity.patientClinicId.startsWith("TEMP-") ->
                        entity.patientClinicId
                    existingLocal != null && existingLocal.patientClinicId?.isNotBlank() == true && !existingLocal.patientClinicId.startsWith("TEMP-") ->
                        existingLocal.patientClinicId
                    else -> "TEMP-${entity.id}"
                }

                if (!localClinicId.startsWith("TEMP-")) {
                    val existingOwnerId = clinicIdOwner[localClinicId]
                    if (existingOwnerId != null && existingOwnerId != entity.id) {
                        val resolvedId = localClinicId + "-CONFLICT-" + entity.id.take(4)
                        // Upsert, not insertPatient: this row can already exist (same remote id
                        // pulled again) and a REPLACE would delete it and cascade its children.
                        patientDao.upsertPatient(entity.copy(
                            patientClinicId = resolvedId,
                            updatedAt = entity.updatedAt?.takeIf { it.isNotEmpty() } ?: com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
                            isSynced = true
                        ))
                        clinicIdOwner[resolvedId] = entity.id
                        continue
                    }
                }

                if ((existingLocal == null || existingLocal.isSynced) && entity.id !in pendingPatientIds) {
                    // Upsert: REPLACE would delete this patient row and cascade away every
                    // visit/item/consultation/reminder/note under it.
                    patientDao.upsertPatient(entity.copy(
                        patientClinicId = localClinicId,
                        updatedAt = entity.updatedAt?.takeIf { it.isNotEmpty() } ?: com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
                        isSynced = true
                    ))
                    // Keep the map in step with the writes this loop just made, the way the
                    // old per-row query saw earlier iterations' rows.
                    clinicIdOwner[localClinicId] = entity.id
                }
            } catch (e: Exception) {
                android.util.Log.e("PatientRepo", "Insert failed for patient ${entity.id}", e)
            }
        }
        val countFlow = patientDao.getPatientCount()
        val totalCount = countFlow.first()
        android.util.Log.d("PatientRepo", "Refresh complete. Total local: $totalCount")
        
        patientCache.clear()
    }

    override suspend fun addPatient(patient: Patient) {
        val isUpdate = patientDao.getPatientById(patient.id) != null
        val userName = sessionManager.getCurrentUserName()
        // BUG-004: mint the clinic ID and insert inside one transaction so concurrent
        // saves cannot both land on the same ID; the create path uses the ABORT insert
        // so a collision fails loudly instead of REPLACE-deleting the other patient.
        val entity = database.withTransaction {
            val finalClinicId = if (patient.patientClinicId.isNullOrBlank()) {
                idGenerator.generateUniqueClinicId()
            } else {
                if (patient.patientClinicId.startsWith("TEMP-")) {
                    throw IllegalArgumentException("Invalid Clinic ID format.")
                }
                if (!idGenerator.isIdUnique(patient.patientClinicId, patient.id)) {
                    throw IllegalStateException("A patient with Clinic ID ${patient.patientClinicId} already exists.")
                }
                patient.patientClinicId
            }

            val built = patient.copy(
                patientClinicId = finalClinicId,
                createdBy = if (isUpdate) patient.createdBy else userName,
                updatedBy = userName,
                updatedAt = patient.updatedAt?.takeIf { it.isNotEmpty() } ?: com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
                isSynced = false
            )
            if (isUpdate) patientDao.upsertPatient(built) else patientDao.insertPatientStrict(built)
            // Enqueue inside the same transaction as the write (docs/SYNC.md: entity
            // writes and enqueue commit together - no partial state). Follows
            // VaccinationRepositoryImpl.addVaccination's pattern.
            syncRepository.enqueue(
                entityName = "PATIENT",
                entityId = patient.id,
                operation = if (isUpdate) SyncOperation.UPDATE else SyncOperation.CREATE,
                priority = SyncPriority.HIGH
            )
            built
        }
        
        auditLogger.recordLog(
            module = "PATIENT",
            entityType = "PATIENT",
            entityId = patient.id,
            action = if (isUpdate) "UPDATED" else "CREATED",
            patientId = patient.id,
            remarks = if (isUpdate) "Patient ${patient.name} updated" else "Patient ${patient.name} registered"
        )
        
        patientCache.invalidate(patient.id)
    }

    override suspend fun deletePatient(id: String) {
        // One transaction for the whole delete (docs/SYNC.md: entity writes and enqueue
        // commit together - no partial state). Previously every deleteX() was followed by
        // its own enqueue outside any transaction, so a crash mid-way left deletes without
        // their queue rows (remote rows surviving) or queue rows without deletes.
        database.withTransaction {
            val vaccinationIds = vaccinationDao.getVaccinationsForPatient(id).first().map { it.id }
            val reminderIds = dueReminderDao.getDueRemindersForPatient(id).first().map { it.id }
            val personalReminderIds = personalReminderDao.getActiveReminders().first().filter { it.patientId == id }.map { it.id }
            val consultationIds = consultationDao.getConsultationsForPatient(id).first().map { it.id }

            // Delete Reminders
            dueReminderDao.deleteRemindersByPatientId(id)
            reminderIds.forEach {
                syncRepository.enqueue("REMINDERS", it, SyncOperation.DELETE, SyncPriority.LOW)
            }

            personalReminderIds.forEach {
                personalReminderDao.delete(it)
                syncRepository.enqueue("PERSONAL_REMINDER", it, SyncOperation.DELETE, SyncPriority.LOW)
            }

            // Delete Vaccinations/Visits (deleteVaccination enqueues their own DELETEs).
            // Clear the local inventory_deductions ledger for these visits FIRST:
            // deleteVaccination replenishes stock from every COMPLETED ledger row, so
            // deleting a patient used to put every dose ever administered to that child
            // back on the shelf - inventing stock for doses that were physically given.
            // Dropping the ledger first makes the delete records-only. The visit, its
            // finance row and the inventory_transactions audit trail are still removed.
            // Not restoring is the safe direction: an over-counted shelf could re-issue a
            // dose a child already received, while an under-count is a visible discrepancy
            // staff can reconcile.
            vaccinationIds.forEach { database.inventoryDeductionDao().deleteForVaccination(it) }
            vaccinationIds.forEach {
                vaccinationRepository.get().deleteVaccination(it)
            }

            // Delete Consultations
            consultationIds.forEach {
                consultationDao.deleteConsultation(it)
                syncRepository.enqueue("CONSULTATION", it, SyncOperation.DELETE, SyncPriority.MEDIUM)
            }

            // Delete Patient - enqueued last: every child is enqueued above so the server-side
            // DELETE for patients can't hit a foreign-key reference from a child still on the row.
            patientDao.deletePatient(id)
            syncRepository.enqueue("PATIENT", id, SyncOperation.DELETE, SyncPriority.MEDIUM)

            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "PATIENT",
                entityId = id,
                action = "DELETED",
                patientId = id
            )
        }

        patientCache.invalidate(id)
    }

    // Reactive Room query: always live, never served from memory.
    override fun searchPatients(query: String): Flow<List<Patient>> =
        patientDao.searchPatients(query)

    override fun getPatientCount(): Flow<Int> = patientDao.getPatientCount()

    override val patientIdsWithMissingPrice: Flow<Set<String>> =
        patientDao.getPatientIdsWithMissingPrice().map { it.toSet() }

    override fun getNotes(patientId: String): Flow<List<PatientNote>> =
        notesDao.getNotesForPatient(patientId).map { rows -> rows.map { it.toDomain() } }
}
