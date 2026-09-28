package com.neochildclinic.data.local.datasource

import com.neochildclinic.data.local.dao.VaccineDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.dao.InventoryDeductionDao
import com.neochildclinic.data.local.entity.VaccineEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryLocalDataSourceImpl @Inject constructor(
    private val vaccineDao: VaccineDao,
    private val syncQueueDao: SyncQueueDao,
    private val inventoryDeductionDao: InventoryDeductionDao
) : InventoryLocalDataSource {

    override fun getAllVaccines(): Flow<List<VaccineEntity>> = vaccineDao.getAllVaccines()

    override fun getAllBatches(): Flow<List<VaccineBatchEntity>> =
        vaccineDao.getAllBatches()

    override fun getBatchesByVaccine(vaccineId: String): Flow<List<VaccineBatchEntity>> =
        vaccineDao.getBatchesByVaccine(vaccineId)

    override suspend fun getVaccineById(vaccineId: String): VaccineEntity? = vaccineDao.getVaccineById(vaccineId)

    override suspend fun getBatchById(batchId: String): VaccineBatchEntity? = vaccineDao.getBatchById(batchId)

    override suspend fun getBatchByVaccineAndNumber(vaccineId: String, batchNumber: String): VaccineBatchEntity? =
        vaccineDao.getBatchByVaccineAndNumber(vaccineId, batchNumber)

    override suspend fun insertVaccine(vaccine: VaccineEntity) = vaccineDao.insertVaccine(vaccine)

    override suspend fun updateVaccine(vaccine: VaccineEntity) = vaccineDao.updateVaccine(vaccine)

    override suspend fun insertBatch(batch: VaccineBatchEntity) = vaccineDao.insertBatch(batch)

    override suspend fun updateBatch(batch: VaccineBatchEntity) = vaccineDao.updateBatch(batch)

    override suspend fun deleteBatch(batchId: String, deletedAt: String, deletedBy: String) =
        vaccineDao.deleteBatch(batchId, deletedAt, deletedBy)

    override suspend fun deleteVaccine(vaccineId: String, deletedAt: String, deletedBy: String) =
        vaccineDao.deleteVaccine(vaccineId, deletedAt, deletedBy)

    override suspend fun getBatchCountForVaccine(vaccineId: String): Int = vaccineDao.getBatchCountForVaccine(vaccineId)

    override suspend fun getVaccinationCountForVaccine(vaccineId: String): Int = vaccineDao.getVaccinationCountForVaccine(vaccineId)

    override suspend fun getWasteCountForVaccine(vaccineId: String): Int = vaccineDao.getWasteCountForVaccine(vaccineId)

    override suspend fun getTransactionCountForVaccine(vaccineId: String): Int = vaccineDao.getTransactionCountForVaccine(vaccineId)

    override suspend fun insertTransaction(transaction: InventoryTransactionEntity) = vaccineDao.insertTransaction(transaction)

    override suspend fun getFilteredTransactionsPage(
        vaccineId: String?,
        batchId: String?,
        types: List<String>,
        typesEmpty: Boolean,
        fromDate: String?,
        toDate: String?,
        limit: Int,
        offset: Int
    ): List<InventoryTransactionEntity> =
        vaccineDao.getFilteredTransactionsPage(vaccineId, batchId, types, typesEmpty, fromDate, toDate, limit, offset)

    override suspend fun getTransactionById(transactionId: String): InventoryTransactionEntity? =
        vaccineDao.getTransactionById(transactionId)

    override suspend fun updatePatientIdInTransactions(duplicateId: String, masterId: String) =
        vaccineDao.updatePatientIdInTransactions(duplicateId, masterId)

    override suspend fun getTotalStockForVaccine(vaccineId: String): Int? = vaccineDao.getTotalStockForVaccine(vaccineId)

    override suspend fun getActiveBatchesByExpiry(vaccineId: String): List<VaccineBatchEntity> =
        vaccineDao.getActiveBatchesByExpiry(vaccineId)

    override suspend fun getCompletedForVaccination(vaccinationId: String): List<InventoryDeductionEntity> =
        inventoryDeductionDao.getCompletedForVaccination(vaccinationId)

    override suspend fun insertDeduction(deduction: InventoryDeductionEntity) = inventoryDeductionDao.insert(deduction)

    override suspend fun deleteForVaccination(vaccinationId: String) = inventoryDeductionDao.deleteForVaccination(vaccinationId)

    override suspend fun getForVaccination(vaccinationId: String): List<InventoryDeductionEntity> =
        inventoryDeductionDao.getForVaccination(vaccinationId)

    override suspend fun isUnsyncedBatch(batchId: String): Boolean = syncQueueDao.isUnsynced("BATCH", batchId)

    override suspend fun isUnsyncedVaccine(vaccineId: String): Boolean = syncQueueDao.isUnsynced("VACCINE", vaccineId)

    override suspend fun isUnsyncedTransaction(transactionId: String): Boolean = syncQueueDao.isUnsynced("INVENTORY_TRANSACTION", transactionId)
}
