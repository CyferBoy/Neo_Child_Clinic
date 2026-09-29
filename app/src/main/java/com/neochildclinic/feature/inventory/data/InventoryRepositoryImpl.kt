package com.neochildclinic.feature.inventory.data
import com.neochildclinic.core.database.TransactionRunner

import android.content.Context
import com.neochildclinic.core.cache.MemoryCache
import com.neochildclinic.core.cache.QueryCacheKey
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.core.preferences.PreferenceManager
import com.neochildclinic.feature.inventory.domain.InventoryUtils
import com.neochildclinic.core.common.PatientUtils.parseDate
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.domain.model.InventoryDeduction
import com.neochildclinic.domain.model.Vaccine
import com.neochildclinic.domain.model.VaccineBatch
import com.neochildclinic.domain.model.InventoryTransaction
import com.neochildclinic.data.remote.PatientRemoteDataSource
import com.neochildclinic.core.preferences.NotificationSettingsManager
import com.neochildclinic.domain.model.InventoryFilter
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventorySort
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.data.local.dao.VaccineDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.cloudRefresh

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val syncQueueDao: SyncQueueDao,
    private val vaccineDao: VaccineDao,
    private val postgrest: Postgrest,
    private val localDataSource: InventoryLocalDataSource,
    private val remoteDataSource: PatientRemoteDataSource,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger,
    private val settingsManager: NotificationSettingsManager,
    private val sessionManager: com.neochildclinic.core.security.SessionManager,
    @ApplicationContext private val context: Context,
    private val inventoryCache: MemoryCache<String, InventoryItem>,
    private val inventoryListCache: MemoryCache<QueryCacheKey, List<InventoryItem>>
) : InventoryRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun enqueueBatchStockChange(batchId: String, transactionId: String, groupId: String? = null) {
        syncRepository.enqueue("INVENTORY_TRANSACTION", transactionId, SyncOperation.CREATE, SyncPriority.HIGH, groupId)
        syncRepository.enqueue("BATCH", batchId, SyncOperation.UPDATE, SyncPriority.MEDIUM, groupId)
    }

    override fun getInventoryItems(
        query: String,
        filter: InventoryFilter,
        sort: InventorySort
    ): Flow<List<InventoryItem>> {
        return combine(
            localDataSource.getAllVaccines(),
            localDataSource.getAllBatches(),
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
                    batches = batches.sortedBy { parseDate(it.expiryDate) }.map { it.toDomain() },
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
        }.flowOn(Dispatchers.Default)
    }

    override fun getAllVaccines(): Flow<List<Vaccine>> = localDataSource.getAllVaccines().map { it.map { row -> row.toDomain() } }

    override fun getVaccineBatches(vaccineId: String): Flow<List<VaccineBatch>> =
        localDataSource.getBatchesByVaccine(vaccineId).map { it.map { row -> row.toDomain() } }.map { batches ->
            batches.sortedBy { parseDate(it.expiryDate) }
        }

    override suspend fun getInventoryDeductionsForVaccination(vaccinationId: String): List<InventoryDeduction> =
        localDataSource.getForVaccination(vaccinationId).map { it.toDomain() }

    override suspend fun insertInventoryDeduction(entity: InventoryDeduction) =
        localDataSource.insertDeduction(entity.toEntity())

    override suspend fun deleteInventoryDeductionsForVaccination(vaccinationId: String) =
        localDataSource.deleteForVaccination(vaccinationId)

    override suspend fun getBatchById(batchId: String): VaccineBatch? =
        localDataSource.getBatchById(batchId)?.toDomain()

    override suspend fun getVaccineById(vaccineId: String): Vaccine? = localDataSource.getVaccineById(vaccineId)?.toDomain()

    override suspend fun addVaccine(vaccine: Vaccine, user: String) {
        val isUpdate = localDataSource.getVaccineById(vaccine.id) != null
        val userName = sessionManager.getCurrentUserName()
        val entity = vaccine.toEntity().copy(
            createdBy = if (isUpdate) vaccine.createdBy else userName,
            updatedBy = userName
        )
        localDataSource.insertVaccine(entity)
        syncRepository.enqueue("VACCINE", vaccine.id, if (isUpdate) SyncOperation.UPDATE else SyncOperation.CREATE, SyncPriority.MEDIUM)
        auditLogger.recordLog(
            module = "VACCINE",
            entityType = "VACCINE",
            entityId = vaccine.id,
            action = if (isUpdate) "UPDATED" else "CREATED",
            remarks = "Vaccine Definition: ${vaccine.brandName}"
        )
        invalidateVaccinationCache(vaccine.id)
    }

    override suspend fun updateVaccine(vaccine: Vaccine, user: String) {
        val isUpdate = localDataSource.getVaccineById(vaccine.id) != null
        val userName = sessionManager.getCurrentUserName()
        val existing = localDataSource.getVaccineById(vaccine.id)
        val updated = vaccine.toEntity().copy(
            lastUpdated = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
            createdBy = existing?.createdBy ?: vaccine.createdBy ?: userName,
            updatedBy = userName
        )
        localDataSource.updateVaccine(updated)
        syncRepository.enqueue("VACCINE", vaccine.id, SyncOperation.UPDATE, SyncPriority.MEDIUM)
        auditLogger.recordLog(
            module = "VACCINE",
            entityType = "VACCINE",
            entityId = vaccine.id,
            action = "UPDATED",
            remarks = "Vaccine Definition Updated: ${vaccine.brandName}"
        )
        invalidateVaccinationCache(vaccine.id)
    }

    override suspend fun addBatch(
        batch: VaccineBatch,
        user: String,
        transactionGroupId: String?
    ) {
        val vaccine = localDataSource.getVaccineById(batch.vaccineId) ?: throw IllegalStateException("Vaccine not found")
        val userName = sessionManager.getCurrentUserName()
        val entityWithAudit = batch.toEntity().copy(
            createdBy = userName,
            updatedBy = userName,
            updatedAt = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
        )
        localDataSource.insertBatch(entityWithAudit)

        val transaction = InventoryTransactionEntity(
            vaccineId = batch.vaccineId,
            batchId = batch.batchId,
            transactionType = InventoryTransactionType.PURCHASE.name,
            quantity = batch.purchaseQuantity,
            previousQuantity = 0,
            currentQuantity = entityWithAudit.remainingQuantity,
            user = userName,
            notes = "Batch Added: ${batch.batchNumber}",
            timestamp = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
            createdBy = userName,
            updatedBy = userName
        )
        localDataSource.insertTransaction(transaction)

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

    override suspend fun addStockBatch(
        entriesByVaccine: Map<String, List<VaccineBatch>>,
        user: String
    ) {
        if (entriesByVaccine.isEmpty()) {
            throw IllegalStateException("Add at least one vaccine with a batch before saving.")
        }
        for ((vaccineId, batches) in entriesByVaccine) {
            val vaccine = localDataSource.getVaccineById(vaccineId)
                ?: throw IllegalStateException("Selected vaccine could not be found. Please refresh and try again.")
            if (batches.isEmpty()) throw IllegalStateException("${vaccine.brandName}: add at least one batch.")
            val seenBatchNumbers = mutableSetOf<String>()
            for (batch in batches) {
                if (batch.vaccineId != vaccineId) throw IllegalStateException("${vaccine.brandName}: batch data does not match the selected vaccine.")
                val batchNumber = batch.batchNumber.trim()
                if (batchNumber.isBlank()) throw IllegalStateException("${vaccine.brandName}: batch number is required.")
                if (!seenBatchNumbers.add(batchNumber.lowercase())) throw IllegalStateException("${vaccine.brandName}: batch number '$batchNumber' was entered more than once in this submission.")
                if (batch.expiryDate.isBlank()) throw IllegalStateException("${vaccine.brandName} ($batchNumber): expiry date is required.")
                if (batch.purchaseQuantity <= 0) throw IllegalStateException("${vaccine.brandName} ($batchNumber): quantity must be greater than zero.")
                if (batch.sellingPrice < 0 || batch.purchaseCost < 0) throw IllegalStateException("${vaccine.brandName} ($batchNumber): MRP and Net Rate cannot be negative.")
                val existingBatch = localDataSource.getBatchByVaccineAndNumber(vaccineId, batchNumber)
                if (existingBatch != null) throw IllegalStateException("${vaccine.brandName}: batch '$batchNumber' already exists for this vaccine.")
                val normalizedBatch = batch.copy(batchNumber = batchNumber, remainingQuantity = batch.purchaseQuantity)
                addBatch(normalizedBatch, user, null)
                val currentVaccine = localDataSource.getVaccineById(vaccineId) ?: vaccine
                if (currentVaccine.mrp != batch.sellingPrice || currentVaccine.netRate != batch.purchaseCost) {
                    updateVaccine(currentVaccine.toDomain().copy(mrp = batch.sellingPrice, netRate = batch.purchaseCost), user)
                }
            }
        }
    }

    override suspend fun getStockHistoryPage(
        vaccineId: String?, batchId: String?, types: List<InventoryTransactionType>,
        fromDateIso: String?, toDateIso: String?, limit: Int, offset: Int, remoteOnly: Boolean
    ): List<InventoryTransaction> {
        val rows = if (!remoteOnly) {
            localDataSource.getFilteredTransactionsPage(
                vaccineId = vaccineId, batchId = batchId, types = types.map { it.name }, typesEmpty = types.isEmpty(),
                fromDate = fromDateIso, toDate = toDateIso, limit = limit, offset = offset
            )
        } else {
            remoteDataSource.getStockHistoryPage(
                vaccineId = vaccineId, batchId = batchId, types = types.map { it.name }, typesEmpty = types.isEmpty(),
                fromDateIso = fromDateIso, toDateIso = toDateIso, limit = limit, offset = offset
            )
        }
        return rows.map { it.toDomain() }
    }

    override suspend fun updateBatch(batch: VaccineBatch, user: String, notes: String?) {
        val updatedEntity = batch.toEntity().copy(
            updatedAt = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
            createdBy = batch.createdBy,
            updatedBy = user
        )
        localDataSource.updateBatch(updatedEntity)
        val oldBatch = localDataSource.getBatchById(batch.batchId) ?: return@updateBatch
        val diff = batch.remainingQuantity - oldBatch.remainingQuantity
        val userName = sessionManager.getCurrentUserName()
        if (diff != 0) {
            val transaction = InventoryTransactionEntity(
                vaccineId = batch.vaccineId, batchId = batch.batchId,
                transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT.name,
                quantity = diff, previousQuantity = oldBatch.remainingQuantity,
                currentQuantity = batch.remainingQuantity, user = userName,
                notes = notes ?: "Batch Updated: ${batch.batchNumber}",
                timestamp = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
                createdBy = userName, updatedBy = userName
            )
            localDataSource.insertTransaction(transaction)
            syncRepository.enqueue("INVENTORY_TRANSACTION", transaction.transactionId, SyncOperation.CREATE, SyncPriority.MEDIUM)
        }
        auditLogger.recordLog(
            module = "INVENTORY", entityType = "BATCH", entityId = batch.batchId, action = "UPDATED",
            remarks = "Batch: ${batch.batchNumber}, Qty Diff: $diff"
        )
        syncRepository.enqueue("BATCH", batch.batchId, SyncOperation.UPDATE, SyncPriority.MEDIUM)
    }

    override suspend fun deleteBatch(batchId: String, user: String) {
        val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
        localDataSource.deleteBatch(batchId, now, user)
        syncRepository.enqueue("BATCH", batchId, SyncOperation.UPDATE, SyncPriority.MEDIUM)
    }

    override suspend fun deleteVaccine(vaccineId: String, user: String) {
        val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
        val vaccine = localDataSource.getVaccineById(vaccineId) ?: return@deleteVaccine

        // Check for batches
        val batchCount = localDataSource.getBatchCountForVaccine(vaccineId)
        if (batchCount > 0) {
            throw IllegalStateException("This vaccine cannot be deleted because batch records still exist.")
        }
        
        // Check historical references
        val vaccinationCount = localDataSource.getVaccinationCountForVaccine(vaccineId)
        val wasteCount = localDataSource.getWasteCountForVaccine(vaccineId)
        val transactionCount = localDataSource.getTransactionCountForVaccine(vaccineId)
        
        val hasHistory = vaccinationCount > 0 || wasteCount > 0 || transactionCount > 0
        if (hasHistory) {
            throw IllegalStateException("This vaccine cannot be deleted because it has historical records.")
        } else {
            localDataSource.deleteVaccine(vaccineId, now, user)
            syncRepository.enqueue("VACCINE", vaccineId, SyncOperation.UPDATE, SyncPriority.MEDIUM)
            auditLogger.recordLog(
                module = "VACCINE",
                entityType = "VACCINE",
                entityId = vaccineId,
                action = "SOFT_DELETED",
                remarks = "Vaccine: ${vaccine.brandName}"
            )
            invalidateVaccinationCache(vaccineId)
        }
    }

    override suspend fun deductStock(
        vaccineId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        visitId: String?,
        patientId: String?
    ) {
        val transactionGroupId = UUID.randomUUID().toString()
        val totalAvailable = localDataSource.getTotalStockForVaccine(vaccineId) ?: 0
        if (totalAvailable < quantity) {
            throw IllegalStateException("Insufficient stock for this vaccine. Available: $totalAvailable, Required: $quantity")
        }

        var remaining = quantity
        val batches = localDataSource.getActiveBatchesByExpiry(vaccineId)
            .filter { !InventoryUtils.isExpired(it.expiryDate) }

        for (batch in batches) {
            if (remaining <= 0) break
            val deduct = minOf(batch.remainingQuantity, remaining)
            deductStockFromBatch(
                batchId = batch.batchId,
                quantity = deduct,
                user = user,
                transactionType = transactionType,
                visitId = visitId,
                patientId = patientId,
                notes = null,
                allowExpired = false,
                givenDate = null,
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

    override suspend fun deductStockFromBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        visitId: String?,
        patientId: String?,
        notes: String?,
        allowExpired: Boolean,
        givenDate: String?,
        transactionGroupId: String?
    ) {
        val batch = localDataSource.getBatchById(batchId) ?: throw IllegalStateException("Batch not found")
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
        localDataSource.updateBatch(batch.deducted(quantity, transactionType, userName))

        val transaction = buildStockTransaction(
            vaccineId = batch.vaccineId,
            batchId = batchId,
            transactionType = transactionType,
            quantity = -quantity,
            previousQuantity = batch.remainingQuantity,
            currentQuantity = batch.remainingQuantity - quantity,
            user = userName,
            notes = notes,
            patientId = patientId,
            visitId = visitId
        )
        localDataSource.insertTransaction(transaction)

        enqueueBatchStockChange(batchId, transaction.transactionId, transactionGroupId)
    }

    override suspend fun addStockToBatch(
        batchId: String,
        quantity: Int,
        user: String,
        transactionType: InventoryTransactionType,
        notes: String?
    ) {
        val batch = localDataSource.getBatchById(batchId) ?: throw IllegalStateException("Batch not found")
        val userName = sessionManager.getCurrentUserName()
        
        val updatedBatch = if (transactionType == InventoryTransactionType.MANUAL_ADJUSTMENT) {
            // addStockToBatch with MANUAL_ADJUSTMENT is used to restore stock when a
            // waste record is edited/deleted, so unwind the wasted-quantity bucket too.
            batch.copy(
                remainingQuantity = batch.remainingQuantity + quantity,
                wastedQuantity = (batch.wastedQuantity - quantity).coerceAtLeast(0),
                updatedBy = userName,
                updatedAt = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
            )
        } else {
            batch.copy(
                remainingQuantity = batch.remainingQuantity + quantity,
                updatedBy = userName,
                updatedAt = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
            )
        }
        localDataSource.updateBatch(updatedBatch)

        val transaction = buildStockTransaction(
            vaccineId = batch.vaccineId,
            batchId = batchId,
            transactionType = transactionType,
            quantity = quantity,
            previousQuantity = batch.remainingQuantity,
            currentQuantity = batch.remainingQuantity + quantity,
            user = userName,
            notes = notes
        )
        localDataSource.insertTransaction(transaction)

        enqueueBatchStockChange(batchId, transaction.transactionId)
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
    override suspend fun reverseDeduction(
        batchId: String,
        quantity: Int,
        user: String,
        visitId: String?,
        patientId: String?,
        transactionGroupId: String?
    ) {
        val batch = localDataSource.getBatchById(batchId) ?: throw IllegalStateException("Batch not found")
        val userName = sessionManager.getCurrentUserName()
        localDataSource.updateBatch(batch.copy(
            remainingQuantity = batch.remainingQuantity + quantity,
            usedQuantity = (batch.usedQuantity - quantity).coerceAtLeast(0),
            updatedBy = userName,
            updatedAt = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
        ))

        val transaction = buildStockTransaction(
            vaccineId = batch.vaccineId,
            batchId = batchId,
            transactionType = InventoryTransactionType.REVERSAL,
            quantity = quantity,
            previousQuantity = batch.remainingQuantity,
            currentQuantity = batch.remainingQuantity + quantity,
            user = userName,
            notes = "Stock reversal from edited vaccination${visitId?.let { " (visit: $it)" } ?: ""}",
            patientId = patientId,
            visitId = visitId
        )
        localDataSource.insertTransaction(transaction)
        val groupId = transactionGroupId ?: UUID.randomUUID().toString()
        enqueueBatchStockChange(batchId, transaction.transactionId, groupId)
    }

    override suspend fun returnBorrowedStock(
        originalBatchId: String,
        returnToBatchId: String,
        quantity: Int,
        user: String,
        notes: String?,
        transactionGroupId: String?
    ) {
        val targetBatch = localDataSource.getBatchById(returnToBatchId) ?: throw IllegalStateException("Batch not found")
        val userName = sessionManager.getCurrentUserName()
        val sameBatch = returnToBatchId == originalBatchId

        val updatedBatch = if (sameBatch) {
            targetBatch.copy(
                remainingQuantity = targetBatch.remainingQuantity + quantity,
                borrowedQuantity = (targetBatch.borrowedQuantity - quantity).coerceAtLeast(0),
                updatedBy = userName,
                updatedAt = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
            )
        } else {
            targetBatch.copy(
                remainingQuantity = targetBatch.remainingQuantity + quantity,
                updatedBy = userName,
                updatedAt = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
            )
        }
        localDataSource.updateBatch(updatedBatch)

        val transaction = buildStockTransaction(
            vaccineId = targetBatch.vaccineId,
            batchId = returnToBatchId,
            transactionType = InventoryTransactionType.BORROW_RETURN,
            quantity = quantity,
            previousQuantity = targetBatch.remainingQuantity,
            currentQuantity = targetBatch.remainingQuantity + quantity,
            user = userName,
            notes = notes ?: if (sameBatch) {
                "Borrow returned"
            } else {
                "Borrow returned to different batch (originally borrowed from batch: $originalBatchId)"
            }
        )
        localDataSource.insertTransaction(transaction)

        val groupId = transactionGroupId ?: UUID.randomUUID().toString()
        enqueueBatchStockChange(returnToBatchId, transaction.transactionId, groupId)
    }

    override suspend fun transferPatientTransactions(duplicateId: String, masterId: String) {
        localDataSource.updatePatientIdInTransactions(duplicateId, masterId)
    }

    private fun invalidateVaccinationCache(id: String) {
        repositoryScope.launch {
            inventoryCache.invalidate(id)
        }
    }

    private fun invalidateInventoryListCache() {
        repositoryScope.launch {
            inventoryListCache.invalidateAll(
                listOf(
                    QueryCacheKey(entityType = "INVENTORY"),
                    QueryCacheKey(entityType = "INVENTORY", query = "")
                )
            )
        }
    }

    override suspend fun refreshInventory() = cloudRefresh("InventoryRepo") {
        val vaccines = postgrest.from("vaccines").select { filter { eq("is_deleted", false) } }.decodeList<VaccineEntity>()
        val batches = postgrest.from("vaccine_batches").select { filter { eq("is_deleted", false) } }.decodeList<VaccineBatchEntity>()

        transactionRunner.run {
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
