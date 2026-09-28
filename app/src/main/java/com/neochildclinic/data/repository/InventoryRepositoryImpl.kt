package com.neochildclinic.data.repository

import android.content.Context
import com.neochildclinic.core.cache.MemoryCache
import com.neochildclinic.core.cache.QueryCacheKey
import com.neochildclinic.core.logger.AuditLogger
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.preferences.PreferenceManager
import com.neochildclinic.core.utils.InventoryUtils
import com.neochildclinic.core.utils.PatientUtils.parseDate
import com.neochildclinic.data.local.datasource.InventoryLocalDataSource
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.data.remote.datasource.PatientRemoteDataSource
import com.neochildclinic.data.settings.NotificationSettingsManager
import com.neochildclinic.domain.model.InventoryFilter
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventorySort
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.SyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val localDataSource: InventoryLocalDataSource,
    private val remoteDataSource: PatientRemoteDataSource,
    private val syncRepository: SyncRepository,
    private val auditLogger: AuditLogger,
    private val settingsManager: NotificationSettingsManager,
    private val sessionManager: com.neochildclinic.core.session.SessionManager,
    @ApplicationContext private val context: Context,
    private val inventoryCache: MemoryCache<String, InventoryItem>,
    private val inventoryListCache: MemoryCache<QueryCacheKey, List<InventoryItem>>
) : InventoryRepository {

    

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

    override fun getAllVaccines(): Flow<List<VaccineEntity>> = localDataSource.getAllVaccines()

    override fun getVaccineBatches(vaccineId: String): Flow<List<VaccineBatchEntity>> =
        localDataSource.getBatchesByVaccine(vaccineId).map { batches ->
            batches.sortedBy { parseDate(it.expiryDate) }
        }

    override suspend fun getInventoryDeductionsForVaccination(vaccinationId: String): List<InventoryDeductionEntity> =
        localDataSource.getForVaccination(vaccinationId)

    override suspend fun insertInventoryDeduction(entity: InventoryDeductionEntity) =
        localDataSource.insertDeduction(entity)

    override suspend fun deleteInventoryDeductionsForVaccination(vaccinationId: String) =
        localDataSource.deleteForVaccination(vaccinationId)

    override suspend fun getBatchById(batchId: String): VaccineBatchEntity? =
        localDataSource.getBatchById(batchId)

    override suspend fun getVaccineById(vaccineId: String): VaccineEntity? {
        return localDataSource.getVaccineById(vaccineId)
    }

    override suspend fun addVaccine(vaccine: VaccineEntity, user: String) {
        val isUpdate = localDataSource.getVaccineById(vaccine.id) != null
        val userName = sessionManager.getCurrentUserName()
        val entity = vaccine.copy(
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

    override suspend fun updateVaccine(vaccine: VaccineEntity, user: String) {
        val isUpdate = localDataSource.getVaccineById(vaccine.id) != null
        val userName = sessionManager.getCurrentUserName()
        val existing = localDataSource.getVaccineById(vaccine.id) ?: vaccine
        val updated = vaccine.copy(
            lastUpdated = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
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
        batch: VaccineBatchEntity,
        user: String,
        transactionGroupId: String?
    ) {
        val vaccine = localDataSource.getVaccineById(batch.vaccineId) ?: throw IllegalStateException("Vaccine not found")
        
        val userName = sessionManager.getCurrentUserName()
        val entityWithAudit = batch.copy(
            createdBy = userName,
            updatedBy = userName,
            updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
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
            timestamp = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
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
        entriesByVaccine: Map<String, List<VaccineBatchEntity>>,
        user: String
    ) {
        if (entriesByVaccine.isEmpty()) {
            throw IllegalStateException("Add at least one vaccine with a batch before saving.")
        }

        for ((vaccineId, batches) in entriesByVaccine) {
            val vaccine = localDataSource.getVaccineById(vaccineId)
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
                val existingBatch = localDataSource.getBatchByVaccineAndNumber(vaccineId, batchNumber)
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
                addBatch(normalizedBatch, user, null)

                // Same "latest batch price becomes the vaccine default" behavior as
                // AddBatchViewModel.saveBatch - re-read the vaccine since a prior
                // batch in this same submission may have just updated it.
                val currentVaccine = localDataSource.getVaccineById(vaccineId) ?: vaccine
                if (currentVaccine.mrp != batch.sellingPrice || currentVaccine.netRate != batch.purchaseCost) {
                    updateVaccine(
                        currentVaccine.copy(mrp = batch.sellingPrice, netRate = batch.purchaseCost),
                        user
                    )
                }
            }
        }
    }

    override suspend fun getStockHistoryPage(
        vaccineId: String?,
        batchId: String?,
        types: List<InventoryTransactionType>,
        fromDateIso: String?,
        toDateIso: String?,
        limit: Int,
        offset: Int,
        remoteOnly: Boolean
    ): List<InventoryTransactionEntity> {
        if (!remoteOnly) {
            return localDataSource.getFilteredTransactionsPage(
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

        return remoteDataSource.getStockHistoryPage(
            vaccineId = vaccineId,
            batchId = batchId,
            types = types.map { it.name },
            typesEmpty = types.isEmpty(),
            fromDateIso = fromDateIso,
            toDateIso = toDateIso,
            limit = limit,
            offset = offset
        )
    }

    override suspend fun updateBatch(batch: VaccineBatchEntity, user: String, notes: String?) {
        localDataSource.updateBatch(batch.copy(
            updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
            createdBy = batch.createdBy,
            updatedBy = user
        ))
        val oldBatch = localDataSource.getBatchById(batch.batchId) ?: return@updateBatch
        val diff = batch.remainingQuantity - oldBatch.remainingQuantity
        val userName = sessionManager.getCurrentUserName()

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
            localDataSource.insertTransaction(transaction)

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

    override suspend fun deleteBatch(batchId: String, user: String) {
        val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
        val batch = localDataSource.getBatchById(batchId) ?: return@deleteBatch

        val transactions = localDataSource.getFilteredTransactionsPage(
            vaccineId = null, batchId = batchId,
            types = emptyList(), typesEmpty = true,
            fromDate = null, toDate = null,
            limit = Int.MAX_VALUE, offset = 0
        )
        for (txn in transactions) {
            localDataSource.deleteTransactionById(txn.id, now, sessionManager.getCurrentUserName())
            syncRepository.enqueue(
                entityName = "INVENTORY_TRANSACTION",
                entityId = txn.id,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM
            )
        }

        localDataSource.deleteBatch(batchId, now, user)
        syncRepository.enqueue("BATCH", batchId, SyncOperation.UPDATE, SyncPriority.MEDIUM)
    }

    override suspend fun deleteVaccine(vaccineId: String, user: String) {
        val now = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
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
            syncRepository.enqueue("VACCINE", vaccineId, SyncOperation.
