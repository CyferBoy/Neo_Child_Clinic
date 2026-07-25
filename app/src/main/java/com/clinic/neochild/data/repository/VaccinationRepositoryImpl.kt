package com.clinic.neochild.data.repository

import com.clinic.neochild.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.clinic.neochild.data.local.entity.toVaccination
import com.clinic.neochild.data.local.entity.toEntity
import com.clinic.neochild.core.model.SyncOperation
import com.clinic.neochild.core.model.SyncPriority
import com.clinic.neochild.core.logger.AuditLogger
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.repository.SyncRepository
import com.clinic.neochild.domain.repository.VaccinationRepository
import com.clinic.neochild.domain.repository.InventoryRepository
import com.clinic.neochild.data.local.dao.InventoryDeductionDao
import com.clinic.neochild.data.remote.mapper.FirestoreMappers
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccinationRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val syncRepository: SyncRepository,
    private val inventoryRepository: InventoryRepository,
    private val auditLogger: AuditLogger
) : VaccinationRepository {

    private val vaccinationDao = database.vaccinationDao()
    private val inventoryDeductionDao = database.inventoryDeductionDao()
    private val patientDao = database.patientDao()

    override val allVaccinations: Flow<List<Vaccination>> = 
        vaccinationDao.getAllVaccinations().map { list -> list.map { it.toVaccination() } }

    override fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>> = 
        vaccinationDao.getVaccinationsForPatient(patientId).map { list -> list.map { it.toVaccination() } }

    override suspend fun refreshVaccinations() {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("vaccinations").get().await()
                val totalDownloaded = snapshot.documents.size
                var imported = 0
                var failedMapping = 0
                var failedValidation = 0
                var skippedMissingPatient = 0

                val vaccinations = snapshot.documents.mapNotNull { doc ->
                    val domain = FirestoreMappers.toVaccination(doc)
                    if (domain == null) {
                        failedMapping++
                        return@mapNotNull null
                    }
                    
                    // Basic Validation before Room insert
                    if (domain.id.isBlank() || domain.patientId.isBlank()) {
                        android.util.Log.e("VaccinationRepo", "Validation failed for ${domain.id}: patientId=${domain.patientId}")
                        failedValidation++
                        return@mapNotNull null
                    }
                    
                    domain
                }

                database.withTransaction {
                    for (remote in vaccinations) {
                        // FOREIGN KEY CHECK: Ensure patient exists locally before inserting visit
                        val patientExists = patientDao.getPatientById(remote.patientId) != null
                        if (!patientExists) {
                            android.util.Log.e("VaccinationRepo", "FK Violation Avoided: Skipping Vaccination ${remote.id} because Patient ${remote.patientId} is missing locally.")
                            skippedMissingPatient++
                            continue
                        }

                        val local = vaccinationDao.getVaccinationById(remote.id)
                        if (local == null || local.isSynced) {
                            vaccinationDao.insertVaccination(remote.toEntity(isSynced = true))
                            imported++
                        }
                    }
                }
                
                android.util.Log.i("VaccinationRepo", """
                    Sync Complete:
                    - Total Downloaded: $totalDownloaded
                    - Successfully Imported: $imported
                    - Failed Mapping (Schema Issues): $failedMapping
                    - Failed Validation (Missing Data): $failedValidation
                    - Skipped (Missing Patients): $skippedMissingPatient
                """.trimIndent())
                
            } catch (e: Exception) {
                android.util.Log.e("VaccinationRepo", "Cloud Refresh failed", e)
            }
        }
    }

    override suspend fun addVaccination(vaccination: Vaccination) {
        database.withTransaction {
            // 1. Check if it's an update
            val existing = vaccinationDao.getVaccinationById(vaccination.id)
            
            // 2. Save locally
            vaccinationDao.insertVaccination(vaccination.toEntity(isSynced = false))
            
            // 3. Queue for background sync
            val operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE
            
            syncRepository.enqueue(
                entityName = "VACCINATION",
                entityId = vaccination.id,
                operation = operation,
                priority = SyncPriority.HIGH
            )
            
            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "VACCINATION",
                entityId = vaccination.id,
                action = "VACCINATION",
                patientId = vaccination.patientId,
                remarks = "Vaccines: ${vaccination.vaccineNames.joinToString(", ")}"
            )
        }
    }

    override suspend fun deleteVaccination(id: String) {
        database.withTransaction {
            val existing = vaccinationDao.getVaccinationById(id) ?: return@withTransaction
            
            // 1. Identify batches used in this vaccination
            val batchIds = existing.batchIds.split(",").filter { it.isNotBlank() }
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"

            // 2. Replenish inventory atomically
            for (batchId in batchIds) {
                try {
                    inventoryRepository.reverseDeduction(batchId, 1, user)
                } catch (e: Exception) {
                    android.util.Log.e("VaccinationRepo", "Failed to replenish stock for batch $batchId: ${e.message}")
                }
            }

            // 3. Clean up deduction logs
            inventoryDeductionDao.deleteForVaccination(id)

            // 4. Soft-delete the record
            vaccinationDao.deleteVaccination(id)
            
            syncRepository.enqueue(
                entityName = "VACCINATION",
                entityId = id,
                operation = SyncOperation.DELETE,
                priority = SyncPriority.MEDIUM
            )
            
            auditLogger.recordLog(
                module = "PATIENT",
                entityType = "VACCINATION",
                entityId = id,
                action = "DELETED",
                patientId = existing.patientId,
                remarks = "Vaccines: ${existing.vaccineNames}"
            )
        }
    }

    override suspend fun markAsDone(id: String) {
        database.withTransaction {
            val current = vaccinationDao.getVaccinationById(id)
            if (current != null) {
                val updated = current.copy(isDone = true, isSynced = false)
                vaccinationDao.insertVaccination(updated)
                
                syncRepository.enqueue(
                    entityName = "VACCINATION",
                    entityId = id,
                    operation = SyncOperation.UPDATE,
                    priority = SyncPriority.MEDIUM
                )
                
                auditLogger.recordLog(
                    module = "PATIENT",
                    entityType = "VACCINATION",
                    entityId = id,
                    action = "COMPLETED",
                    patientId = current.patientId,
                    remarks = "Vaccines: ${current.vaccineNames}"
                )
            }
        }
    }

    override fun getTodayCount(date: String): Flow<Int> = vaccinationDao.getCountByDate(date)
    override fun getTodayRevenue(date: String): Flow<Double?> = vaccinationDao.getRevenueByDate(date)
    override fun getTodayCash(date: String): Flow<Double?> = vaccinationDao.getCashByDate(date)
    override fun getTodayOnline(date: String): Flow<Double?> = vaccinationDao.getOnlineByDate(date)
    override fun getMonthlyCount(pattern: String): Flow<Int> = vaccinationDao.getMonthlyCount(pattern)
    override fun getMonthlyRevenue(pattern: String): Flow<Double?> = vaccinationDao.getMonthlyRevenue(pattern)
    override fun getVaccineNamesForMonth(pattern: String): Flow<List<String>> = vaccinationDao.getVaccineNamesForMonth(pattern)

    override suspend fun transferVaccinations(duplicateId: String, masterId: String) {
        vaccinationDao.updatePatientId(duplicateId, masterId)
    }
}

