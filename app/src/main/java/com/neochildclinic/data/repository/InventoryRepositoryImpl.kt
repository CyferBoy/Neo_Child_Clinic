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
import com.neochildclinic.data.repository.SyncRepositoryImpl
import com.neochildclinic.features.settings.NotificationSettingsManager
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val syncRepository: SyncRepositoryImpl,
    private val auditLogger: AuditLogger,
    private val settingsManager: NotificationSettingsManager,
    private val sessionManager: com.neochildclinic.core.session.SessionManager
) {

    private val vaccineDao = database.vaccineDao()
    private val syncQueueDao = database.syncQueueDao()

    // Applies a stock deduction to a batch, routing the quantity into the
    // matching used/wasted/borrowed bucket alongside remainingQuantity so those
    // counters always stay consistent with why stock left the batch.
    private fun VaccineBatchEntity.deducted(quantity: Int, transactionType: InventoryTransactionType, userName: String): VaccineBatchEntity {
        val base = copy(
            remainingQuantity = remainingQuantity - quantity,
            updatedBy = userName,
            updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
        )
        return when (transactionType) {
            InventoryTransactionType.VACCINATION -> base.copy(usedQuantity = usedQuantity + quantity)
            InventoryTransactionType.BORROWED -> base.copy(borrowedQuantity = borrowedQuantity + quantity)
            InventoryTransactionType.EXPIRED,
            InventoryTransactionType.DAMAGED,
            InventoryTransactionType.COLD_CHAIN_FAILURE,
            InventoryTransactionType.CONTAMINATED,
            InventoryTransactionType.OTHER -> base.copy(wastedQuantity = wastedQuantity + quantity)
            else -> base
        }
    }

    fun getInventoryItems(
        query: String = "",
        filter: InventoryFilter = InventoryFilter.ALL,
        sort: InventorySort = InventorySort.ALPHABETICAL
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
                
                val hasExpired = batches.any { InventoryUtils.isExpired(it.expiryDate) && it.remainingQuantity > 0 }
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

    fun getVaccineBatches(vaccineId: String): Flow<List<VaccineBatchEntity>> = 
        vaccineDao.getBatchesByVaccine(vaccineId).map { batches ->
            batches.sortedBy { parseDate(it.expiryDate) }
        }

    fun getInventoryTransactions(vaccineId: String): Flow<List<InventoryTransactionEntity>> = 
        vaccineDao.getTransactionsForVaccine(vaccineId)

    suspend fun getBatchById(batchId: String): VaccineBatchEntity? =
        vaccineDao.getBatchById(batchId)

    suspend fun getVaccineById(vaccineId: String): VaccineEntity? {
        return vaccineDao.getVaccineById(vaccineId)
    }

    suspend fun addVaccine(vaccine: VaccineEntity, user: String) {
        database.withTransaction {
            val userName = sessionManager.getCurrentUserName()
            val entity = vaccine.copy(
                createdBy = userName,
                updatedBy = userName
            )
            vaccineDao.insertVaccine(entity)
            syncRepository.enqueue("VACCINE", entity.id, SyncOperation.CREATE, SyncPriority.MEDIUM)
            auditLogger.recordLog(
                module = "VACCINE",
                entityType = "VACCINE",
                entityId = vaccine.id,
                action = "CREATED",
                remarks = "Vaccine Definition: ${vaccine.brandName}"
            )
        }
    }

    suspend fun updateVaccine(vaccine: VaccineEntity, user: String) {
        database.withTransaction {
            val existing = vaccineDao.getVaccineById(vaccine.id)
            val userName = sessionManager.getCurrentUserName()
            val updated = vaccine.copy(
                lastUpdated = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                createdBy = existing?.createdBy ?: vaccine.createdBy ?: userName,
                updatedBy = userName
            )
            vaccineDao.updateVaccine(updated)
            syncRepository.enqueue("VACCINE", vaccine.id, SyncOperation.UPDATE, SyncPriority.MEDIUM)
            auditLogger.recordLog(
                module = "VACCINE",
                entityType = "VACCINE",
                entityId = vaccine.id,
                action = "UPDATED",
                remarks = "Vaccine Definition Updated: ${vaccine.brandName}"
            )
        }
    }

    suspend fun addBatch(
        batch: VaccineBatchEntity,
        user: String,
        transactionGroupId: String? = null
    ) {
        database.withTransaction {
            val vaccine = vaccineDao.getVaccineById(batch.vaccineId) ?: throw IllegalStateException("Vaccine not found")
            
            val userName = sessionManager.getCurrentUserName()
            val entityWithAudit = batch.copy(
                createdBy = userName,
                updatedBy = userName,
                updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
            )
            vaccineDao.insertBatch(entityWithAudit)

            val transaction = InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batch.batchId,
                transactionType = InventoryTransactionType.PURCHASE.name,
                quantity = batch.purchaseQuantity,
                previousQuantity = 0,
                currentQuantity = entityWithAudit.remainingQuantity,
                user = userName,
                notes = "Batch Added: ${batch.batchNumber}",
                timestamp = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                createdBy = userName,
                updatedBy = userName
            )
            vaccineDao.insertTransaction(transaction)

            auditLogger.recordLog(
                module = "INVENTORY",
                entityType = "BATCH",
                entityId = batch.batchId,
                action = "CREATED",
                remarks = "Vaccine: ${vaccine.brandName}, Batch: ${batch.batchNumber}, Qty: ${batch.purchaseQuantity}"
            )
            val groupId = transactionGroupId ?: UUID.randomUUID().toString()
            syncRepository.enqueue("BATCH", batch.batchId, SyncOperation.CREATE, SyncPriority.MEDIUM, groupId)
            syncRepository.enqueue("INVENTORY_TRANSACTION", transaction.transactionId, SyncOperation.CREATE, SyncPriority.MEDIUM, groupId)
        }
    }

    suspend fun addStockBatch(
        entriesByVaccine: Map<String, List<VaccineBatchEntity>>,
        user: String
    ) {
        if (entriesByVaccine.isEmpty()) {
            throw IllegalStateException("Add at least one vaccine with a batch before saving.")
        }

        // A single outer transaction wraps every addBatch()/updateVaccine() call below.
        // Room's withTransaction reuses the existing transaction when called again from
        // within it (addBatch/updateVaccine each open their own), so this whole submission
        // commits or rolls back together - if any vaccine/batch fails validation, nothing
        // from this submission is left partially saved.
        database.withTransaction {
            val transactionGroupId = UUID.randomUUID().toString()

            for ((vaccineId, batches) in entriesByVaccine) {
                val vaccine = vaccineDao.getVaccineById(vaccineId)
                    ?: throw IllegalStateException("Selected vaccine could not be found. Please refresh and try again.")

                if (batches.isEmpty()) {
                    throw IllegalStateException("${vaccine.brandName}: add at least one batch.")
                }

                val seenBatchNumbers = mutableSetOf<String>()

                for (batch in batches) {
                    if (batch.vaccineId != vaccineId) {
                        throw IllegalStateException("${vaccine.brandName}: batch data does not match the selected vaccine.")
                    }

                    val batchNumber = batch.batchNumber.trim()
                    if (batchNumber.isBlank()) {
                        throw IllegalStateException("${vaccine.brandName}: batch number is required.")
                    }
                    if (!seenBatchNumbers.add(batchNumber.lowercase())) {
                        throw IllegalStateException("${vaccine.brandName}: batch number '$batchNumber' was entered more than once in this submission.")
                    }
                    if (batch.expiryDate.isBlank()) {
                        throw IllegalStateException("${vaccine.brandName} ($batchNumber): expiry date is required.")
                    }
                    if (batch.purchaseQuantity <= 0) {
                        throw IllegalStateException("${vaccine.brandName} ($batchNumber): quantity must be greater than zero.")
                    }
                    if (batch.sellingPrice < 0 || batch.purchaseCost < 0) {
                        throw IllegalStateException("${vaccine.brandName} ($batchNumber): MRP and Net Rate cannot be negative.")
                    }

                    // Existing DB constraint check - same guard AddBatchViewModel relies on,
                    // just enforced here too since this path can add many batches at once.
                    val existingBatch = vaccineDao.getBatchByVaccineAndNumber(vaccineId, batchNumber)
                    if (existingBatch != null) {
                        throw IllegalStateException("${vaccine.brandName}: batch '$batchNumber' already exists for this vaccine.")
                    }

                    val normalizedBatch = batch.copy(
                        batchNumber = batchNumber,
                        remainingQuantity = batch.purchaseQuantity
                    )

                    // Reuses the existing single-batch save path so batch insert, the
                    // PURCHASE inventory_transaction, audit log, and sync queue entries
                    // stay identical to a normal Add Batch save.
                    addBatch(normalizedBatch, user, transactionGroupId)

                    // Same "latest batch price becomes the vaccine default" behavior as
                    // AddBatchViewModel.saveBatch - re-read the vaccine since a prior
                    // batch in this same submission may have just updated it.
                    val currentVaccine = vaccineDao.getVaccineById(vaccineId) ?: vaccine
                    if (currentVaccine.mrp != batch.sellingPrice || currentVaccine.netRate != batch.purchaseCost) {
                        updateVaccine(
                            currentVaccine.copy(mrp = batch.sellingPrice, netRate = batch.purchaseCost),
                            user
                        )
                    }
                }
            }
        }
    }

    suspend fun getStockHistoryPage(
        vaccineId: String? = null,
        batchId: String? = null,
        types: List<InventoryTransactionType> = emptyList(),
        fromDateIso: String? = null,
        toDateIso: String? = null,
        limit: Int = 50,
        offset: Int = 0,
        remoteOnly: Boolean = true
    ): List<InventoryTransactionEntity> {
        if (!remoteOnly) {
            return vaccineDao.getFilteredTransactionsPage(
                vaccineId = vaccineId,
                batchId = batchId,
                types = types.map { it.name },
                typesEmpty = types.isEmpty(),
                fromDate = fromDateIso,
                toDate = toDateIso,
                limit = limit,
                offset = offset
            )
        }

        return withContext(Dispatchers.IO) {
            postgrest.from("inventory_transactions").select {
                filter {
                    if (vaccineId != null) eq("vaccine_id", vaccineId)
                    if (batchId != null) eq("batch_id", batchId)
                    if (types.isNotEmpty()) isIn("transaction_type", types.map { it.name })
                    if (fromDateIso != null) gte("timestamp", fromDateIso)
                    if (toDateIso != null) lte("timestamp", toDateIso + "T23:59:59")
                }
                order("timestamp", Order.DESCENDING)
                range(offset.toLong(), (offset + limit - 1).toLong())
            }.decodeList<InventoryTransactionEntity>()
        }
    }

    suspend fun updateBatch(batch: VaccineBatchEntity, user: String, notes: String? = null) {
        database.withTransaction {
            val oldBatch = vaccineDao.getBatchById(batch.batchId) ?: return@withTransaction
            val diff = batch.remainingQuantity - oldBatch.remainingQuantity
            val userName = sessionManager.getCurrentUserName()

            vaccineDao.updateBatch(batch.copy(
                updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                createdBy = oldBatch.createdBy ?: batch.createdBy ?: userName,
                updatedBy = userName
            ))

            if (diff != 0) {
                val transaction = InventoryTransactionEntity(
                    vaccineId = batch.vaccineId,
                    batchId = batch.batchId,
                    transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT.name,
                    quantity = diff,
                    previousQuantity = oldBatch.remainingQuantity,
                    currentQuantity = batch.remainingQuantity,
                    user = userName,
                    notes = notes ?: "Batch Updated: ${batch.batchNumber}",
                    timestamp = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                    createdBy = userName,
                    updatedBy = userName
                )
                vaccineDao.insertTransaction(transaction)
                
                syncRepository.enqueue(
                    entityName = "INVENTORY_TRANSACTION",
                    entityId = transaction.transactionId,
                    operation = SyncOperation.CREATE,
                    priority = SyncPriority.MEDIUM
                )
            }

            auditLogger.recordLog(
                module = "INVENTORY",
                entityType = "BATCH",
                entityId = batch.batchId,
                action = "UPDATED",
                remarks = "Batch: ${batch.batchNumber}, Qty Diff: $diff"
            )

            syncRepository.enqueue(
                entityName = "BATCH",
                entityId = batch.batchId,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM
            )
        }
    }

    suspend fun deleteBatch(batchId: String, user: String) {
        database.withTransaction {
            val batch = vaccineDao.getBatchById(batchId) ?: return@withTransaction

            vaccineDao.deleteBatch(batchId)

            val userName = sessionManager.getCurrentUserName()
            vaccineDao.insertTransaction(InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batch.batchId,
                transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT.name,
                quantity = -batch.remainingQuantity,
                previousQuantity = batch.remainingQuantity,
                currentQuantity = 0,
                user = userName,
                notes = "Batch Deleted: ${batch.batchNumber}",
                createdBy = userName,
                updatedBy = userName
            ))

            auditLogger.recordLog(
                module = "INVENTORY",
                entityType = "BATCH",
                entityId = batchId,
                action = "DELETED",
                remarks = "Batch: ${batch.batchNumber}, Removed Qty: ${batch.remainingQuantity}"
            )
            syncRepository.enqueue("BATCH", batchId, SyncOperation.DELETE, SyncPriority.MEDIUM)
        }
    }

    suspend fun deleteVaccine(vaccineId: String, user: String) {
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
                auditLogger.recordLog(
                    module = "VACCINE",
                    entityType = "VACCINE",
                    entityId = vaccineId,
                    action = "DELETED_PERMANENTLY",
                    remarks = "Vaccine: ${vaccine.brandName}"
                )
            }
        }
    }

    suspend fun deductStock(
        vaccineId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        visitId: String? = null,
        patientId: String? = null
    ) {
        val transactionGroupId = UUID.randomUUID().toString()
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
                val userName = sessionManager.getCurrentUserName()
                
                vaccineDao.updateBatch(batch.deducted(deduct, transactionType, userName))
                val transaction = InventoryTransactionEntity(
                    vaccineId = vaccineId,
                    batchId = batch.batchId,
                    patientId = patientId,
                    visitId = visitId,
                    transactionType = transactionType.name,
                    quantity = -deduct,
                    previousQuantity = batch.remainingQuantity,
                    currentQuantity = batch.remainingQuantity - deduct,
                    user = userName,
                    timestamp = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                    createdBy = userName,
                    updatedBy = userName
                )
                vaccineDao.insertTransaction(transaction)
                
                syncRepository.enqueue(
                    entityName = "INVENTORY_TRANSACTION",
                    entityId = transaction.transactionId,
                    operation = SyncOperation.CREATE,
                    priority = SyncPriority.HIGH,
                    transactionGroupId = transactionGroupId
                )
                syncRepository.enqueue(
                    entityName = "BATCH",
                    entityId = batch.batchId,
                    operation = SyncOperation.UPDATE,
                    priority = SyncPriority.MEDIUM,
                    transactionGroupId = transactionGroupId
                )
                remaining -= deduct
            }

            if (remaining > 0) throw IllegalStateException("Insufficient stock")
            auditLogger.recordLog(
                module = "INVENTORY",
                entityType = "VACCINE",
                entityId = vaccineId,
                action = "STOCK_DEDUCTED",
                patientId = patientId,
                remarks = "Qty: $quantity",
                transactionGroupId = transactionGroupId
            )
        }
    }

    suspend fun deductStockFromBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        visitId: String? = null,
        patientId: String? = null,
        notes: String? = null,
        allowExpired: Boolean = false,
        givenDate: String? = null
    ) {
        database.withTransaction {
            val batch = vaccineDao.getBatchById(batchId) ?: throw IllegalStateException("Batch not found")
            if (transactionType == InventoryTransactionType.VACCINATION && !allowExpired) {
                // Validity is judged against the vaccination's given date, not today - a
                // batch that has since expired by today is still valid for a historical
                // record whose given date fell on or before the batch's expiry.
                val expired = if (givenDate != null) {
                    InventoryUtils.isExpiredAsOf(batch.expiryDate, givenDate)
                } else {
                    InventoryUtils.isExpired(batch.expiryDate)
                }
                if (expired) throw IllegalStateException("Cannot deduct stock from a batch that had already expired on the vaccination date.")
            }
            if (batch.remainingQuantity < quantity) {
                throw IllegalStateException("Insufficient stock in Batch ${batch.batchNumber}. Available: ${batch.remainingQuantity}")
            }

            val userName = sessionManager.getCurrentUserName()
            vaccineDao.updateBatch(batch.deducted(quantity, transactionType, userName))
            
            val transaction = InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batchId,
                patientId = patientId,
                visitId = visitId,
                transactionType = transactionType.name,
                quantity = -quantity,
                previousQuantity = batch.remainingQuantity,
                currentQuantity = batch.remainingQuantity - quantity,
                user = userName,
                notes = notes,
                timestamp = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                createdBy = userName,
                updatedBy = userName
            )
            vaccineDao.insertTransaction(transaction)
            
            syncRepository.enqueue(
                entityName = "INVENTORY_TRANSACTION",
                entityId = transaction.transactionId,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.HIGH
            )
            syncRepository.enqueue(
                entityName = "BATCH",
                entityId = batchId,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM
            )
        }
    }

    suspend fun addStockToBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        notes: String? = null
    ) {
        database.withTransaction {
            val batch = vaccineDao.getBatchById(batchId) ?: throw IllegalStateException("Batch not found")
            val userName = sessionManager.getCurrentUserName()
            
            val updatedBatch = if (transactionType == InventoryTransactionType.MANUAL_ADJUSTMENT) {
                // addStockToBatch with MANUAL_ADJUSTMENT is used to restore stock when a
                // waste record is edited/deleted, so unwind the wasted-quantity bucket too.
                batch.copy(
                    remainingQuantity = batch.remainingQuantity + quantity,
                    wastedQuantity = (batch.wastedQuantity - quantity).coerceAtLeast(0),
                    updatedBy = userName,
                    updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
                )
            } else {
                batch.copy(
                    remainingQuantity = batch.remainingQuantity + quantity,
                    updatedBy = userName,
                    updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
                )
            }
            vaccineDao.updateBatch(updatedBatch)
            
            val transaction = InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batchId,
                transactionType = transactionType.name,
                quantity = quantity,
                previousQuantity = batch.remainingQuantity,
                currentQuantity = batch.remainingQuantity + quantity,
                user = userName,
                notes = notes,
                timestamp = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                createdBy = userName,
                updatedBy = userName
            )
            vaccineDao.insertTransaction(transaction)
            
            syncRepository.enqueue(
                entityName = "INVENTORY_TRANSACTION",
                entityId = transaction.transactionId,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.HIGH
            )
            syncRepository.enqueue(
                entityName = "BATCH",
                entityId = batchId,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM
            )
        }
    }

    // Idempotency note: this always generates a brand-new transactionId and always adds
    // `quantity` to the batch's remainingQuantity - it has no built-in guard against being
    // applied twice for the "same" reversal. That's intentional: the guard lives one level
    // up, in the caller's atomic local transaction (e.g. VaccinationRepositoryImpl.deleteVaccination
    // only calls this once per completed inventory_deductions row, and deletes that row in
    // the same transaction - so a rolled-back attempt leaves nothing reversed and nothing
    // to retry from, and a committed attempt can never be replayed because the row driving
    // it is gone). Callers must not call this more than once for the same physical
    // deduction being undone.
    suspend fun reverseDeduction(
        batchId: String,
        quantity: Int,
        user: String,
        visitId: String? = null,
        patientId: String? = null,
        transactionGroupId: String? = null
    ) {
        database.withTransaction {
            val batch = vaccineDao.getBatchById(batchId) ?: throw IllegalStateException("Batch not found")
            val userName = sessionManager.getCurrentUserName()
            vaccineDao.updateBatch(batch.copy(
                remainingQuantity = batch.remainingQuantity + quantity,
                usedQuantity = (batch.usedQuantity - quantity).coerceAtLeast(0),
                updatedBy = userName,
                updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
            ))

            val transaction = InventoryTransactionEntity(
                vaccineId = batch.vaccineId,
                batchId = batchId,
                patientId = patientId,
                visitId = visitId,
                transactionType = InventoryTransactionType.REVERSAL.name,
                quantity = quantity,
                previousQuantity = batch.remainingQuantity,
                currentQuantity = batch.remainingQuantity + quantity,
                user = userName,
                notes = "Stock reversal from edited vaccination${visitId?.let { " (visit: $it)" } ?: ""}",
                timestamp = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                createdBy = userName,
                updatedBy = userName
            )
            vaccineDao.insertTransaction(transaction)
            val groupId = transactionGroupId ?: UUID.randomUUID().toString()
            syncRepository.enqueue(
                entityName = "INVENTORY_TRANSACTION",
                entityId = transaction.transactionId,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.HIGH,
                transactionGroupId = groupId
            )
            syncRepository.enqueue(
                entityName = "BATCH",
                entityId = batchId,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM,
                transactionGroupId = groupId
            )
        }
    }

    suspend fun returnBorrowedStock(
        originalBatchId: String,
        returnToBatchId: String,
        quantity: Int,
        user: String,
        notes: String? = null,
        transactionGroupId: String? = null
    ) {
        database.withTransaction {
            val targetBatch = vaccineDao.getBatchById(returnToBatchId) ?: throw IllegalStateException("Batch not found")
            val userName = sessionManager.getCurrentUserName()
            val sameBatch = returnToBatchId == originalBatchId

            // Stock physically comes back into the target batch either way.
            // The borrowed-quantity debt only clears if it's coming back into the
            // same batch it left from — a return to a different batch leaves the
            // original batch's borrowedQuantity outstanding.
            val updatedBatch = if (sameBatch) {
                targetBatch.copy(
                    remainingQuantity = targetBatch.remainingQuantity + quantity,
                    borrowedQuantity = (targetBatch.borrowedQuantity - quantity).coerceAtLeast(0),
                    updatedBy = userName,
                    updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
                )
            } else {
                targetBatch.copy(
                    remainingQuantity = targetBatch.remainingQuantity + quantity,
                    updatedBy = userName,
                    updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
                )
            }
            vaccineDao.updateBatch(updatedBatch)

            val transaction = InventoryTransactionEntity(
                vaccineId = targetBatch.vaccineId,
                batchId = returnToBatchId,
                transactionType = InventoryTransactionType.BORROW_RETURN.name,
                quantity = quantity,
                previousQuantity = targetBatch.remainingQuantity,
                currentQuantity = targetBatch.remainingQuantity + quantity,
                user = userName,
                notes = notes ?: if (sameBatch) {
                    "Borrow returned"
                } else {
                    "Borrow returned to different batch (originally borrowed from batch: $originalBatchId)"
                },
                timestamp = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                createdBy = userName,
                updatedBy = userName
            )
            vaccineDao.insertTransaction(transaction)

            val groupId = transactionGroupId ?: UUID.randomUUID().toString()
            syncRepository.enqueue(
                entityName = "INVENTORY_TRANSACTION",
                entityId = transaction.transactionId,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.HIGH,
                transactionGroupId = groupId
            )
            syncRepository.enqueue(
                entityName = "BATCH",
                entityId = returnToBatchId,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM,
                transactionGroupId = groupId
            )
        }
    }

    suspend fun transferPatientTransactions(duplicateId: String, masterId: String) {
        vaccineDao.updatePatientIdInTransactions(duplicateId, masterId)
    }

    suspend fun refreshInventory() = cloudRefresh("InventoryRepo") {
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
    }
}
