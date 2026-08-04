package com.neochildclinic.data.repository

import androidx.room.withTransaction
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.domain.model.WasteRecord
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.WasteRepository
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WasteRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val postgrest: Postgrest,
    private val inventoryRepository: InventoryRepository,
    private val syncRepository: SyncRepository,
    private val auditLogger: com.neochildclinic.core.logger.AuditLogger
) : WasteRepository {

    private val wasteDao = database.wasteDao()
    private val syncQueueDao = database.syncQueueDao()

    override fun getAllWaste(): Flow<List<WasteRecord>> = 
        wasteDao.getAllWaste().map { list -> list.map { it.toDomain() } }

    override suspend fun getWasteById(id: String): WasteRecord? =
        wasteDao.getWasteById(id)?.toDomain()

    override suspend fun recordWaste(record: WasteRecord, user: String) {
        database.withTransaction {
            // 1. Save Locally
            wasteDao.insertWaste(record.toEntity(isSynced = false))

            // 2. Deduct Inventory from the specific batch
            inventoryRepository.deductStockFromBatch(
                batchId = record.batchId,
                quantity = record.quantity,
                user = user,
                transactionType = mapReasonToTransactionType(record.reason),
                notes = "Waste Record: ${record.id}"
            )

            // 3. Queue Sync
            syncRepository.enqueue(
                entityName = "WASTE",
                entityId = record.id,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.MEDIUM
            )

            auditLogger.log(
                module = "INVENTORY",
                entityType = "WASTE",
                entityId = record.id,
                action = "WASTE_RECORDED",
                remarks = "${record.brandName} x${record.quantity} - ${record.reason}"
            )
        }
    }

    override suspend fun updateWaste(oldRecord: WasteRecord, newRecord: WasteRecord, user: String) {
        database.withTransaction {
            // 1. Restore old stock
            inventoryRepository.addStockToBatch(
                batchId = oldRecord.batchId,
                quantity = oldRecord.quantity,
                user = user,
                transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT,
                notes = "Reversing waste for update: ${oldRecord.id}"
            )

            // 2. Deduct new stock
            inventoryRepository.deductStockFromBatch(
                batchId = newRecord.batchId,
                quantity = newRecord.quantity,
                user = user,
                transactionType = mapReasonToTransactionType(newRecord.reason),
                notes = "Waste update: ${newRecord.id}"
            )

            // 3. Update Waste Record
            wasteDao.insertWaste(newRecord.toEntity(isSynced = false))

            auditLogger.log(
                module = "INVENTORY",
                entityType = "WASTE",
                entityId = newRecord.id,
                action = "WASTE_UPDATED",
                remarks = "${newRecord.brandName} x${newRecord.quantity} - ${newRecord.reason}"
            )

            // 4. Queue Sync
            syncRepository.enqueue(
                entityName = "WASTE",
                entityId = newRecord.id,
                operation = SyncOperation.UPDATE,
                priority = SyncPriority.MEDIUM
            )
        }
    }

    override suspend fun deleteWaste(id: String, user: String) {
        database.withTransaction {
            val record = wasteDao.getWasteById(id)?.toDomain() ?: return@withTransaction

            // 1. Restore stock
            inventoryRepository.addStockToBatch(
                batchId = record.batchId,
                quantity = record.quantity,
                user = user,
                transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT,
                notes = "Restored from deleted waste: ${record.id}"
            )

            // 2. Mark as deleted locally
            wasteDao.deleteWaste(id)

            // 3. Queue Sync
            syncRepository.enqueue(
                entityName = "WASTE",
                entityId = id,
                operation = SyncOperation.DELETE,
                priority = SyncPriority.LOW
            )
        }
    }

    override suspend fun refreshWaste() {
        withContext(Dispatchers.IO) {
            try {
                val wasteRecords = postgrest.from("waste_records").select().decodeList<WasteRecord>()
                database.withTransaction {
                    for (remote in wasteRecords) {
                        if (!syncQueueDao.isUnsynced("WASTE", remote.id)) {
                            wasteDao.insertWaste(remote.toEntity(isSynced = true))
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WasteRepo", "Refresh failed", e)
            }
        }
    }

    override fun getWasteCount(): Flow<Int> = wasteDao.getWasteCount()

    private fun mapReasonToTransactionType(reason: String): InventoryTransactionType {
        return when (reason.lowercase()) {
            "expired" -> InventoryTransactionType.EXPIRED
            "broken vial", "damaged" -> InventoryTransactionType.DAMAGED
            "cold chain failure" -> InventoryTransactionType.COLD_CHAIN_FAILURE
            "contaminated" -> InventoryTransactionType.CONTAMINATED
            "returned" -> InventoryTransactionType.RETURN
            else -> InventoryTransactionType.OTHER
        }
    }
}
