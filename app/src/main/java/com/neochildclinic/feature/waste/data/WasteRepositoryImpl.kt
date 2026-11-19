package com.neochildclinic.feature.waste.data
import com.neochildclinic.core.common.DateUtils
import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.data.local.dao.WasteDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.data.local.entity.toEntity
import com.neochildclinic.domain.repository.WasteRepository

import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.domain.model.WasteRecord
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.SyncRepository
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.neochildclinic.core.sync.cloudRefresh
import com.neochildclinic.core.sync.fetchAllPages
import com.neochildclinic.core.sync.RemotePrune

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
        wasteDao.getAllWaste().map { list -> list.map { it.toDomain() } }

    override suspend fun getWasteById(id: String): WasteRecord? =
        wasteDao.getWasteById(id)?.toDomain()

    override suspend fun recordWaste(record: WasteRecord, user: String) {
        transactionRunner.run {
            val userName = sessionManager.getCurrentUserName()
            wasteDao.insertWaste(record.toEntity(isSynced = false).copy(
                createdBy = userName,
                updatedBy = userName,
                updatedAt = record.updatedAt.ifEmpty { com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp() },
                isSynced = false
            ))

            inventoryRepository.deductStockFromBatch(
                batchId = record.batchId,
                quantity = record.quantity,
                user = userName,
                transactionType = mapReasonToTransactionType(record.reason),
                notes = "Waste Record: ${record.id}"
            )

            syncRepository.enqueue(
                entityName = "WASTE",
                entityId = record.id,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.MEDIUM
            )

            auditLogger.recordLog(
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
            inventoryRepository.addStockToBatch(
                batchId = oldRecord.batchId,
                quantity = oldRecord.quantity,
                user = userName,
                transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT,
                notes = "Reversing waste for update: ${oldRecord.id}"
            )

            inventoryRepository.deductStockFromBatch(
                batchId = newRecord.batchId,
                quantity = newRecord.quantity,
                user = userName,
                transactionType = mapReasonToTransactionType(newRecord.reason),
                notes = "Waste update: ${newRecord.id}"
            )

            wasteDao.insertWaste(newRecord.toEntity(isSynced = false).copy(
                createdBy = oldRecord.createdBy ?: userName,
                updatedBy = userName,
                updatedAt = newRecord.updatedAt.ifEmpty { com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp() },
                isSynced = false
            ))

            auditLogger.recordLog(
                module = "INVENTORY",
                entityType = "WASTE",
                entityId = newRecord.id,
                action = "WASTE_UPDATED",
                remarks = "${newRecord.brandName} x${newRecord.quantity} - ${newRecord.reason}"
            )

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

            inventoryRepository.addStockToBatch(
                batchId = record.batchId,
                quantity = record.quantity,
                user = userName,
                transactionType = InventoryTransactionType.MANUAL_ADJUSTMENT,
                notes = "Restored from deleted waste: ${record.id}"
            )

            val now = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp()
            wasteDao.deleteWaste(id)

            // an UPDATE here leaves it on Supabase and the next refreshWaste re-imports it.
            syncRepository.enqueue(
                entityName = "WASTE",
                entityId = id,
                operation = SyncOperation.DELETE,
                priority = SyncPriority.LOW
            )
        }
    }

    override suspend fun refreshWaste() = cloudRefresh("WasteRepo", rethrow = true) {
        val wasteRecords = fetchAllPages { from, to ->
            postgrest.from("waste_records")
                .select {
                    filter { eq("is_deleted", false) }
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<com.neochildclinic.data.local.entity.WasteEntity>()
        }
        transactionRunner.run {
            val pendingWasteIds = syncQueueDao.unsyncedIdSet("WASTE")
            for (remote in wasteRecords) {
                if (remote.id !in pendingWasteIds) {
                    wasteDao.insertWaste(remote.copy(
                        isSynced = true,
                        updatedAt = remote.updatedAt.ifEmpty { com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp() }
                    ))
                }
            }
            // Remote-deletion propagation (NCC-023 / DA-01): synced waste rows the remote set
            // no longer lists and no queue entry claims are drift (waste rows are leaves).
            RemotePrune.computeBatches(
                localIds = wasteDao.getWasteIds(),
                syncedIds = wasteDao.getSyncedWasteIds().toSet(),
                remoteIds = wasteRecords.mapTo(HashSet()) { it.id },
                pendingIds = pendingWasteIds
            ).flatten().forEach { wasteDao.deleteWaste(it) }
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
