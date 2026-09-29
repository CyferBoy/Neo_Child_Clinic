package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.data.local.entity.PatientVaccinationCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccinationDao {
    @Query("SELECT * FROM patient_visits ORDER BY dateGiven DESC")
    fun getAllVaccinations(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM patient_visits WHERE patientId = :patientId ORDER BY dateGiven DESC")
    fun getVaccinationsForPatient(patientId: String): Flow<List<VisitEntity>>

    // --- Pagination (large-data scalability pass) ---
    // patient_visits grows with every visit (not just every patient), so getAllVaccinations()
    // is the fastest-growing unbounded list in the app. Additive - existing Flow methods
    // above are untouched for backward compatibility.
    @Query("SELECT * FROM patient_visits ORDER BY dateGiven DESC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getAllVaccinationsPage(limit: Int, offset: Int): List<VisitEntity>

    @Query("SELECT * FROM patient_visits WHERE patientId = :patientId ORDER BY dateGiven DESC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun getVaccinationsForPatientPage(patientId: String, limit: Int, offset: Int): List<VisitEntity>

    @Transaction
    @Query("SELECT * FROM patient_visits WHERE patientId = :patientId ORDER BY dateGiven DESC")
    fun getVaccinationCardsForPatient(patientId: String): Flow<List<PatientVaccinationCardEntity>>

    @Query("SELECT * FROM patient_visits WHERE receiptNumber = :receiptNumber LIMIT 1")
    suspend fun getVaccinationByReceiptNumber(receiptNumber: String): VisitEntity?

    // Applied after a CREATE sync so the locally held row picks up the number the
    // patient_visits DB trigger assigned (see 20260824_receipt_numbering.sql). Never
    // called to invent a number locally - only to mirror what the server generated.
    @Query("UPDATE patient_visits SET receiptNumber = :receiptNumber WHERE id = :id")
    suspend fun updateReceiptNumber(id: String, receiptNumber: String)

    @Query("SELECT * FROM patient_visits WHERE id = :id")
    suspend fun getActiveVaccinationById(id: String): VisitEntity?

    @Query("SELECT * FROM patient_visits WHERE id = :id")
    suspend fun getVaccinationById(id: String): VisitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccination(vaccination: VisitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccinations(vaccinations: List<VisitEntity>)


    @Query("DELETE FROM patient_visits WHERE id = :id")
    suspend fun deleteVaccination(id: String)

    @Query("DELETE FROM patient_visits WHERE patientId = :patientId")
    suspend fun deleteVaccinationsForPatient(patientId: String)


    @Query("UPDATE patient_visits SET patientId = :masterId, isSynced = 0 WHERE patientId = :duplicateId")
    suspend fun updatePatientId(duplicateId: String, masterId: String)

    @Query("SELECT COUNT(*) FROM patient_visits WHERE nextDueDate = :date AND (status = 'ACTIVE' OR status = 'RESCHEDULED')")
    suspend fun getDueCount(date: String): Int

    @Query("UPDATE patient_visits SET inventoryStatus = :status WHERE id = :id")
    suspend fun updateInventoryStatus(id: String, status: String)

    @Query("SELECT * FROM patient_visits WHERE inventoryStatus IN ('PENDING', 'FAILED', 'PARTIAL')")
    suspend fun getVaccinationsPendingReconciliation(): List<VisitEntity>

    @Query("SELECT * FROM patient_visits WHERE inventoryStatus != 'COMPLETED'")
    fun getPendingInventoryVisits(): Flow<List<VisitEntity>>
}
