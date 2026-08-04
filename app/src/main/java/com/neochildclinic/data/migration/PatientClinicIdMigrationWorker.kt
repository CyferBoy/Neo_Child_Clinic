package com.neochildclinic.data.migration

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.core.preferences.PreferenceManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PatientClinicIdMigrationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger,
    private val preferenceManager: PreferenceManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Migration started: Assigning patientClinicIds to existing patients.")
        val patientDao = database.patientDao()
        
        try {
            val patientsToMigrate = patientDao.getPatientsNeedingId()
            Log.d(TAG, "Patients scanned: ${patientsToMigrate.size} needing IDs.")

            if (patientsToMigrate.isEmpty()) {
                Log.i(TAG, "Migration completed: No patients needing IDs.")
                preferenceManager.setPatientIdMigrationCompleted(true)
                return Result.success()
            }

            database.withTransaction {
                var nextNumber = getStartingNumber(patientDao)
                
                for (patient in patientsToMigrate) {
                    // Generate sequential ID
                    var clinicId = "NEO-$nextNumber"
                    
                    // Safety check against existing IDs
                    while (patientDao.getPatientByClinicId(clinicId) != null) {
                        nextNumber++
                        clinicId = "NEO-$nextNumber"
                    }

                    // Update Room
                    val updatedPatient = patient.copy(
                        patientClinicId = clinicId,
                        isSynced = false
                    )
                    patientDao.insertPatient(updatedPatient)
                    
                    // Queue for Firestore Update
                    syncRepository.enqueue(
                        entityName = "PATIENT",
                        entityId = patient.id,
                        operation = SyncOperation.UPDATE,
                        priority = SyncPriority.MEDIUM
                    )

                    // Log the change
                    auditLogger.recordLog(
                        module = "SYSTEM",
                        entityType = "PATIENT",
                        entityId = patient.id,
                        action = "MIGRATED_ID",
                        patientId = patient.id,
                        remarks = "Assigned Clinic ID: $clinicId (Legacy migration)"
                    )
                    
                    Log.d(TAG, "Updated Patient ${patient.id}: assigned $clinicId")
                    nextNumber++
                }
            }

            preferenceManager.setPatientIdMigrationCompleted(true)
            Log.i(TAG, "Migration completed successfully: ${patientsToMigrate.size} patients updated.")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
            return Result.retry()
        }
    }

    private suspend fun getStartingNumber(patientDao: com.neochildclinic.data.local.dao.PatientDao): Int {
        val maxId = patientDao.getMaxClinicId()
        return if (maxId != null && maxId.startsWith("NEO-")) {
            val numericPart = maxId.substring(4).toIntOrNull() ?: 0
            numericPart + 1
        } else {
            1000
        }
    }

    companion object {
        private const val TAG = "PatientIdMigration"
        const val WORK_NAME = "PatientClinicIdMigrationWork"
    }
}
