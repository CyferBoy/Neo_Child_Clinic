package com.neochildclinic.data.repository

import com.neochildclinic.data.local.database.AppDatabase
import androidx.room.withTransaction
import com.neochildclinic.data.local.entity.toVaccination
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.repository.InventoryRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccinationRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val auth: Auth,
    private val syncRepository: SyncRepository,
    private val inventoryRepository: InventoryRepository,
    private val auditLogger: AuditLogger
) : VaccinationRepository {

    private val vaccinationDao = database.vaccinationDao()
    private val vaccinationItemDao = database.vaccinationItemDao()
    private val inventoryDeductionDao = database.inventoryDeductionDao()
    private val patientDao = database.patientDao()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val allVaccinations: Flow<List<Vaccination>> = 
        vaccinationDao.getAllVaccinations().flatMapLatest { list ->
            if (list.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val flows = list.map { entity ->
                vaccinationItemDao.getItemsForVaccination(entity.id).map { items ->
                    entity.toVaccination().copy(items = items.map { it.toDomain() })
                }
            }
            combine(flows) { it.toList() }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>> = 
        vaccinationDao.getVaccinationsForPatient(patientId).flatMapLatest { list ->
            if (list.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val flows = list.map { entity ->
                vaccinationItemDao.getItemsForVaccination(entity.id).map { items ->
                    entity.toVaccination().copy(items = items.map { it.toDomain() })
                }
            }
            combine(flows) { it.toList() }
        }

    override suspend fun getVaccinationById(id: String): Vaccination? {
        return withContext(Dispatchers.IO) {
            val entity = vaccinationDao.getVaccinationById(id) ?: return@withContext null
            val items = vaccinationItemDao.getItemsForVaccination(id).first()
            entity.toVaccination().copy(items = items.map { it.toDomain() })
        }
    }

    override suspend fun refreshVaccinations() {
        withContext(Dispatchers.IO) {
            try {
                val vaccinations = postgrest.from("vaccinations").select().decodeList<Vaccination>()
                val totalDownloaded = vaccinations.size
                var imported = 0
                var failedValidation = 0
                var skippedMissingPatient = 0

                database.withTransaction {
                    for (remote in vaccinations) {
                        // Basic Validation before Room insert
                        if (remote.id.isBlank() || remote.patientId.isBlank()) {
                            android.util.Log.e("VaccinationRepo", "Validation failed for ${remote.id}: patientId=${remote.patientId}")
                            failedValidation++
                            continue
                        }

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
            
            // 2. Save header
            vaccinationDao.insertVaccination(vaccination.toEntity(isSynced = false))

            // 3. Save items
            vaccinationItemDao.deleteItemsForVaccination(vaccination.id)
            vaccinationItemDao.insertItems(vaccination.items.map { it.toEntity().copy(vaccinationId = vaccination.id) })
            
            // 4. Queue for background sync
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
                remarks = "Vaccines: ${vaccination.items.joinToString(", ") { it.vaccineName }}"
            )
        }
    }

    override suspend fun deleteVaccination(id: String) {
        database.withTransaction {
            val existing = vaccinationDao.getActiveVaccinationById(id) ?: return@withTransaction
            
            // 1. Identify batches used in this vaccination
            val batchIds = existing.batchIds.split(",").filter { it.isNotBlank() }
            val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"

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
            
            try {
                auditLogger.recordLog(
                    module = "PATIENT",
                    entityType = "VACCINATION",
                    entityId = id,
                    action = "DELETED",
                    patientId = existing.patientId,
                    remarks = "Vaccines: ${existing.vaccineNames}"
                )
            } catch (e: Exception) {
                android.util.Log.e("VaccinationRepo", "Audit log failed but deletion proceeded", e)
            }
        }
    }

    override suspend fun markAsDone(id: String) {
        database.withTransaction {
            val current = vaccinationDao.getActiveVaccinationById(id)
            if (current != null) {
                val updated = current.copy(status = com.neochildclinic.domain.model.ReminderStatus.COMPLETED, isSynced = false)
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

