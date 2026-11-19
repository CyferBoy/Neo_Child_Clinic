package com.neochildclinic.feature.patient.data

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import com.neochildclinic.core.database.TransactionRunner
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.data.local.dao.PatientDao
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.core.preferences.PreferenceManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class PatientClinicIdMigrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val patientDao: PatientDao,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger,
    private val preferenceManager: PreferenceManager,
    private val idGenerator: PatientIdGenerator,
    private val transactionRunner: TransactionRunner
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Migration started: Assigning patientClinicIds to existing patients.")

        try {
            val patientsToMigrate = patientDao.getPatientsNeedingId()
            Log.d(TAG, "Patients scanned: ${patientsToMigrate.size} needing IDs.")

            if (patientsToMigrate.isEmpty()) {
                Log.i(TAG, "Migration completed: No patients needing IDs.")
                preferenceManager.setPatientIdMigrationCompleted(true)
                return Result.success()
            }

            transactionRunner.run {
                for (patient in patientsToMigrate) {
                    // Same generator as new patients, so legacy rows get the per-install
                    // suffix that prevents cross-device clinic-ID collisions.
                    val clinicId = idGenerator.generateUniqueClinicId()

                    val updatedPatient = patient.copy(
                        patientClinicId = clinicId,
                        isSynced = false
                    )
                    patientDao.upsertPatient(updatedPatient)

                    syncRepository.enqueue(
                        entityName = "PATIENT",
                        entityId = patient.id,
                        operation = SyncOperation.UPDATE,
                        priority = SyncPriority.MEDIUM
                    )

                    auditLogger.recordLog(
                        module = "SYSTEM",
                        entityType = "PATIENT",
                        entityId = patient.id,
                        action = "MIGRATED_ID",
                        patientId = patient.id,
                        remarks = "Assigned Clinic ID: $clinicId (Legacy migration)"
                    )
                }
            }

            preferenceManager.setPatientIdMigrationCompleted(true)
            Log.i(TAG, "Migration completed successfully: ${patientsToMigrate.size} patients updated.")
            return Result.success()

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Migration failed", e)
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "PatientIdMigration"
        const val WORK_NAME = "PatientClinicIdMigrationWork"
    }
}
