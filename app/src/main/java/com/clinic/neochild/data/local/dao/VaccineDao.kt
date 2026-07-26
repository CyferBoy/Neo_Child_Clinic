package com.clinic.neochild.data.local.dao

import androidx.room.*
import com.clinic.neochild.data.local.entity.InventoryTransactionEntity
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.data.local.entity.VaccineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineDao {
    // Vaccine Definition
    @Query("SELECT * FROM vaccines WHERE isDeleted = 0")
    fun getAllVaccines(): Flow<List<VaccineEntity>>

    @Query("SELECT * FROM vaccines WHERE id = :id AND isDeleted = 0")
    suspend fun getVaccineById(id: String): VaccineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccine(vaccine: VaccineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccines(vaccines: List<VaccineEntity>)

    @Update
    suspend fun updateVaccine(vaccine: VaccineEntity)

    @Query("UPDATE vaccines SET isDeleted = 1 WHERE id = :id")
    suspend fun deleteVaccine(id: String)

    @Delete
    suspend fun deleteVaccinePermanently(vaccine: VaccineEntity)

    @Query("SELECT * FROM vaccines WHERE id = :id LIMIT 1")
    suspend fun getVaccineByIdIncludingDeleted(id: String): VaccineEntity?

    @Query("SELECT * FROM vaccines WHERE brandName = :brandName AND type = :type LIMIT 1")
    suspend fun getVaccineByNameAndTypeIncludingDeleted(brandName: String, type: String): VaccineEntity?

    // Batches
    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND isDeleted = 0 AND remainingQuantity > 0 ORDER BY expiryDate ASC")
    suspend fun getActiveBatchesByExpiry(vaccineId: String): List<VaccineBatchEntity>

    @Query("SELECT * FROM vaccine_batches WHERE isDeleted = 0")
    fun getAllBatches(): Flow<List<VaccineBatchEntity>>

    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND isDeleted = 0")
    fun getBatchesForVaccine(vaccineId: String): Flow<List<VaccineBatchEntity>>

    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND isDeleted = 0")
    fun getBatchesByVaccine(vaccineId: String): Flow<List<VaccineBatchEntity>>

    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND isDeleted = 0")
    suspend fun getBatchesByVaccineSync(vaccineId: String): List<VaccineBatchEntity>

    @Query("SELECT * FROM vaccine_batches WHERE batchId = :batchId LIMIT 1")
    suspend fun getBatchById(batchId: String): VaccineBatchEntity?

    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND batchNumber = :batchNumber AND isDeleted = 0 LIMIT 1")
    suspend fun getBatchByVaccineAndNumber(vaccineId: String, batchNumber: String): VaccineBatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: VaccineBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatches(batches: List<VaccineBatchEntity>)

    @Update
    suspend fun updateBatch(batch: VaccineBatchEntity)

    // Transactions
    @Insert
    suspend fun insertTransaction(transaction: InventoryTransactionEntity)

    @Query("SELECT * FROM inventory_transactions WHERE vaccineId = :vaccineId ORDER BY timestamp DESC")
    fun getTransactionsForVaccine(vaccineId: String): Flow<List<InventoryTransactionEntity>>

    @Query("UPDATE inventory_transactions SET patientId = :masterId WHERE patientId = :duplicateId")
    suspend fun updatePatientIdInTransactions(duplicateId: String, masterId: String)

    @Query("SELECT * FROM inventory_transactions WHERE transactionId = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): InventoryTransactionEntity?

    // Stock Summary
    @Query("SELECT SUM(remainingQuantity) FROM vaccine_batches WHERE vaccineId = :vaccineId AND isDeleted = 0")
    suspend fun getTotalStockForVaccine(vaccineId: String): Int?

    // Reference Checks
    @Query("SELECT COUNT(*) FROM vaccine_batches WHERE vaccineId = :vaccineId")
    suspend fun getBatchCountForVaccine(vaccineId: String): Int

    @Query("SELECT COUNT(*) FROM patient_visits WHERE vaccineIds LIKE '%' || :vaccineId || '%' AND isDeleted = 0")
    suspend fun getVaccinationCountForVaccine(vaccineId: String): Int

    @Query("SELECT COUNT(*) FROM waste_records WHERE vaccineId = :vaccineId AND isDeleted = 0")
    suspend fun getWasteCountForVaccine(vaccineId: String): Int

    @Query("SELECT COUNT(*) FROM inventory_transactions WHERE vaccineId = :vaccineId")
    suspend fun getTransactionCountForVaccine(vaccineId: String): Int

    @Query("SELECT COUNT(*) FROM audit_logs WHERE remarks LIKE '%' || :brandName || '%'")
    suspend fun getAuditCountForVaccine(brandName: String): Int

    @Query("SELECT COUNT(*) FROM audit_logs WHERE remarks LIKE '%' || :brandName || '%' AND `action` NOT IN ('CREATED', 'VACCINE_CREATED')")
    suspend fun getAuditCountExcludingCreation(brandName: String): Int
}
