package com.neochildclinic.feature.borrowed.data
import com.neochildclinic.core.common.IST_ZONE_ID
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
import com.neochildclinic.feature.borrowed.domain.selectBorrowBatch
import com.neochildclinic.feature.inventory.domain.InsufficientStockException
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.feature.borrowed.domain.repository.NewBatchInfo
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.cloudRefresh
import com.neochildclinic.core.sync.fetchAllPages
import com.neochildclinic.core.sync.RemotePrune

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

    override fun getActiveBorrowCount(): Flow<Int> = borrowDao.getActiveBorrowCount()

    override fun getReturnedRecords(): Flow<List<BorrowedVaccine>> =
        borrowDao.getReturnedBorrows().map { list -> list.map { it.toDomain() } }

    override fun getReturnRecords(): Flow<List<BorrowReturnRecord>> =
        borrowReturnDao.getAllReturns().map { list -> list.map { it.toDomain() } }

    override suspend fun saveBorrowedItem(item: BorrowedVaccine) {
        transactionRunner.run {
            val user = sessionManager.getCurrentUserName()
            val isNew = item.id.isEmpty()
            val finalItem = if (isNew) item.copy(id = UUID.randomUUID().toString()) else item

            // A borrow record stores ONE batchId and every return credits only that batch, so a
            // borrow must never span batches. Deducting earliest-expiry-first across batches
            // (what InventoryRepository.deductStock does) while recording a single batchId
            // permanently invented doses: returning a 5-dose borrow that took 3 from B1 and 2
            // from B2 put all 5 back on B1 and left B2 short forever.
            val batches = inventoryRepository.getVaccineBatches(finalItem.vaccineId).first()

            if (isNew) {
                val batch = selectBorrowBatch(batches, finalItem.quantity, finalItem.batchId)
                    ?: throw InsufficientStockException(
                        noSingleBatchMessage(finalItem, finalItem.quantity)
                    )
                inventoryRepository.deductStockFromBatch(
                    batchId = batch.batchId,
                    quantity = finalItem.quantity,
                    user = user,
                    transactionType = InventoryTransactionType.BORROWED
                )
                // The recorded batch must be the one stock actually left.
                persistBorrow(finalItem.copy(batchId = batch.batchId), user, isNew = true)
            } else {
                // Editing the quantity moves the difference (deduction above only ever ran
                // for brand-new records): 5 -> 8 owes 3 more, 5 -> 3 gives back what is
                // still outstanding (quantity minus already-returned, capped so an edit
                // below the returned amount can't hand back stock that was never taken).
                val existing = borrowDao.getRecordById(finalItem.id)
                val diff = finalItem.quantity - (existing?.quantity ?: 0)
                var batchId = existing?.batchId ?: finalItem.batchId
                if (diff > 0) {
                    // The extra doses must come from the batch this borrow already records,
                    // otherwise the return would credit the wrong batch again.
                    val recorded = batches.firstOrNull { it.batchId == batchId }
                        ?: throw InsufficientStockException(
                            "Borrowed batch $batchId is no longer in the catalog for ${finalItem.vaccineId}."
                        )
                    if (recorded.remainingQuantity < diff) {
                        throw InsufficientStockException(
                            "Batch ${recorded.batchNumber} has only ${recorded.remainingQuantity} doses left; " +
                                "this borrow needs $diff more. Record a separate borrow for another batch."
                        )
                    }
                    inventoryRepository.deductStockFromBatch(
                        batchId = batchId,
                        quantity = diff,
                        user = user,
                        transactionType = InventoryTransactionType.BORROWED
                    )
                } else if (diff < 0 && existing != null) {
                    val outstanding = existing.quantity - borrowReturnDao.getTotalReturnedQuantity(existing.id)
                    val restore = minOf(-diff, outstanding)
                    if (restore > 0) {
                        inventoryRepository.returnBorrowedStock(
                            originalBatchId = existing.batchId,
                            returnToBatchId = existing.batchId,
                            quantity = restore,
                            user = user,
                            notes = "Borrow quantity reduced from ${existing.quantity} to ${finalItem.quantity}"
                        )
                    }
                }
                persistBorrow(finalItem.copy(batchId = batchId), user, isNew = false)
            }
        }
    }

    private fun noSingleBatchMessage(item: BorrowedVaccine, quantity: Int): String =
        "No single batch has $quantity doses of vaccine ${item.vaccineId} in stock. " +
            "Record separate borrows per batch so each return can be credited correctly."

    private suspend fun persistBorrow(item: BorrowedVaccine, user: String, isNew: Boolean) {
            val entity = item.toEntity(isSynced = false)
            borrowDao.insertRecord(entity)

            syncRepository.enqueue(
                entityName = "BORROW",
                entityId = entity.id,
                operation = if (isNew) SyncOperation.CREATE else SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM
            )

            val vaccineName = inventoryRepository.getVaccineById(item.vaccineId)?.let { "${it.type} (${it.brandName})" } ?: item.vaccineId
            auditLogger.recordLog(
                module = "INVENTORY",
                entityType = "BORROW",
                entityId = entity.id,
                action = if (isNew) "BORROW_CREATED" else "BORROW_UPDATED",
                remarks = "$vaccineName x${item.quantity}"
            )
    }

    override suspend fun deleteBorrowedItem(id: String) {
        val userName = sessionManager.getCurrentUserName()
        val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
        transactionRunner.run {
            borrowDao.getRecordById(id)?.let { record ->
                // Reverse whatever stock this borrow still holds: quantity minus what the
                // borrow_returns ledger already gave back (those returns restored their
                // own stock when they were made). Without this, deleting an active borrow
                // leaves the deducted doses gone from inventory forever.
                val outstanding = record.quantity - borrowReturnDao.getTotalReturnedQuantity(record.id)
                if (outstanding > 0) {
                    inventoryRepository.returnBorrowedStock(
                        originalBatchId = record.batchId,
                        returnToBatchId = record.batchId,
                        quantity = outstanding,
                        user = userName,
                        notes = "Borrow record deleted; outstanding stock restored"
                    )
                }
                borrowDao.deleteById(id)
syncRepository.enqueue(
                      entityName = "BORROW",
                      entityId = id,
                      operation = SyncOperation.DELETE,
                      priority = SyncPriority.MEDIUM
                  )
                auditLogger.recordLog(
                    module = "INVENTORY",
                    entityType = "BORROW",
                    entityId = id,
                    action = "BORROW_DELETED"
                )
            }
        }
    }

    override suspend fun submitReturn(
        borrowRecordId: String,
        originalBatchId: String,
        vaccineId: String,
        vaccineName: String,
        quantity: Int,
        batchId: String,
        notes: String?,
        newBatchInfo: NewBatchInfo?
    ) {
        transactionRunner.run {
            val user = sessionManager.getCurrentUserName()
            val today = LocalDate.now(ZoneId.of(IST_ZONE_ID)).toString()
            val transactionGroupId = UUID.randomUUID().toString()

            // Repository-side cap (the UI-level check in ReturnVaccineDialog can be
            // bypassed by a stale dialog or double-submit): a return may only hand back
            // what the borrow still holds - quantity minus everything already returned.
            // Over-returning would mint stock the borrow never took.
            val borrow = borrowDao.getRecordById(borrowRecordId)
                ?: throw IllegalStateException("Borrow record not found.")
            val outstanding = borrow.quantity - borrowReturnDao.getTotalReturnedQuantity(borrowRecordId)
            if (quantity <= 0) throw IllegalStateException("Returned quantity must be greater than zero.")
            if (quantity > outstanding) {
                throw IllegalStateException("Returned quantity ($quantity) exceeds the outstanding borrowed quantity ($outstanding).")
            }

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

            if (quantity == outstanding) {
                borrowDao.markReturned(borrowRecordId, today)
                syncRepository.enqueue(
                    entityName = "BORROW",
                    entityId = borrowRecordId,
                    operation = SyncOperation.UPDATE,
                    priority = SyncPriority.MEDIUM,
                    transactionGroupId = transactionGroupId
                )
            }

            auditLogger.recordLog(
                module = "INVENTORY",
                entityType = "BORROW_RETURN",
                entityId = entity.id,
                action = "BORROW_RETURNED",
                remarks = "${vaccineName} x${quantity} returned"
            )
        }
    }

    override suspend fun refreshBorrows() = cloudRefresh(TAG, rethrow = true) {
                Log.d(TAG, "Refreshing borrow records from Supabase...")
                val records = fetchAllPages { from, to ->
                    postgrest.from("borrow_records")
                        .select {
                            filter { eq("is_deleted", false) }
                            order("id", Order.ASCENDING)
                            range(from, to)
                        }
                        .decodeList<BorrowEntity>()
                }
                val returns = fetchAllPages { from, to ->
                    postgrest.from("borrow_returns")
                        .select {
                            filter { eq("is_deleted", false) }
                            order("id", Order.ASCENDING)
                            range(from, to)
                        }
                        .decodeList<BorrowReturnEntity>()
                }

                Log.d(TAG, "Fetched ${records.size} borrow records and ${returns.size} returns.")

                transactionRunner.run {
                    val pendingBorrowIds = syncQueueDao.unsyncedIdSet("BORROW")
                    val pendingBorrowReturnIds = syncQueueDao.unsyncedIdSet("BORROW_RETURN")
                    for (r in records) {
                        if (r.id !in pendingBorrowIds) {
                            // Upsert: REPLACE deletes the record and cascades away its returns.
                            borrowDao.upsertRecord(r.copy(isSynced = true))
                        }
                    }
                    for (ret in returns) {
                        if (ret.id !in pendingBorrowReturnIds) {
                            borrowReturnDao.insert(ret.copy(isSynced = true))
                        }
                    }
                    // Remote-deletion propagation (NCC-023 / DA-01): returns first (children),
                    // then records; a record deletion cascades any remaining local returns.
                    RemotePrune.computeBatches(
                        localIds = borrowReturnDao.getBorrowReturnIds(),
                        syncedIds = borrowReturnDao.getSyncedBorrowReturnIds().toSet(),
                        remoteIds = returns.mapTo(HashSet()) { it.id },
                        pendingIds = pendingBorrowReturnIds
                    ).flatten().forEach { borrowReturnDao.deleteReturnById(it) }
                    RemotePrune.computeBatches(
                        localIds = borrowDao.getBorrowIds(),
                        syncedIds = borrowDao.getSyncedBorrowIds().toSet(),
                        remoteIds = records.mapTo(HashSet()) { it.id },
                        pendingIds = pendingBorrowIds
                    ).flatten().forEach { borrowDao.deleteById(it) }
                }
                Log.d(TAG, "Borrow records refresh complete.")
    }
}
