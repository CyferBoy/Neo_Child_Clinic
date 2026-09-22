package com.neochildclinic.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.neochildclinic.core.model.BorrowReturnRecord
import com.neochildclinic.core.model.BorrowedVaccine
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.BorrowEntity
import com.neochildclinic.data.local.entity.BorrowReturnEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.NewBatchInfo
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.features.inventory.BorrowedDisplayItem
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BorrowRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val inventoryRepository: InventoryRepository,
    private val syncRepository: SyncRepository,
    private val sessionManager: com.neochildclinic.core.session.SessionManager,
    private val auditLogger: com.neochildclinic.core.logger.AuditLogger
) {

    private val borrowDao = database.borrowDao()
    private val borrowReturnDao = database.borrowReturnDao()
    private val vaccineDao = database.vaccineDao()
    private val syncQueueDao = database.syncQueueDao()

    companion object {
        private const val TAG = "BorrowRepository"
    }

    fun getActiveBorrowedRecords(): Flow<List<BorrowedVaccine>> =
        borrowDao.getActiveBorrows().map { list -> list.map { it.toDomain() } }

    fun getReturnedRecords(): Flow<List<BorrowedVaccine>> =
        borrowDao.getReturnedBorrows().map { list -> list.map { it.toDomain() } }

    fun getReturnRecords(): Flow<List<BorrowReturnRecord>> =
        borrowReturnDao.getAllReturns().map { list -> list.map { it.toDomain() } }

    suspend fun saveBorrowedItem(item: BorrowedVaccine) {
        database.withTransaction {
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

    suspend fun deleteBorrowedItem(id: String) {
        database.withTransaction {
            borrowDao.getRecordById(id)?.let { record ->
                borrowDao.deleteById(id)
                syncRepository.enqueue(
                    entityName = "BORROW",
                    entityId = id,
                    operation = SyncOperation.DELETE,
                    priority = SyncPriority.MEDIUM
                )
                auditLogger.log(
                    module = "INVENTORY",
                    entityType = "BORROW",
                    entityId = id,
                    action = "BORROW_DELETED"
                )
            }
        }
    }

    suspend fun submitReturn(
        item: BorrowedDisplayItem,
        quantity: Int,
        batchId: String,
        notes: String?,
        newBatchInfo: NewBatchInfo? = null
    ) {
        database.withTransaction {
            val user = sessionManager.getCurrentUserName()
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH))
            val transactionGroupId = UUID.randomUUID().toString()

            val effectiveBatchId = if (newBatchInfo != null) {
                val vaccine = vaccineDao.getVaccineById(item.vaccineId)
                val newBatch = VaccineBatchEntity(
                    batchId = UUID.randomUUID().toString(),
                    vaccineId = item.vaccineId,
                    batchNumber = newBatchInfo.batchNumber,
                    manufacturer = vaccine?.companyName ?: "Unknown",
                    purchaseDate = today,
                    expiryDate = newBatchInfo.expiryDate,
                    purchaseQuantity = 0, // It's a return, not a purchase
                    remainingQuantity = 0, // Will be increased by the return transaction
                    supplier = "Returned",
                    purchaseCost = 0.0,
                    sellingPrice = newBatchInfo.sellingPrice,
                    updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp()
                )
                inventoryRepository.addBatch(newBatch, user, transactionGroupId)
                newBatch.batchId
            } else {
                batchId
            }

            // Physically restores stock
            inventoryRepository.returnBorrowedStock(
                originalBatchId = item.record.batchId,
                returnToBatchId = effectiveBatchId,
                quantity = quantity,
                user = user,
                notes = notes,
                transactionGroupId = transactionGroupId
            )

            val returnRecord = BorrowReturnRecord(
                id = UUID.randomUUID().toString(),
                borrowRecordId = item.id,
                batchId = effectiveBatchId,
                quantity = quantity,
                returnedDate = today,
                notes = notes,
                createdAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
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

            if (quantity >= item.remainingQuantity) {
                borrowDao.markReturned(item.id, today)
                syncRepository.enqueue(
                    entityName = "BORROW",
                    entityId = item.id,
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
                remarks = "${item.vaccineName} x${quantity} returned"
            )
        }
    }

    suspend fun refreshBorrows() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Refreshing borrow records from Supabase...")
                val records = postgrest.from("borrow_records").select().decodeList<BorrowEntity>()
                val returns = postgrest.from("borrow_returns").select().decodeList<BorrowReturnEntity>()

                Log.d(TAG, "Fetched ${records.size} borrow records and ${returns.size} returns.")

                database.withTransaction {
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
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
            }
        }
    }
}
