package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.VaccineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineDao {
    // Vaccine Definition
    @Query("SELECT * FROM vaccines")
    fun getAllVaccines(): Flow<List<VaccineEntity>>

    @Query("SELECT * FROM vaccines WHERE id = :id")
    suspend fun getVaccineById(id: String): VaccineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccine(vaccine: VaccineEntity)


    @Update
    suspend fun updateVaccine(vaccine: VaccineEntity)

    // Pull/conflict-download path. REPLACE would delete the vaccine and cascade away its
    // batches, items and borrow records. See PullUpsertPreservesChildrenTest.
    @Upsert
    suspend fun upsertVaccine(vaccine: VaccineEntity)

    @Query("DELETE FROM vaccines WHERE id = :id")
    suspend fun deleteVaccine(id: String)

    // Batches
    @Query("SELECT * FROM vaccine_batches")
    fun getAllBatches(): Flow<List<VaccineBatchEntity>>

    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId")
    fun getBatchesByVaccine(vaccineId: String): Flow<List<VaccineBatchEntity>>

    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId")
    suspend fun getBatchesByVaccineSync(vaccineId: String): List<VaccineBatchEntity>

    @Query("SELECT * FROM vaccine_batches WHERE batchId = :batchId LIMIT 1")
    suspend fun getBatchById(batchId: String): VaccineBatchEntity?

    @Query("SELECT * FROM vaccine_batches WHERE vaccineId = :vaccineId AND batchNumber = :batchNumber LIMIT 1")
    suspend fun getBatchByVaccineAndNumber(vaccineId: String, batchNumber: String): VaccineBatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: VaccineBatchEntity)


    @Update
    suspend fun updateBatch(batch: VaccineBatchEntity)

    // Pull/conflict-download path. REPLACE would delete the batch and cascade away its items
    // and borrow records/returns. See PullUpsertPreservesChildrenTest.
    @Upsert
    suspend fun upsertBatch(batch: VaccineBatchEntity)

    @Query("DELETE FROM vaccine_batches WHERE batchId = :batchId")
    suspend fun deleteBatch(batchId: String)

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

    // Reference Checks
    @Query("SELECT COUNT(*) FROM vaccine_batches WHERE vaccineId = :vaccineId")
    suspend fun getBatchCountForVaccine(vaccineId: String): Int

    @Query("SELECT COUNT(*) FROM vaccination_items WHERE vaccineId = :vaccineId")
    suspend fun getVaccinationCountForVaccine(vaccineId: String): Int

    @Query("SELECT COUNT(*) FROM waste_records WHERE vaccineId = :vaccineId")
    suspend fun getWasteCountForVaccine(vaccineId: String): Int

    @Query("SELECT COUNT(*) FROM inventory_transactions WHERE vaccineId = :vaccineId")
    suspend fun getTransactionCountForVaccine(vaccineId: String): Int

    // Per-batch reference checks for the deleteBatch guard. vaccination_items,
    // borrow_records and borrow_returns CASCADE-delete with the batch (patient history
    // and the borrow ledger would silently lose rows); waste_records and
    // inventory_transactions have no FK on batchId but would be orphaned from the batch
    // they describe.
    @Query("SELECT COUNT(*) FROM vaccination_items WHERE batchId = :batchId")
    suspend fun getVaccinationCountForBatch(batchId: String): Int

    @Query("SELECT COUNT(*) FROM waste_records WHERE batchId = :batchId")
    suspend fun getWasteCountForBatch(batchId: String): Int

    @Query("SELECT COUNT(*) FROM borrow_records WHERE batchId = :batchId")
    suspend fun getBorrowCountForBatch(batchId: String): Int

    @Query("SELECT COUNT(*) FROM borrow_returns WHERE batch_id = :batchId")
    suspend fun getBorrowReturnCountForBatch(batchId: String): Int

    @Query("SELECT COUNT(*) FROM inventory_transactions WHERE batchId = :batchId")
    suspend fun getTransactionCountForBatch(batchId: String): Int

    // Remote-deletion propagation (RemotePrune wiring, Phase 2 Batch 1 / NCC-023).
    // Pulled vaccines/batches land with isSynced=false (decoding defaults), so the
    // synced-guard is unusable here — prune relies on the queue guard alone.
    @Query("SELECT id FROM vaccines")
    suspend fun getVaccineIds(): List<String>

    @Query("SELECT batchId FROM vaccine_batches")
    suspend fun getBatchIds(): List<String>

}
