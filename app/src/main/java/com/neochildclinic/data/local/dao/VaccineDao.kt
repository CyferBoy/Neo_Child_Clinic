package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.VaccineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineDao {
    // Vaccine Definition
    @Query("SELECT * FROM vaccines WHERE is_deleted = 0")
    fun getAllVaccines(): Flow<List<VaccineEntity>>

    @Query("SELECT * FROM vaccines WHERE id = :id AND is_deleted = 0")
    suspend fun getVaccineById(id: String): VaccineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccine(vaccine: VaccineEntity)


    @Update
    suspend fun updateVaccine(vaccine: VaccineEntity)

    @Query("UPDATE vaccines SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, lastUpdated = :deletedAt, updated_by = :deletedBy WHERE id = :id AND is_deleted = 0")
    suspend fun deleteVaccine(id: String, deletedAt: String, deletedBy: String?)

    @Delete
    suspend fun deleteVaccinePermanently(vaccine: VaccineEntity)

    // Batches
    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND remainingQuantity > 0 AND is_deleted = 0 ORDER BY expiryDate ASC")
    suspend fun getActiveBatchesByExpiry(vaccineId: String): List<VaccineBatchEntity>

    @Query("SELECT * FROM vaccine_batches WHERE is_deleted = 0")
    fun getAllBatches(): Flow<List<VaccineBatchEntity>>

    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND is_deleted = 0")
    fun getBatchesForVaccine(vaccineId: String): Flow<List<VaccineBatchEntity>>

    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND is_deleted = 0")
    fun getBatchesByVaccine(vaccineId: String): Flow<List<VaccineBatchEntity>>

    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND is_deleted = 0")
    suspend fun getBatchesByVaccineSync(vaccineId: String): List<VaccineBatchEntity>

    @Query("SELECT * FROM vaccine_batches WHERE batchId = :batchId AND is_deleted = 0 LIMIT 1")
    suspend fun getBatchById(batchId: String): VaccineBatchEntity?

    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND batchNumber = :batchNumber AND is_deleted = 0 LIMIT 1")
    suspend fun getBatchByVaccineAndNumber(vaccineId: String, batchNumber: String): VaccineBatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: VaccineBatchEntity)


    @Update
    suspend fun updateBatch(batch: VaccineBatchEntity)

    @Query("UPDATE vaccine_batches SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, updatedAt = :deletedAt, updated_by = :deletedBy WHERE batchId = :batchId AND is_deleted = 0")
    suspend fun deleteBatch(batchId: String, deletedAt: String, deletedBy: String?)

    // Transactions
    @Insert
    suspend fun insertTransaction(transaction: InventoryTransactionEntity)

    @Query("SELECT * FROM inventory_transactions WHERE vaccineId = :vaccineId ORDER BY timestamp DESC")
    fun getTransactionsForVaccine(vaccineId: String): Flow<List<InventoryTransactionEntity>>

    @Query("UPDATE inventory_transactions SET patientId = :masterId WHERE patientId = :duplicateId")
    suspend fun updatePatientIdInTransactions(duplicateId: String, masterId: String)

    @Query("SELECT * FROM inventory_transactions WHERE transactionId = :id LIMIT 1")
    suspend fun getTransactionById(id: String): InventoryTransactionEntity?

    // Stock History - filters entirely in SQL (rather than loading every transaction
    // into memory and filtering in Kotlin) and paginates via LIMIT/OFFSET so the
    // history screen never has to hold more than one page of rows at a time.
    // typesEmpty short-circuits the IN(:types) check when "All" transaction types
    // are selected, since Room can't bind an empty list to IN(...) meaningfully.
    @Query(
        """
        SELECT * FROM inventory_transactions
        WHERE (:vaccineId IS NULL OR vaccineId = :vaccineId)
        AND (:batchId IS NULL OR batchId = :batchId)
        AND (:typesEmpty = 1 OR transactionType IN (:types))
        AND (:fromDate IS NULL OR substr(timestamp, 1, 10) >= :fromDate)
        AND (:toDate IS NULL OR substr(timestamp, 1, 10) <= :toDate)
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getFilteredTransactionsPage(
        vaccineId: String?,
        batchId: String?,
        types: List<String>,
        typesEmpty: Boolean,
        fromDate: String?,
        toDate: String?,
        limit: Int,
        offset: Int
    ): List<InventoryTransactionEntity>

    // Stock Summary
    @Query("SELECT SUM(remainingQuantity) FROM vaccine_batches WHERE vaccineId = :vaccineId AND is_deleted = 0")
    suspend fun getTotalStockForVaccine(vaccineId: String): Int?

    // Reference Checks
    @Query("SELECT COUNT(*) FROM vaccine_batches WHERE vaccineId = :vaccineId AND is_deleted = 0")
    suspend fun getBatchCountForVaccine(vaccineId: String): Int

    @Query("SELECT COUNT(*) FROM patient_visits WHERE vaccineIds LIKE '%' || :vaccineId || '%' AND is_deleted = 0")
    suspend fun getVaccinationCountForVaccine(vaccineId: String): Int

    @Query("SELECT COUNT(*) FROM waste_records WHERE vaccineId = :vaccineId AND is_deleted = 0")
    suspend fun getWasteCountForVaccine(vaccineId: String): Int

    @Query("SELECT COUNT(*) FROM inventory_transactions WHERE vaccineId = :vaccineId")
    suspend fun getTransactionCountForVaccine(vaccineId: String): Int

    @Query("SELECT COUNT(*) FROM audit_logs WHERE remarks LIKE '%' || :brandName || '%'")
    suspend fun getAuditCountForVaccine(brandName: String): Int

    @Query("SELECT COUNT(*) FROM audit_logs WHERE remarks LIKE '%' || :brandName || '%' AND `action` NOT IN ('CREATED', 'VACCINE_CREATED')")
    suspend fun getAuditCountExcludingCreation(brandName: String): Int
}
