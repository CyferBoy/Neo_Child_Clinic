package com.neochildclinic.feature.borrowed.data
import com.neochildclinic.core.database.TransactionRunner

import android.util.Log
import com.neochildclinic.domain.model.BorrowReturnRecord
import com.neochildclinic.domain.model.BorrowedVaccine
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.data.local.entity.BorrowEntity
import com.neochildclinic.data.local.entity.BorrowReturnEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.data.local.dao.BorrowDao
import com.neochildclinic.data.local.dao.BorrowReturnDao
import com.neochildclinic.data.local.dao.VaccineDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.feature.borrowed.domain.repository.BorrowRepository
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.feature.borrowed.domain.repository.NewBatchInfo
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.cloudRefresh

@Singleton
class BorrowRepositoryImpl @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val syncQueueDao: SyncQueueDao,
    private val vaccineDao: VaccineDao,
    private val borrowReturnDao: BorrowReturnDao,
    private val borrowDao: BorrowDao,
    private val postgrest: Postgrest,
    private val inventoryRepository: InventoryRepository,
    private val syncRepository: SyncRepository,
    private val sessionManager: com.neochildclinic.core.security.SessionManager,
    private val auditLogger: com.neochildclinic.core.logger.AuditLogger
) : BorrowRepository {

    companion object {
        private const val TAG = "BorrowRepositoryImpl"
    }

    override fun getActiveBorrowedRecords(): Flow<List<BorrowedVaccine>> =
        borrowDao.getActiveBorrows().map { list -> list.map { it.toDomain() } }

    override fun getReturnedRecords(): Flow<List<BorrowedVaccine>> =
        borrowDao.getReturnedBorrows().map { list -> list.map { it.toDomain() } }

    override fun getReturnRecords(): Flow<List<BorrowReturnRecord>> =
        borrowReturnDao.getAllReturns().map { list -> list.map { it.toDomain() } }

    override suspend fun saveBorrowedItem(item: BorrowedVaccine) {
        transactionRunner.run {
            val user = sessionManager.getCurrentUserName()
            val isNew = item.id.isEmpty()
            val finalItem = if (isNew) item.copy(id = UUID.randomUUID().toString()) else item
            
            if (isNew) {
                // Deduct from inventory
                inventoryRepository.deductStock(
                    vaccineId = finalItem.vaccineId,
                    quantity = finalItem.quantity,
                    user = user,
                    transactionType = InventoryTransactionType.BORROWED
                )
            }

            val entity = finalItem.toEntity(isSynced = false)
            borrowDao.insertRecord(entity)
            
            syncRepository.enqueue(
                entityName = "BORROW",
                entityId = entity.id,
                operation = if (isNew) SyncOperation.CREATE else SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM
            )

            val vaccineName = inventoryRepository.getVaccineById(finalItem.vaccineId)?.let { "${it.type} (${it.brandName})" } ?: finalItem.vaccineId
            auditLogger.log(
                module = "INVENTORY",
                entityType = "BORROW",
                entityId = entity.id,
                action = if (isNew) "BORROW_CREATED" else "BORROW_UPDATED",
                remarks = "$vaccineName x${finalItem.quantity}"
            )
        }
    }

    override suspend fun deleteBorrowedItem(id: String) {
        val userName = sessionManager.getCurrentUserName()
        val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
        transactionRunner.run {
            borrowDao.getRecordById(id)?.let { record ->
                borrowDao.deleteById(id)
                syncRepository.enqueue(
                    entityName = "BORROW",
                    entityId = id,
                    operation = SyncOperation.UPDATE,
                    priority = SyncPriority.MEDIUM
                )
                auditLogger.log(
                    module = "INVENTORY",
                    entityType = "BORROW",
                    entityId = id,
                    action = "BORROW_SOFT_DELETED"
                )
            }
        }
    }

    override suspend fun submitReturn(
        borrowRecordId: String,
        originalBatchId: String,
        vaccineId: String,
        vaccineName: String,
        remainingQuantity: Int,
        quantity: Int,
        batchId: String,
        notes: String?,
        newBatchInfo: NewBatchInfo?
    ) {
        transactionRunner.run {
            val user = sessionManager.getCurrentUserName()
            val today = LocalDate.now().toString()
            val transactionGroupId = UUID.randomUUID().toString()

            val effectiveBatchId = if (newBatchInfo != null) {
                val vaccine = vaccineDao.getVaccineById(vaccineId)
                val newBatch = VaccineBatchEntity(
                    batchId = UUID.randomUUID().toString(),
                    vaccineId = vaccineId,
                    batchNumber = newBatchInfo.batchNumber,
                    manufacturer = vaccine?.companyName ?: "Unknown",
                    purchaseDate = today,
                    expiryDate = newBatchInfo.expiryDate,
                    purchaseQuantity = 0, // It's a return, not a purchase
                    remainingQuantity = 0, // Will be increased by the return transaction
                    supplier = "Returned",
                    purchaseCost = 0.0,
                    sellingPrice = newBatchInfo.sellingPrice,
                    updatedAt = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
                )
                inventoryRepository.addBatch(newBatch.toDomain(), user, transactionGroupId)
                newBatch.batchId
            } else {
                batchId
            }

            // Physically restores stock
            inventoryRepository.returnBorrowedStock(
                originalBatchId = originalBatchId,
                returnToBatchId = effectiveBatchId,
                quantity = quantity,
                user = user,
                notes = notes,
                transactionGroupId = transactionGroupId
            )

            val returnRecord = BorrowReturnRecord(
                id = UUID.randomUUID().toString(),
                borrowRecordId = borrowRecordId,
                batchId = effectiveBatchId,
                quantity = quantity,
                returnedDate = today,
                notes = notes,
                createdAt = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp(),
                isSynced = false
            )
            val entity = returnRecord.toEntity(isSynced = false)
            borrowReturnDao.insert(entity)

            syncRepository.enqueue(
                entityName = "BORROW_RETURN",
                entityId = entity.id,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.HIGH,
                transactionGroupId = transactionGroupId
            )

            if (quantity >= remainingQuantity) {
                borrowDao.markReturned(borrowRecordId, today)
                syncRepository.enqueue(
                    entityName = "BORROW",
                    entityId = borrowRecordId,
                    operation = SyncOperation.UPDATE,
                    priority = SyncPriority.MEDIUM,
                    transactionGroupId = transactionGroupId
                )
            }

            auditLogger.log(
                module = "INVENTORY",
                entityType = "BORROW_RETURN",
                entityId = entity.id,
                action = "BORROW_RETURNED",
                remarks = "${vaccineName} x${quantity} returned"
            )
        }
    }

    override suspend fun refreshBorrows() = cloudRefresh(TAG) {
                Log.d(TAG, "Refreshing borrow records from Supabase...")
                val records = postgrest.from("borrow_records").select { filter { eq("is_deleted", false) } }.decodeList<BorrowEntity>()
                val returns = postgrest.from("borrow_returns").select { filter { eq("is_deleted", false) } }.decodeList<BorrowReturnEntity>()

                Log.d(TAG, "Fetched ${records.size} borrow records and ${returns.size} returns.")

                transactionRunner.run {
                    for (r in records) {
                        if (!syncQueueDao.isUnsynced("BORROW", r.id)) {
                            borrowDao.insertRecord(r.copy(isSynced = true))
                        }
                    }
                    for (ret in returns) {
                        if (!syncQueueDao.isUnsynced("BORROW_RETURN", ret.id)) {
                            borrowReturnDao.insert(ret.copy(isSynced = true))
                        }
                    }
                }
                Log.d(TAG, "Borrow records refresh complete.")
    }
}