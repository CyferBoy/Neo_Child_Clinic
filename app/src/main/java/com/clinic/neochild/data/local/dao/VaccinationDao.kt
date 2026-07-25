package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.VisitEntity
import com.clinic.neochild.data.local.entity.VaccinationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccinationDao {
    @Query("SELECT * FROM patient_visits WHERE isDeleted = 0")
    fun getAllVaccinations(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM patient_visits WHERE patientId = :patientId AND isDeleted = 0")
    fun getVaccinationsForPatient(patientId: String): Flow<List<VisitEntity>>

    @Query("SELECT * FROM patient_visits WHERE id = :id AND isDeleted = 0")
    suspend fun getActiveVaccinationById(id: String): VisitEntity?

    @Query("SELECT * FROM patient_visits WHERE id = :id")
    suspend fun getVaccinationById(id: String): VisitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccination(vaccination: VisitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccinations(vaccinations: List<VisitEntity>)

    @Query("UPDATE patient_visits SET isDeleted = 1, isSynced = 0 WHERE id = :id")
    suspend fun deleteVaccination(id: String)

    @Query("UPDATE patient_visits SET isDeleted = 1, isSynced = 0 WHERE patientId = :patientId")
    suspend fun deleteVaccinationsForPatient(patientId: String)

    @Query("SELECT * FROM patient_visits WHERE isSynced = 0")
    suspend fun getUnsyncedVaccinations(): List<VisitEntity>

    @Query("UPDATE patient_visits SET patientId = :masterId, isSynced = 0 WHERE patientId = :duplicateId")
    suspend fun updatePatientId(duplicateId: String, masterId: String)

    @Query("UPDATE patient_visits SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT COUNT(*) FROM patient_visits WHERE nextDueDate = :date AND isDone = 0 AND isDeleted = 0")
    suspend fun getDueCount(date: String): Int

    @Query("SELECT COUNT(*) FROM patient_visits WHERE dateGiven = :date AND isDone = 1 AND isDeleted = 0")
    fun getCountByDate(date: String): Flow<Int>

    @Query("SELECT SUM(totalPaid) FROM patient_visits WHERE dateGiven = :date AND isDone = 1 AND isDeleted = 0")
    fun getRevenueByDate(date: String): Flow<Double?>

    @Query("SELECT SUM(cashAmount) FROM patient_visits WHERE dateGiven = :date AND isDone = 1 AND isDeleted = 0")
    fun getCashByDate(date: String): Flow<Double?>

    @Query("SELECT SUM(onlineAmount) FROM patient_visits WHERE dateGiven = :date AND isDone = 1 AND isDeleted = 0")
    fun getOnlineByDate(date: String): Flow<Double?>

    @Query("SELECT COUNT(*) FROM patient_visits WHERE dateGiven LIKE :monthPattern AND isDone = 1 AND isDeleted = 0")
    fun getMonthlyCount(monthPattern: String): Flow<Int>

    @Query("SELECT SUM(totalPaid) FROM patient_visits WHERE dateGiven LIKE :monthPattern AND isDone = 1 AND isDeleted = 0")
    fun getMonthlyRevenue(monthPattern: String): Flow<Double?>

    @Query("SELECT vaccineNames FROM patient_visits WHERE dateGiven LIKE :monthPattern AND isDone = 1 AND isDeleted = 0")
    fun getVaccineNamesForMonth(monthPattern: String): Flow<List<String>>

    @Query("UPDATE patient_visits SET inventoryStatus = :status WHERE id = :id")
    suspend fun updateInventoryStatus(id: String, status: String)

    @Query("SELECT * FROM patient_visits WHERE inventoryStatus IN ('PENDING', 'FAILED', 'PARTIAL') AND isDeleted = 0")
    suspend fun getVaccinationsPendingReconciliation(): List<VisitEntity>

    @Query("SELECT * FROM patient_visits WHERE inventoryStatus != 'COMPLETED' AND isDeleted = 0")
    fun getPendingInventoryVisits(): Flow<List<VisitEntity>>
}
