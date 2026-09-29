package com.neochildclinic.feature.vaccination.data

import com.neochildclinic.data.local.entity.VaccinationItemEntity
import com.neochildclinic.data.local.entity.PatientVaccinationCardEntity
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.model.InventoryTransactionType
import kotlinx.coroutines.flow.Flow

interface VaccinationLocalDataSource {
    fun getAllVaccinations(): Flow<List<VisitEntity>>
    fun getVaccinationsForPatient(patientId: String): Flow<List<VisitEntity>>
    fun getVaccinationCardsForPatient(patientId: String): Flow<List<PatientVaccinationCardEntity>>
    suspend fun getVaccinationById(id: String): VisitEntity?
    suspend fun getActiveVaccinationById(id: String): VisitEntity?
    suspend fun insertVaccination(visit: VisitEntity)
    suspend fun updateInventoryStatus(vaccinationId: String, status: String)
    suspend fun deleteVaccination(id: String, deletedAt: String, deletedBy: String)
    suspend fun updateReceiptNumber(id: String, receiptNumber: String)
    suspend fun getItemsForVaccination(vaccinationId: String): Flow<List<VaccinationItemEntity>>
    suspend fun insertItems(items: List<VaccinationItemEntity>)
    suspend fun deleteItemsByIds(ids: List<String>, deletedAt: String, deletedBy: String)
    suspend fun deleteItemsForVaccination(vaccinationId: String, deletedAt: String, deletedBy: String)
    suspend fun getItemById(id: String): VaccinationItemEntity?
    suspend fun updatePatientId(duplicateId: String, masterId: String)
    suspend fun isUnsyncedVaccination(id: String): Boolean
    /**
     * Get completed inventory deductions for a vaccination (used for inventory replenishment
     * on vaccination deletion). Returns a list of (batchId, quantity) pairs.
     */
    suspend fun getCompletedForVaccination(vaccinationId: String): List<Pair<String, Int>>
    /**
     * Get finance transactions associated with a vaccination visit.
     */
    suspend fun getTransactionsByVisitId(visitId: String): List<FinanceEntity>
}