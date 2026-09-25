package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.data.local.entity.PatientVaccinationCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccinationDao {
    @Query("SELECT * FROM patient_visits WHERE is_deleted = 0 ORDER BY dateGiven DESC")
    fun getAllVaccinations(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM patient_visits WHERE patientId = :patientId AND is_deleted = 0 ORDER BY dateGiven DESC")
    fun getVaccinationsForPatient(patientId: String): Flow<List<VisitEntity>>

    @Transaction
    @Query("SELECT * FROM patient_visits WHERE patientId = :patientId AND is_deleted = 0 ORDER BY dateGiven DESC")
    fun getVaccinationCardsForPatient(patientId: String): Flow<List<PatientVaccinationCardEntity>>

    @Query("SELECT * FROM patient_visits WHERE receiptNumber = :receiptNumber AND is_deleted = 0 LIMIT 1")
    suspend fun getVaccinationByReceiptNumber(receiptNumber: String): VisitEntity?

    // Applied after a CREATE sync so the locally held row picks up the number the
    // patient_visits DB trigger assigned (see 20260824_receipt_numbering.sql). Never
    // called to invent a number locally - only to mirror what the server generated.
    @Query("UPDATE patient_visits SET receiptNumber = :receiptNumber WHERE id = :id")
    suspend fun updateReceiptNumber(id: String, receiptNumber: String)

    @Query("SELECT * FROM patient_visits WHERE id = :id AND is_deleted = 0")
    suspend fun getActiveVaccinationById(id: String): VisitEntity?

    @Query("SELECT * FROM patient_visits WHERE id = :id")
    suspend fun getVaccinationById(id: String): VisitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccination(vaccination: VisitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccinations(vaccinations: List<VisitEntity>)


    @Query("UPDATE patient_visits SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, isSynced = 0, updatedAt = :deletedAt, updated_by = :deletedBy WHERE id = :id")
    suspend fun deleteVaccination(id: String, deletedAt: String, deletedBy: String?)

    @Query("UPDATE patient_visits SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, isSynced = 0, updatedAt = :deletedAt, updated_by = :deletedBy WHERE patientId = :patientId AND is_deleted = 0")
    suspend fun deleteVaccinationsForPatient(patientId: String, deletedAt: String, deletedBy: String?)


    @Query("UPDATE patient_visits SET patientId = :masterId, isSynced = 0 WHERE patientId = :duplicateId")
    suspend fun updatePatientId(duplicateId: String, masterId: String)

    @Query("SELECT COUNT(*) FROM patient_visits WHERE nextDueDate = :date AND (status = 'ACTIVE' OR status = 'RESCHEDULED') AND is_deleted = 0")
    suspend fun getDueCount(date: String): Int

    @Query("UPDATE patient_visits SET inventoryStatus = :status WHERE id = :id")
    suspend fun updateInventoryStatus(id: String, status: String)

    @Query("SELECT * FROM patient_visits WHERE inventoryStatus IN ('PENDING', 'FAILED', 'PARTIAL') AND is_deleted = 0")
    suspend fun getVaccinationsPendingReconciliation(): List<VisitEntity>

    @Query("SELECT * FROM patient_visits WHERE inventoryStatus != 'COMPLETED' AND is_deleted = 0")
    fun getPendingInventoryVisits(): Flow<List<VisitEntity>>
}
