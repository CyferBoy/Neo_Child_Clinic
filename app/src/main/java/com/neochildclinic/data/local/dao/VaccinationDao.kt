package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.data.local.entity.PatientVaccinationCardEntity
import com.neochildclinic.data.local.entity.VaccinationWithItemsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccinationDao {
    @Query("SELECT * FROM patient_visits ORDER BY dateGiven DESC")
    fun getAllVaccinations(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM patient_visits WHERE patientId = :patientId ORDER BY dateGiven DESC")
    fun getVaccinationsForPatient(patientId: String): Flow<List<VisitEntity>>

    @Transaction
    @Query("SELECT * FROM patient_visits WHERE patientId = :patientId ORDER BY dateGiven DESC")
    fun getVaccinationCardsForPatient(patientId: String): Flow<List<PatientVaccinationCardEntity>>

    // Batch reads: visit + items in two queries total, instead of one item query per visit.
    // Replaces the per-visit combine() in VaccinationRepositoryImpl.
    @Transaction
    @Query("SELECT * FROM patient_visits ORDER BY dateGiven DESC")
    fun getAllVaccinationsWithItems(): Flow<List<VaccinationWithItemsEntity>>

    @Transaction
    @Query("SELECT * FROM patient_visits WHERE patientId = :patientId ORDER BY dateGiven DESC")
    fun getVaccinationsForPatientWithItems(patientId: String): Flow<List<VaccinationWithItemsEntity>>

    // Applied after a CREATE sync so the locally held row picks up the number the
    // patient_visits DB trigger assigned (see 20260824_receipt_numbering.sql). Never
    // called to invent a number locally - only to mirror what the server generated.
    @Query("UPDATE patient_visits SET receiptNumber = :receiptNumber WHERE id = :id")
    suspend fun updateReceiptNumber(id: String, receiptNumber: String)

    @Query("SELECT * FROM patient_visits WHERE id = :id")
    suspend fun getActiveVaccinationById(id: String): VisitEntity?

    @Query("SELECT * FROM patient_visits WHERE id = :id")
    suspend fun getVaccinationById(id: String): VisitEntity?

    @Upsert
    suspend fun insertVaccination(vaccination: VisitEntity)

    // Pull/edit path for a visit row that already exists locally. REPLACE would delete the
    // visit and cascade away its vaccination_items and consultations. See
    // PullUpsertPreservesChildrenTest.
    @Upsert
    suspend fun upsertVaccination(vaccination: VisitEntity)


    @Query("DELETE FROM patient_visits WHERE id = :id")
    suspend fun deleteVaccination(id: String)

    @Query("UPDATE patient_visits SET patientId = :masterId, isSynced = 0 WHERE patientId = :duplicateId")
    suspend fun updatePatientId(duplicateId: String, masterId: String)

    @Query("UPDATE patient_visits SET inventoryStatus = :status WHERE id = :id")
    suspend fun updateInventoryStatus(id: String, status: String)

    // Remote-deletion propagation (RemotePrune wiring, Phase 2 Batch 1 / NCC-023).
    @Query("SELECT id FROM patient_visits")
    suspend fun getVisitIds(): List<String>

    @Query("SELECT id FROM patient_visits WHERE isSynced = 1")
    suspend fun getSyncedVisitIds(): List<String>

}
