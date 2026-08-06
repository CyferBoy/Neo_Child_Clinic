package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.dao.PatientDao
import com.neochildclinic.data.local.dao.DueReminderDao
import com.neochildclinic.data.local.dao.AuditLogDao
import com.neochildclinic.data.local.dao.PatientNotesDao
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.data.local.entity.toPatient
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.data.local.entity.toVaccination
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.utils.PatientIdGenerator
import androidx.room.withTransaction
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.neochildclinic.core.preferences.PreferenceManager
import com.neochildclinic.data.migration.PatientClinicIdMigrationWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val patientDao: PatientDao,
    private val vaccinationDao: VaccinationDao,
    private val dueReminderDao: DueReminderDao,
    private val auditLogDao: AuditLogDao,
    private val notesDao: PatientNotesDao,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger,
    private val idGenerator: PatientIdGenerator,
    private val preferenceManager: PreferenceManager,
    @ApplicationContext private val context: Context
) : PatientRepository {

    init {
        // Schedule migration if not completed
        GlobalScope.launch(Dispatchers.IO) {
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
        patientDao.getAllPatients().map { list -> list.map { it.toPatient() } }

    override suspend fun getPatientById(id: String): Patient? {
        return patientDao.getPatientById(id)?.toPatient()
    }

    override suspend fun refreshPatients() {
        withContext(Dispatchers.IO) {
            try {
                val entities = postgrest.from("patients").select().decodeList<PatientEntity>()
                
                android.util.Log.d("PatientRepo", "Pulled ${entities.size} patients from Supabase")
                
                database.withTransaction {
                    for (entity in entities) {
                        try {
                            val patient = entity.toPatient()
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
                            if (localClinicId != null && !localClinicId.startsWith("TEMP-")) {
                                val existingByClinicId = patientDao.getPatientByClinicId(localClinicId)
                                if (existingByClinicId != null && existingByClinicId.id != patient.id) {
                                    val resolvedId = localClinicId + "-CONFLICT-" + patient.id.take(4)
                                    patientDao.insertPatient(patient.copy(patientClinicId = resolvedId).toEntity())
                                    continue
                                }
                            }

                            // Insert/Update only if local doesn't exist or is already synced
                            if (existingLocal == null || existingLocal.isSynced) {
                                patientDao.insertPatient(patient.copy(patientClinicId = localClinicId).toEntity(isSynced = true))
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PatientRepo", "Insert failed for patient ${entity.id}", e)
                        }
                    }
                }
                android.util.Log.d("PatientRepo", "Refresh complete. Total local: ${patientDao.getTotalPatientCount()}")
            } catch (e: Exception) {
                android.util.Log.e("PatientRepo", "Refresh failed", e)
                throw e // Propagate to UI
            }
        }
    }

    override suspend fun addPatient(patient: Patient) {
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

            val entity = patient.copy(patientClinicId = finalClinicId).toEntity(isSynced = false)
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
        database.withTransaction {
            val vaccinationIds = vaccinationDao.getVaccinationsForPatient(id).first().map { it.id }
            val reminderIds = dueReminderDao.getDueRemindersForPatient(id).first().map { it.id }

            patientDao.deletePatient(id)
            vaccinationDao.deleteVaccinationsForPatient(id)
            dueReminderDao.softDeleteRemindersForPatient(id)
            
            syncRepository.enqueue("PATIENT", id, SyncOperation.DELETE, SyncPriority.MEDIUM)
            vaccinationIds.forEach {
                syncRepository.enqueue("VACCINATION", it, SyncOperation.DELETE, SyncPriority.MEDIUM)
            }
            reminderIds.forEach {
                syncRepository.enqueue("REMINDER_STATE", it.toString(), SyncOperation.DELETE, SyncPriority.LOW)
            }
        }

        auditLogger.recordLog(
            module = "PATIENT",
            entityType = "PATIENT",
            entityId = id,
            action = "DELETED",
            patientId = id
        )
    }

    override fun searchPatients(query: String): Flow<List<Patient>> =
        patientDao.searchPatients(query).map { list -> list.map { it.toPatient() } }

    override fun getPatientCount(): Flow<Int> = patientDao.getPatientCount()

    override suspend fun getTotalPatientCount(): Int = patientDao.getTotalPatientCount()

    override fun getPatientTimeline(patientId: String): Flow<List<AuditLogEntity>> {
        return auditLogDao.getLogsForPatient(patientId)
    }

    override fun getPatientHistory(patientId: String): Flow<List<Vaccination>> {
        return vaccinationDao.getVaccinationsForPatient(patientId).map { list ->
            list.map { it.toVaccination() }
        }
    }

    override fun getNotes(patientId: String): Flow<List<PatientNotesEntity>> {
        return notesDao.getNotesForPatient(patientId)
    }

    override suspend fun addNote(patientId: String, content: String, author: String) {
        val note = PatientNotesEntity(
            patientId = patientId,
            content = content,
            author = author
        )
        notesDao.insertNote(note)
        syncRepository.enqueue("PATIENT_NOTE", note.id, SyncOperation.CREATE, SyncPriority.LOW)
    }

    override suspend fun deleteNote(noteId: String) {
        notesDao.deleteNote(noteId)
        syncRepository.enqueue("PATIENT_NOTE", noteId, SyncOperation.DELETE, SyncPriority.LOW)
    }

    override suspend fun refreshNotes() {
        withContext(Dispatchers.IO) {
            try {
                val entities = postgrest.from("patient_notes").select().decodeList<PatientNotesEntity>()
                database.withTransaction {
                    for (remote in entities) {
                        val local = notesDao.getNoteById(remote.id)
                        if (local == null || local.isSynced) {
                            notesDao.insertNote(remote.copy(isSynced = true))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
