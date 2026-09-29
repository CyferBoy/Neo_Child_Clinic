package com.neochildclinic.feature.patient.data

import com.neochildclinic.feature.patient.domain.repository.PatientRepository
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.data.remote.PatientRemoteDataSource
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
import com.neochildclinic.core.cache.MemoryCache
import com.neochildclinic.core.cache.QueryCacheKey
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
    private val localDataSource: PatientLocalDataSource,
    private val remoteDataSource: PatientRemoteDataSource,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger,
    private val idGenerator: PatientIdGenerator,
    private val preferenceManager: PreferenceManager,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context,
    private val vaccinationRepository: dagger.Lazy<VaccinationRepository>,
    private val patientCache: MemoryCache<String, Patient>,
    private val patientListCache: MemoryCache<QueryCacheKey, List<Patient>>
) : PatientRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
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

    override val allPatients: Flow<List<Patient>> =
        localDataSource.getAllPatients()
            .onEach { patients ->
                val key = QueryCacheKey(entityType = "PATIENT")
                repositoryScope.launch { patientListCache.put(key, patients) }
            }

    override suspend fun getPatientById(id: String): Patient? {
        val cached = patientCache.get(id)
        if (cached != null) return cached
        
        val entity = localDataSource.getPatientById(id)
        entity?.let { patientCache.put(id, it) }
        return entity
    }

    override suspend fun refreshPatients() = cloudRefresh("PatientRepo", rethrow = true) {
        val entities = remoteDataSource.fetchAllPatients()
        android.util.Log.d("PatientRepo", "Pulled ${entities.size} patients from Supabase")

        for (entity in entities) {
            try {
                val existingLocal = localDataSource.getPatientById(entity.id)
                val localClinicId = when {
                    entity.patientClinicId?.isNotBlank() == true && !entity.patientClinicId.startsWith("TEMP-") ->
                        entity.patientClinicId
                    existingLocal != null && existingLocal.patientClinicId?.isNotBlank() == true && !existingLocal.patientClinicId.startsWith("TEMP-") ->
                        existingLocal.patientClinicId
                    else -> "TEMP-${entity.id}"
                }

                if (!localClinicId.startsWith("TEMP-")) {
                    val existingByClinicId = localDataSource.getPatientByClinicId(localClinicId)
                    if (existingByClinicId != null && existingByClinicId.id != entity.id) {
                        val resolvedId = localClinicId + "-CONFLICT-" + entity.id.take(4)
                        localDataSource.insertPatient(entity.copy(
                            patientClinicId = resolvedId,
                            updatedAt = entity.updatedAt?.takeIf { it.isNotEmpty() } ?: com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
                            isSynced = true
                        ))
                        continue
                    }
                }

                if ((existingLocal == null || existingLocal.isSynced) && !localDataSource.isUnsyncedPatient(entity.id)) {
                    localDataSource.insertPatient(entity.copy(
                        patientClinicId = localClinicId,
                        updatedAt = entity.updatedAt?.takeIf { it.isNotEmpty() } ?: com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
                        isSynced = true
                    ))
                }
            } catch (e: Exception) {
                android.util.Log.e("PatientRepo", "Insert failed for patient ${entity.id}", e)
            }
        }
        val countFlow = localDataSource.getPatientCount()
        val totalCount = countFlow.first()
        android.util.Log.d("PatientRepo", "Refresh complete. Total local: $totalCount")
        
        invalidatePatientListCache()
    }

    override suspend fun addPatient(patient: Patient) {
        val isUpdate = localDataSource.getPatientById(patient.id) != null
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
            updatedAt = patient.updatedAt?.takeIf { it.isNotEmpty() } ?: com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
            isSynced = false
        )
        localDataSource.insertPatient(entity)
        
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
        
        invalidatePatientCache(patient.id)
        invalidatePatientListCache()
    }

    override suspend fun deletePatient(id: String) {
        val userName = sessionManager.getCurrentUserName()
        val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()

        val vaccinationIds = localDataSource.getVaccinationsForPatient(id)?.first()?.map { it.id } ?: emptyList()
        val reminderIds = localDataSource.getDueRemindersForPatient(id)?.first()?.map { it.id } ?: emptyList()
        val personalReminderIds = localDataSource.getActivePersonalReminders()?.first()?.filter { it.patientId == id }?.map { it.id } ?: emptyList()
        val consultationIds = localDataSource.getConsultationsForPatient(id)?.first()?.map { it.id } ?: emptyList()

        // Delete Reminders
        localDataSource.deleteRemindersByPatientId(id, now, userName)
        reminderIds.forEach {
            syncRepository.enqueue("REMINDERS", it, SyncOperation.UPDATE, SyncPriority.LOW)
        }

        personalReminderIds.forEach {
            localDataSource.deletePersonalReminder(it, now, userName)
            syncRepository.enqueue("PERSONAL_REMINDER", it, SyncOperation.UPDATE, SyncPriority.LOW)
        }

        // Delete Vaccinations/Visits
        vaccinationIds.forEach {
            vaccinationRepository.get().deleteVaccination(it)
        }

        // Delete Consultations
        consultationIds.forEach {
            localDataSource.deleteConsultation(it, now, userName)
            syncRepository.enqueue("CONSULTATION", it, SyncOperation.UPDATE, SyncPriority.MEDIUM)
        }

        // Delete Patient
        localDataSource.deletePatient(id, now, userName)
        syncRepository.enqueue("PATIENT", id, SyncOperation.UPDATE, SyncPriority.MEDIUM)

        auditLogger.recordLog(
            module = "PATIENT",
            entityType = "PATIENT",
            entityId = id,
            action = "SOFT_DELETED",
            patientId = id
        )
        
        invalidatePatientCache(id)
        invalidatePatientListCache()
    }

    override fun searchPatients(query: String): Flow<List<Patient>> {
        val key = QueryCacheKey(entityType = "PATIENT", query = query)
        val cached = patientListCache.get(key)
        if (cached != null) return flowOf(cached)
        
        return localDataSource.searchPatients(query)
            .onEach { patients ->
                repositoryScope.launch { patientListCache.put(key, patients) }
            }
    }

    override fun getPatientCount(): Flow<Int> = localDataSource.getPatientCount()

    override fun getNotes(patientId: String): Flow<List<PatientNote>> =
        localDataSource.getNotesForPatient(patientId).map { rows -> rows.map { it.toDomain() } }

    private suspend fun invalidatePatientCache(id: String) {
        patientCache.invalidate(id)
    }

    private suspend fun invalidatePatientListCache() {
        patientListCache.invalidateAll(
            listOf(
                QueryCacheKey(entityType = "PATIENT"),
                QueryCacheKey(entityType = "PATIENT", query = ""),
            )
        )
    }
}