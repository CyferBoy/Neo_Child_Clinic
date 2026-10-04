package com.neochildclinic.feature.waste.data
import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.data.local.dao.WasteDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.feature.waste.domain.repository.WasteRepository

import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.domain.model.WasteRecord
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.cloudRefresh

@Singleton
class WasteRepositoryImpl @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val syncQueueDao: SyncQueueDao,
    private val wasteDao: WasteDao,
    private val postgrest: Postgrest,
    private val inventoryRepository: InventoryRepository,
    private val syncRepository: SyncRepository,
    private val auditLogger: com.neochildclinic.core.logger.AuditLogger,
    private val sessionManager: com.neochildclinic.core.security.SessionManager
) : WasteRepository {

    override fun getAllWaste(): Flow<List<WasteRecord>> =
        wasteDao.getAllWaste()

    override suspend fun getWasteById(id: String): WasteRecord? =
        wasteDao.getWasteById(id)

    override suspend fun recordWaste(record: WasteRecord, user: String) {
        transactionRunner.run {
            val userName = sessionManager.getCurrentUserName()
            // 1. Save Locally
            wasteDao.insertWaste(record.copy(
                createdBy = userName,
                updatedBy = userName,
                updatedAt = record.updatedAt.ifEmpty { com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp() }
            ))

            // 2. Deduct Inventory from the specific batch
            inventoryRepository.deductStockFromBatch(
                batchId = record.batchId,
                quantity = record.quantity,
                user = userName,
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
        transactionRunner.run {
            val userName = sessionManager.getCurrentUserName()
            // 1. Restore old stock
            inventoryRepository.addStockToBatch(
                batchId = oldRecord.batchId,
                quantity = oldRecord.quantity,
                user = userName,
                transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT,
                notes = "Reversing waste for update: ${oldRecord.id}"
            )

            // 2. Deduct new stock
            inventoryRepository.deductStockFromBatch(
                batchId = newRecord.batchId,
                quantity = newRecord.quantity,
                user = userName,
                transactionType = mapReasonToTransactionType(newRecord.reason),
                notes = "Waste update: ${newRecord.id}"
            )

            // 3. Update Waste Record
            wasteDao.insertWaste(newRecord.copy(
                createdBy = oldRecord.createdBy ?: userName,
                updatedBy = userName,
                updatedAt = newRecord.updatedAt.ifEmpty { com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp() }
            ))

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
        transactionRunner.run {
            val record = wasteDao.getWasteById(id) ?: return@run
            val userName = sessionManager.getCurrentUserName()

            // 1. Restore stock
            inventoryRepository.addStockToBatch(
                batchId = record.batchId,
                quantity = record.quantity,
                user = userName,
                transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT,
                notes = "Restored from deleted waste: ${record.id}"
            )

            // 2. Delete the waste record locally
            val now = com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp()
            wasteDao.deleteWaste(id)

            // 3. Queue Sync as a real DELETE so the row is removed server-side too;
            // an UPDATE here leaves it on Supabase and the next refreshWaste re-imports it.
            syncRepository.enqueue(
                entityName = "WASTE",
                entityId = id,
                operation = SyncOperation.DELETE,
                priority = SyncPriority.LOW
            )
        }
    }

    override suspend fun refreshWaste() = cloudRefresh("WasteRepo") {
        val wasteRecords = postgrest.from("waste_records").select { filter { eq("is_deleted", false) } }.decodeList<WasteRecord>()
        transactionRunner.run {
            for (remote in wasteRecords) {
                if (!syncQueueDao.isUnsynced("WASTE", remote.id)) {
                    wasteDao.insertWaste(remote.copy(
                        isSynced = true,
                        updatedAt = remote.updatedAt.ifEmpty { com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp() }
                    ))
                }
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
