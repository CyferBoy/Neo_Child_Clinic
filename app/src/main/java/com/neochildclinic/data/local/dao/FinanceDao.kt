package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.FinanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: FinanceEntity)

    @Query("SELECT * FROM finance_transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): FinanceEntity?

    @Query("SELECT * FROM finance_transactions ORDER BY COALESCE(transaction_date, substr(timestamp, 1, 10)) DESC, timestamp DESC")
    suspend fun getAllTransactionsSnapshot(): List<FinanceEntity>

    @Query("SELECT * FROM finance_transactions ORDER BY COALESCE(transaction_date, substr(timestamp, 1, 10)) DESC, timestamp DESC")
    fun getAllTransactions(): Flow<List<FinanceEntity>>

    @Query("SELECT * FROM finance_transactions WHERE visitId = :visitId")
    suspend fun getTransactionsByVisitId(visitId: String): List<FinanceEntity>

    // getAllTransactions()/getAllTransactionsSnapshot() above are left exactly as-is,
    // since FinanceCalculator (features/statistics) does bespoke per-row parsing (COGS snapshot
    // markers, invalid-date counting, profit-completeness flags) over the full list.

    @Query("DELETE FROM finance_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    // Patient merge: finance rows keep their visit link; only the patient id moves.
    @Query("SELECT id FROM finance_transactions WHERE patientId = :patientId")
    suspend fun getIdsForPatient(patientId: String): List<String>

    @Query("UPDATE finance_transactions SET patientId = :masterId, isSynced = 0 WHERE patientId = :duplicateId")
    suspend fun reassignToPatient(duplicateId: String, masterId: String)

    // Remote-deletion propagation (RemotePrune wiring, Phase 2 Batch 1 / NCC-023).
    @Query("SELECT id FROM finance_transactions")
    suspend fun getFinanceIds(): List<String>

    @Query("SELECT id FROM finance_transactions WHERE isSynced = 1")
    suspend fun getSyncedFinanceIds(): List<String>

}
