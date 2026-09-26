package com.neochildclinic.data.repository
import com.neochildclinic.domain.repository.PatientRepository

import com.neochildclinic.core.session.SessionManager
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.dao.PatientDao
import com.neochildclinic.data.local.dao.DueReminderDao
import com.neochildclinic.data.local.dao.PatientNotesDao
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.data.repository.SyncRepositoryImpl
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.utils.PatientIdGenerator
import androidx.room.withTransaction
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import com.neochildclinic.core.preferences.PreferenceManager
import com.neochildclinic.data.migration.PatientClinicIdMigrationWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val patientDao: PatientDao,
    private val vaccinationDao: VaccinationDao,
    private val dueReminderDao: DueReminderDao,
    private val notesDao: PatientNotesDao,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepositoryImpl,
    private val auditLogger: AuditLogger,
    private val idGenerator: PatientIdGenerator,
    private val preferenceManager: PreferenceManager,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context,
    private val vaccinationRepository: dagger.Lazy<VaccinationRepositoryImpl>
) : PatientRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Schedule migration if not completed
        repositoryScope.launch {
            if (!preferenceManager.isPatientIdMigrationCompleted.first()) {
                schedulePatientIdMigration()
            }
        }
    }

    private fun schedulePatientIdMigration() {
        val request = OneTimeWorkRequestBuilder<PatientClinicIdMigrationWorker>()
            .build()
        
        WorkManager.getInstance(context).enqueueUniqueWork(
            PatientClinicIdMigrationWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        
        // Note: The worker itself should update preferenceManager when successfully done.
        // But since we want it to run once per app lifecycle if it fails, 
        // we keep the check in init.
    }

    override val allPatients: Flow<List<Patient>> =
        patientDao.getAllPatients()

    suspend fun getPatientById(id: String): Patient? =
        patientDao.getPatientById(id)

    override suspend fun refreshPatients() = cloudRefresh("PatientRepo", rethrow = true) {
                val entities = postgrest.from("patients").select().decodeList<PatientEntity>()
                
                android.util.Log.d("PatientRepo", "Pulled ${entities.size} patients from Supabase")
                
                database.withTransaction {
                    for (entity in entities) {
                        try {
                            val patient = entity
                            val existingLocal = patientDao.getPatientById(patient.id)
                            
                            // Determine the best clinic ID to keep
                            val localClinicId = when {
                                // 1. Incoming from Supabase has a real ID
                                patient.patientClinicId?.isNotBlank() == true && !patient.patientClinicId.startsWith("TEMP-") -> 
                                    patient.patientClinicId
                                
                                // 2. Local already has a real ID (assigned by Worker but not yet synced)
                                existingLocal != null && existingLocal.patientClinicId?.isNotBlank() == true && !existingLocal.patientClinicId.startsWith("TEMP-") -> 
                                    existingLocal.patientClinicId
                                
                                // 3. Fallback to TEMP ID for legacy patients
                                else -> "TEMP-${patient.id}"
                            }

                            // Uniqueness conflict check (only for real IDs)
                            if (!localClinicId.startsWith("TEMP-")) {
                                val existingByClinicId = patientDao.getPatientByClinicId(localClinicId)
                                if (existingByClinicId != null && existingByClinicId.id != patient.id) {
                                    val resolvedId = localClinicId + "-CONFLICT-" + patient.id.take(4)
                                    patientDao.insertPatient(patient.copy(
                                        patientClinicId = resolvedId,
                                        updatedAt = patient.updatedAt?.takeIf { it.isNotEmpty() } ?: com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                                        isSynced = true
                                    ))
                                    continue
                                }
                            }

                            // Insert/Update only if local doesn't exist or is already synced,
                            // AND there's no pending DELETE in the sync queue
                            if ((existingLocal == null || existingLocal.isSynced) && !database.syncQueueDao().isUnsynced("PATIENT", patient.id)) {
                                patientDao.insertPatient(patient.copy(
                                    patientClinicId = localClinicId,
                                    updatedAt = patient.updatedAt?.takeIf { it.isNotEmpty() } ?: com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                                    isSynced = true
                                ))
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PatientRepo", "Insert failed for patient ${entity.id}", e)
                        }
                    }
                }
                android.util.Log.d("PatientRepo", "Refresh complete. Total local: ${patientDao.getTotalPatientCount()}")
    }

    suspend fun addPatient(patient: Patient) {
        database.withTransaction {
            val isUpdate = patientDao.getPatientById(patient.id) != null
            // Business Rule: patientClinicId must be unique. 
            // If empty, generate one.
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

            val userName = sessionManager.getCurrentUserName()
            val entity = patient.copy(
                patientClinicId = finalClinicId,
                createdBy = if (isUpdate) patient.createdBy else userName,
                updatedBy = userName,
                updatedAt = patient.updatedAt?.takeIf { it.isNotEmpty() } ?: com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                isSynced = false
            )
            patientDao.insertPatient(entity)
            
            syncRepository.enqueue(
                entityName = "PATIENT",
                entityId = patient.id,
                operation = if (isUpdate) SyncOperation.UPDATE else SyncOperation.CREATE,
                priority = SyncPriority.HIGH
            )

            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "PATIENT",
                entityId = patient.id,
                action = if (isUpdate) "UPDATED" else "CREATED",
                patientId = patient.id,
                remarks = if (isUpdate) "Patient ${patient.name} updated" else "Patient ${patient.name} registered"
            )
        }
    }

    override suspend fun deletePatient(id: String) {
        val userName = sessionManager.getCurrentUserName()
        val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()

        database.withTransaction {
            val vaccinationIds = vaccinationDao.getVaccinationsForPatient(id).first().map { it.id }
            val reminderIds = dueReminderDao.getDueRemindersForPatient(id).first().map { it.id }
            val personalReminderIds = database.personalReminderDao().getActiveReminders().first().filter { it.patientId == id }.map { it.id }
            val consultationIds = database.consultationDao().getConsultationsForPatient(id).first().map { it.id }

            // 1. Delete Reminders (Children)
            dueReminderDao.deleteRemindersByPatientId(id, now, userName)
            reminderIds.forEach {
                syncRepository.enqueue("REMINDERS", it, SyncOperation.UPDATE, SyncPriority.LOW)
            }

            personalReminderIds.forEach {
                database.personalReminderDao().delete(it, now, userName)
                syncRepository.enqueue("PERSONAL_REMINDER", it, SyncOperation.UPDATE, SyncPriority.LOW)
            }

            // 2. Delete Vaccinations/Visits (Children)
            vaccinationIds.forEach {
                // To maintain proper side effects (inventory reversal), we must run
                // the full delete logic per visit, rather than just soft-deleting them.
                vaccinationRepository.get().deleteVaccination(it)
            }

            // 3. Delete Consultations
            consultationIds.forEach {
                database.consultationDao().deleteConsultation(it, now, userName)
                syncRepository.enqueue("CONSULTATION", it, SyncOperation.UPDATE, SyncPriority.MEDIUM)
            }

            // 4. Delete Patient (Parent)
            patientDao.deletePatient(id, now, userName)
            syncRepository.enqueue("PATIENT", id, SyncOperation.UPDATE, SyncPriority.MEDIUM)

            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "PATIENT",
                entityId = id,
                action = "SOFT_DELETED",
                patientId = id
            )
        }
    }

    override fun searchPatients(query: String): Flow<List<Patient>> =
        patientDao.searchPatients(query)

    fun getPatientCount(): Flow<Int> = patientDao.getPatientCount()

    suspend fun getTotalPatientCount(): Int = patientDao.getTotalPatientCount()

    // NOTE: patient audit history is loaded online-only via PatientAuditLogPager now, not
    // through this repository - see PatientViewModel/PatientListViewModel.

    fun getNotes(patientId: String): Flow<List<PatientNotesEntity>> {
        return notesDao.getNotesForPatient(patientId)
    }

    suspend fun addNote(patientId: String, content: String, author: String) {
        val userName = sessionManager.getCurrentUserName()
        val note = PatientNotesEntity(
            patientId = patientId,
            content = content,
            author = author,
            createdBy = userName,
            updatedBy = userName
        )
        notesDao.insertNote(note)
        syncRepository.enqueue("PATIENT_NOTE", note.id, SyncOperation.CREATE, SyncPriority.LOW)
        auditLogger.recordLog(
            module = "PATIENT",
            entityType = "PATIENT_NOTE",
            entityId = note.id,
            action = "CREATED",
            patientId = patientId
        )
    }

    suspend fun deleteNote(noteId: String) {
        val userName = sessionManager.getCurrentUserName()
        val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
        notesDao.deleteNote(noteId, now, userName)
        syncRepository.enqueue("PATIENT_NOTE", noteId, SyncOperation.UPDATE, SyncPriority.LOW)
        auditLogger.recordLog(
            module = "PATIENT",
            entityType = "PATIENT_NOTE",
            entityId = noteId,
            action = "SOFT_DELETED"
        )
    }
}
