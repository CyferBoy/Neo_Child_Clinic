package com.neochildclinic.data.local.datasource

import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.dao.VaccinationItemDao
import com.neochildclinic.data.local.dao.InventoryDeductionDao
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.entity.VaccinationItemEntity
import com.neochildclinic.data.local.entity.PatientVaccinationCardEntity
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.data.local.entity.FinanceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccinationLocalDataSourceImpl @Inject constructor(
    private val vaccinationDao: VaccinationDao,
    private val vaccinationItemDao: VaccinationItemDao,
    private val inventoryDeductionDao: InventoryDeductionDao,
    private val syncQueueDao: SyncQueueDao
) : VaccinationLocalDataSource {

    override fun getAllVaccinations(): Flow<List<VisitEntity>> = vaccinationDao.getAllVaccinations()

    override fun getVaccinationsForPatient(patientId: String): Flow<List<VisitEntity>> =
        vaccinationDao.getVaccinationsForPatient(patientId)

    override fun getVaccinationCardsForPatient(patientId: String): Flow<List<PatientVaccinationCardEntity>> =
        vaccinationDao.getVaccinationCardsForPatient(patientId)

    override suspend fun getVaccinationById(id: String): VisitEntity? = vaccinationDao.getVaccinationById(id)

    override suspend fun getActiveVaccinationById(id: String): VisitEntity? = vaccinationDao.getActiveVaccinationById(id)

    override suspend fun insertVaccination(visit: VisitEntity) = vaccinationDao.insertVaccination(visit)

    override suspend fun updateInventoryStatus(vaccinationId: String, status: String) =
        vaccinationDao.updateInventoryStatus(vaccinationId, status)

    override suspend fun deleteVaccination(id: String, deletedAt: String, deletedBy: String) =
        vaccinationDao.deleteVaccination(id, deletedAt, deletedBy)

    override suspend fun updateReceiptNumber(id: String, receiptNumber: String) =
        vaccinationDao.updateReceiptNumber(id, receiptNumber)

    override suspend fun getItemsForVaccination(vaccinationId: String): Flow<List<VaccinationItemEntity>> =
        vaccinationItemDao.getItemsForVaccination(vaccinationId)

    override suspend fun insertItems(items: List<VaccinationItemEntity>) = vaccinationItemDao.insertItems(items)

    override suspend fun deleteItemsByIds(ids: List<String>, deletedAt: String, deletedBy: String) =
        vaccinationItemDao.deleteItemsByIds(ids, deletedAt, deletedBy)

    override suspend fun deleteItemsForVaccination(vaccinationId: String, deletedAt: String, deletedBy: String) =
        vaccinationItemDao.deleteItemsForVaccination(vaccinationId, deletedAt, deletedBy)

    override suspend fun getItemById(id: String): VaccinationItemEntity? = vaccinationItemDao.getItemById(id)

    override suspend fun updatePatientId(duplicateId: String, masterId: String) =
        vaccinationDao.updatePatientId(duplicateId, masterId)

    override suspend fun isUnsyncedVaccination(id: String): Boolean = syncQueueDao.isUnsynced("VACCINATION", id)

    override suspend fun getCompletedForVaccination(vaccinationId: String): List<Pair<String, Int>> {
        val deductions = inventoryDeductionDao.getCompletedForVaccination(vaccinationId)
        return deductions.filter { it.batchId != null }.map { it.batchId!! to it.quantity }
    }

    override suspend fun getTransactionsByVisitId(visitId: String): List<FinanceEntity> {
        // Delegating - full finance integration requires FinanceLocalDataSource
        // This placeholder returns empty list; the repository handles the gap
        return emptyList()
    }
}
