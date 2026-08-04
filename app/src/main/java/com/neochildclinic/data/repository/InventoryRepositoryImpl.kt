package com.neochildclinic.data.repository

import androidx.room.withTransaction
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.utils.InventoryUtils
import com.neochildclinic.core.utils.PatientUtils.parseDate
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.VaccineEntity
import com.neochildclinic.domain.model.*
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.features.settings.NotificationSettingsManager
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger,
    private val settingsManager: NotificationSettingsManager
) : InventoryRepository {

    private val vaccineDao = database.vaccineDao()
    private val syncQueueDao = database.syncQueueDao()

    override fun getInventoryItems(
        query: String,
        filter: InventoryFilter,
        sort: InventorySort
    ): Flow<List<InventoryItem>> {
        return combine(
            vaccineDao.getAllVaccines(),
            vaccineDao.getAllBatches(),
            settingsManager.settingsFlow
        ) { vaccines, allBatches, settings ->
            val globalThreshold = settings.lowStockThreshold
            vaccines.map { vaccine ->
                val batches = allBatches.filter { it.vaccineId == vaccine.id }
                val totalStock = batches.sumOf { it.remainingQuantity }
                
                val hasExpired = batches.any { InventoryUtils.isExpired(it.expiryDate) }
                val isNearExpiry = batches.any { InventoryUtils.isNearExpiry(it.expiryDate) }
                val isLowStock = totalStock <= globalThreshold
                val isOutOfStock = totalStock <= 0
                val activeBatches = batches.filter { it.remainingQuantity > 0 && !InventoryUtils.isExpired(it.expiryDate) }

                // Fallback pricing from latest batch if not set in definition
                val latestBatch = batches.maxByOrNull { it.purchaseDate }
                val displayMrp = if (vaccine.mrp == 0.0) latestBatch?.sellingPrice ?: 0.0 else vaccine.mrp
                val displayNetRate = if (vaccine.netRate == 0.0) latestBatch?.purchaseCost ?: 0.0 else vaccine.netRate

                InventoryItem(
                    id = vaccine.id,
                    brandName = vaccine.brandName,
                    stock = totalStock,
                    type = vaccine.type,
                    company = vaccine.companyName,
                    mrp = displayMrp,
                    netRate = displayNetRate,
                    batches = batches.sortedBy { parseDate(it.expiryDate) },
                    isLowStock = isLowStock,
                    isNearExpiry = isNearExpiry,
                    hasExpired = hasExpired,
                    hasOutofStock = isOutOfStock,
                    activeBatchesCount = activeBatches.size
                )
            }.filter { item ->
                val matchesQuery = query.isBlank() || 
                    item.brandName.contains(query, ignoreCase = true) || 
                    item.company.contains(query, ignoreCase = true)
                
                val matchesFilter = when (filter) {
                    InventoryFilter.ALL -> true
                    InventoryFilter.LOW_STOCK -> item.isLowStock
                    InventoryFilter.NEAR_EXPIRY -> item.isNearExpiry
                    InventoryFilter.EXPIRED -> item.hasExpired
                    InventoryFilter.OUT_OF_STOCK -> item.hasOutofStock
                    InventoryFilter.HIDDEN -> false
                    InventoryFilter.AVAILABLE -> item.activeBatchesCount > 0
                }
                
                matchesQuery && matchesFilter
            }.sortedWith { a, b ->
                when (sort) {
                    InventorySort.ALPHABETICAL -> a.brandName.lowercase().compareTo(b.brandName.lowercase())
                    InventorySort.HIGHEST_STOCK -> b.stock.compareTo(a.stock)
                    InventorySort.LOWEST_STOCK -> a.stock.compareTo(b.stock)
                    InventorySort.EXPIRY -> (a.batches.firstOrNull()?.expiryDate ?: "9999-12-31").compareTo(b.batches.firstOrNull()?.expiryDate ?: "9999-12-31")
                    InventorySort.MANUFACTURER -> a.company.lowercase().compareTo(b.company.lowercase())
                    InventorySort.NEWEST -> (b.batches.maxOfOrNull { it.purchaseDate } ?: "").compareTo(a.batches.maxOfOrNull { it.purchaseDate } ?: "")
                    InventorySort.OLDEST -> (a.batches.minOfOrNull { it.purchaseDate } ?: "").compareTo(b.batches.minOfOrNull { it.purchaseDate } ?: "")
                }
            }
        }
    }

    override fun getVaccineBatches(vaccineId: String): Flow<List<VaccineBatchEntity>> = 
        vaccineDao.getBatchesByVaccine(vaccineId).map { batches ->
            batches.sortedBy { parseDate(it.expiryDate) }
        }

    override fun getInventoryTransactions(vaccineId: String): Flow<List<InventoryTransactionEntity>> = 
        vaccineDao.getTransactionsForVaccine(vaccineId)

    override suspend fun getBatchById(batchId: String): VaccineBatchEntity? = 
        vaccineDao.getBatchById(batchId)

    override suspend fun addVaccine(vaccine: VaccineEntity, user: String) {
        database.withTransaction {
            vaccineDao.insertVaccine(vaccine)
            syncRepository.enqueue("VACCINE", vaccine.id, SyncOperation.CREATE, SyncPriority.MEDIUM)
            try {
                auditLogger.recordLog(
                    module = "VACCINE",
                    entityType = "VACCINE",
                    entityId = vaccine.id,
                    action = "CREATED",
                    remarks = "Vaccine Definition: ${vaccine.brandName}"
                )
            } catch (e: Exception) {
                android.util.Log.e("InventoryRepo", "Audit log failed: ${e.message}")
            }
        }
    }

    override suspend fun updateVaccine(vaccine: VaccineEntity, user: String) {
        database.withTransaction {
            vaccineDao.updateVaccine(vaccine.copy(lastUpdated = System.currentTimeMillis()))
            syncRepository.enqueue("VACCINE", vaccine.id, SyncOperation.UPDATE, SyncPriority.MEDIUM)
            try {
                auditLogger.recordLog(
                    module = "VACCINE",
                    entityType = "VACCINE",
                    entityId = vaccine.id,
                    action = "UPDATED",
                    remarks = "Vaccine Definition Updated: ${vaccine.brandName}"
                )
            } catch (e: Exception) {
                android.util.Log.e("InventoryRepo", "Audit log failed: ${e.message}")
            }
        }
    }

    override suspend fun addBatch(batch: VaccineBatchEntity, user: String) {
        database.withTransaction {
            val vaccine = vaccineDao.getVaccineById(batch.vaccineId) ?: throw IllegalStateException("Vaccine not found")
            val currentTotal = vaccineDao.getTotalStockForVaccine(batch.vaccineId) ?: 0
            
            vaccineDao.insertBatch(batch)

            vaccineDao.insertTransaction(InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batch.batchId,
                transactionType = InventoryTransactionType.PURCHASE.name,
                quantity = batch.purchaseQuantity,
                previousQuantity = currentTotal,
                currentQuantity = currentTotal + batch.purchaseQuantity,
                user = user,
                notes = "Batch Added: ${batch.batchNumber}"
            ))

            try {
                auditLogger.recordLog(
                    module = "INVENTORY",
                    entityType = "BATCH",
                    entityId = batch.batchId,
                    action = "CREATED",
                    remarks = "Vaccine: ${vaccine.brandName}, Batch: ${batch.batchNumber}, Qty: ${batch.purchaseQuantity}"
                )
            } catch (e: Exception) {
                android.util.Log.e("InventoryRepo", "Audit log failed: ${e.message}")
            }
            syncRepository.enqueue("BATCH", batch.batchId, SyncOperation.CREATE, SyncPriority.MEDIUM)
        }
    }

    override suspend fun updateBatch(batch: VaccineBatchEntity, user: String, notes: String?) {
        database.withTransaction {
            val oldBatch = vaccineDao.getBatchById(batch.batchId) ?: return@withTransaction
            val currentTotal = vaccineDao.getTotalStockForVaccine(batch.vaccineId) ?: 0
            val diff = batch.remainingQuantity - oldBatch.remainingQuantity

            vaccineDao.updateBatch(batch)

            if (diff != 0) {
                vaccineDao.insertTransaction(InventoryTransactionEntity(
                    vaccineId = batch.vaccineId,
                    batchId = batch.batchId,
                    transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT.name,
                    quantity = diff,
                    previousQuantity = currentTotal,
                    currentQuantity = currentTotal + diff,
                    user = user,
                    notes = notes ?: "Batch Updated: ${batch.batchNumber}"
                ))
            }

            try {
                auditLogger.recordLog(
                    module = "INVENTORY",
                    entityType = "BATCH",
                    entityId = batch.batchId,
                    action = "UPDATED",
                    remarks = "Batch: ${batch.batchNumber}, Qty Diff: $diff"
                )
            } catch (e: Exception) {
                android.util.Log.e("InventoryRepo", "Audit log failed: ${e.message}")
            }
            syncRepository.enqueue("BATCH", batch.batchId, SyncOperation.UPDATE, SyncPriority.MEDIUM)
        }
    }

    override suspend fun deleteBatch(batchId: String, user: String) {
        database.withTransaction {
            val batch = vaccineDao.getBatchById(batchId) ?: return@withTransaction
            val currentTotal = vaccineDao.getTotalStockForVaccine(batch.vaccineId) ?: 0

            vaccineDao.deleteBatch(batchId)

            vaccineDao.insertTransaction(InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batch.batchId,
                transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT.name,
                quantity = -batch.remainingQuantity,
                previousQuantity = currentTotal,
                currentQuantity = currentTotal - batch.remainingQuantity,
                user = user,
                notes = "Batch Deleted: ${batch.batchNumber}"
            ))

            try {
                auditLogger.recordLog(
                    module = "INVENTORY",
                    entityType = "BATCH",
                    entityId = batchId,
                    action = "DELETED",
                    remarks = "Batch: ${batch.batchNumber}, Removed Qty: ${batch.remainingQuantity}"
                )
            } catch (e: Exception) {
                android.util.Log.e("InventoryRepo", "Audit log failed: ${e.message}")
            }
            syncRepository.enqueue("BATCH", batchId, SyncOperation.DELETE, SyncPriority.MEDIUM)
        }
    }

    override suspend fun deleteVaccine(vaccineId: String, user: String) {
        database.withTransaction {
            val vaccine = vaccineDao.getVaccineById(vaccineId) ?: return@withTransaction
            
            // 1. Check for ANY batches
            val batchCount = vaccineDao.getBatchCountForVaccine(vaccineId)
            if (batchCount > 0) {
                throw IllegalStateException("This vaccine cannot be deleted because batch records still exist.")
            }
            
            // 2. Check historical references
            val vaccinationCount = vaccineDao.getVaccinationCountForVaccine(vaccineId)
            val wasteCount = vaccineDao.getWasteCountForVaccine(vaccineId)
            val transactionCount = vaccineDao.getTransactionCountForVaccine(vaccineId)
            
            val hasHistory = vaccinationCount > 0 || wasteCount > 0 || transactionCount > 0
            
            if (hasHistory) {
                throw IllegalStateException("This vaccine cannot be deleted because it has historical vaccination or waste records.")
            } else {
                // Permanent Delete
                vaccineDao.deleteVaccine(vaccineId)
                syncRepository.enqueue("VACCINE", vaccineId, SyncOperation.DELETE, SyncPriority.MEDIUM)
                try {
                    auditLogger.recordLog(
                        module = "VACCINE",
                        entityType = "VACCINE",
                        entityId = vaccineId,
                        action = "DELETED_PERMANENTLY",
                        remarks = "Vaccine: ${vaccine.brandName}"
                    )
                } catch (e: Exception) {
                    android.util.Log.e("InventoryRepo", "Audit log failed: ${e.message}")
                }
            }
        }
    }

    override suspend fun deductStock(
        vaccineId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        vaccinationId: String?,
        patientId: String?
    ) {
        database.withTransaction {
            // Ensure stock is available before proceeding
            val totalAvailable = vaccineDao.getTotalStockForVaccine(vaccineId) ?: 0
            if (totalAvailable < quantity) {
                throw IllegalStateException("Insufficient stock for this vaccine. Available: $totalAvailable, Required: $quantity")
            }

            var remaining = quantity
            val batches = vaccineDao.getActiveBatchesByExpiry(vaccineId)
                .filter { !InventoryUtils.isExpired(it.expiryDate) }

            for (batch in batches) {
                if (remaining <= 0) break
                val deduct = minOf(batch.remainingQuantity, remaining)
                val prev = vaccineDao.getTotalStockForVaccine(vaccineId) ?: 0
                
                vaccineDao.updateBatch(batch.copy(remainingQuantity = batch.remainingQuantity - deduct))
                vaccineDao.insertTransaction(InventoryTransactionEntity(
                    vaccineId = vaccineId,
                    batchId = batch.batchId,
                    patientId = patientId,
                    vaccinationId = vaccinationId,
                    transactionType = transactionType.name,
                    quantity = -deduct,
                    previousQuantity = prev,
                    currentQuantity = prev - deduct,
                    user = user
                ))
                
                syncRepository.enqueue("BATCH", batch.batchId, SyncOperation.UPDATE, SyncPriority.HIGH)
                remaining -= deduct
            }

            if (remaining > 0) throw IllegalStateException("Insufficient stock")
            auditLogger.recordLog(
                module = "INVENTORY",
                entityType = "VACCINE",
                entityId = vaccineId,
                action = "STOCK_DEDUCTED",
                patientId = patientId,
                remarks = "Qty: $quantity"
            )
        }
    }

    override suspend fun deductStockFromBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        notes: String?
    ) {
        database.withTransaction {
            val batch = vaccineDao.getBatchById(batchId) ?: throw IllegalStateException("Batch not found")
            if (transactionType == InventoryTransactionType.VACCINATION && InventoryUtils.isExpired(batch.expiryDate)) {
                throw IllegalStateException("Cannot deduct stock from an expired batch.")
            }
            if (batch.remainingQuantity < quantity) {
                throw IllegalStateException("Insufficient stock in Batch ${batch.batchNumber}. Available: ${batch.remainingQuantity}")
            }

            val current = vaccineDao.getTotalStockForVaccine(batch.vaccineId) ?: 0
            vaccineDao.updateBatch(batch.copy(remainingQuantity = batch.remainingQuantity - quantity))
            vaccineDao.insertTransaction(InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batchId,
                transactionType = transactionType.name,
                quantity = -quantity,
                previousQuantity = current,
                currentQuantity = current - quantity,
                user = user,
                notes = notes
            ))
            syncRepository.enqueue("BATCH", batchId, SyncOperation.UPDATE, SyncPriority.HIGH)
        }
    }

    override suspend fun addStockToBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        notes: String?
    ) {
        database.withTransaction {
            val batch = vaccineDao.getBatchById(batchId) ?: throw IllegalStateException("Batch not found")
            val current = vaccineDao.getTotalStockForVaccine(batch.vaccineId) ?: 0
            
            vaccineDao.updateBatch(batch.copy(remainingQuantity = batch.remainingQuantity + quantity))
            vaccineDao.insertTransaction(InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batchId,
                transactionType = transactionType.name,
                quantity = quantity,
                previousQuantity = current,
                currentQuantity = current + quantity,
                user = user,
                notes = notes
            ))
            syncRepository.enqueue("BATCH", batchId, SyncOperation.UPDATE, SyncPriority.HIGH)
        }
    }

    override suspend fun reverseDeduction(batchId: String, quantity: Int, user: String) {
        addStockToBatch(
            batchId = batchId,
            quantity = quantity,
            user = user,
            transactionType = InventoryTransactionType.REVERSAL,
            notes = "Stock reversal from deleted/edited vaccination"
        )
    }

    override suspend fun adjustStock(batchId: String, newQuantity: Int, user: String, reason: String) {
        database.withTransaction {
            val batch = vaccineDao.getBatchById(batchId) ?: return@withTransaction
            val current = vaccineDao.getTotalStockForVaccine(batch.vaccineId) ?: 0
            val diff = newQuantity - batch.remainingQuantity
            
            vaccineDao.updateBatch(batch.copy(remainingQuantity = newQuantity))
            vaccineDao.insertTransaction(InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batchId,
                transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT.name,
                quantity = diff,
                previousQuantity = current,
                currentQuantity = current + diff,
                user = user,
                notes = "Adjustment: $reason"
            ))
            syncRepository.enqueue("BATCH", batchId, SyncOperation.UPDATE, SyncPriority.MEDIUM)
        }
    }

    override suspend fun transferPatientTransactions(duplicateId: String, masterId: String) {
        vaccineDao.updatePatientIdInTransactions(duplicateId, masterId)
    }

    override suspend fun refreshInventory() {
        withContext(Dispatchers.IO) {
            try {
                val vaccines = postgrest.from("vaccines").select().decodeList<VaccineEntity>()
                val batches = postgrest.from("vaccine_batches").select().decodeList<VaccineBatchEntity>()

                database.withTransaction {
                    for (v in vaccines) {
                        if (!syncQueueDao.isUnsynced("VACCINE", v.id)) {
                            vaccineDao.insertVaccine(v)
                        }
                    }
                    for (b in batches) {
                        if (!syncQueueDao.isUnsynced("BATCH", b.batchId)) {
                            vaccineDao.insertBatch(b)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("InventoryRepo", "Refresh failed", e)
            }
        }
    }
}
