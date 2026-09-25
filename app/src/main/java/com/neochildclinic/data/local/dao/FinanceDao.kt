package com.neochildclinic.data.local.dao

import androidx.room.*
import com.neochildclinic.data.local.entity.FinanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: FinanceEntity)

    @Query("SELECT * FROM finance_transactions WHERE id = :id AND is_deleted = 0")
    suspend fun getTransactionById(id: String): FinanceEntity?

    @Query("SELECT * FROM finance_transactions WHERE is_deleted = 0 ORDER BY COALESCE(transaction_date, substr(timestamp, 1, 10)) DESC, timestamp DESC")
    suspend fun getAllTransactionsSnapshot(): List<FinanceEntity>

    @Query("SELECT * FROM finance_transactions WHERE is_deleted = 0 ORDER BY COALESCE(transaction_date, substr(timestamp, 1, 10)) DESC, timestamp DESC")
    fun getAllTransactions(): Flow<List<FinanceEntity>>

    @Query("SELECT * FROM finance_transactions WHERE visitId = :visitId AND is_deleted = 0")
    suspend fun getTransactionsByVisitId(visitId: String): List<FinanceEntity>

    // Row counts and simple sums for a date range, computed by SQLite rather than by
    // summing a fully-materialized Kotlin list. Use for "how many transactions / what's
    // the raw revenue-minus-expense total in this window" - anything needing the finer
    // COGS/validity business rules still goes through FinanceCalculator on the full
    // transaction list.
    @Query(
        "SELECT COUNT(*) FROM finance_transactions " +
        "WHERE is_deleted = 0 AND COALESCE(transaction_date, substr(timestamp, 1, 10)) BETWEEN :fromDate AND :toDate"
    )
    suspend fun getTransactionCountInRange(fromDate: String, toDate: String): Int

    @Query(
        "SELECT COALESCE(SUM(amount), 0.0) FROM finance_transactions " +
        "WHERE type = :type AND is_deleted = 0 AND COALESCE(transaction_date, substr(timestamp, 1, 10)) BETWEEN :fromDate AND :toDate"
    )
    suspend fun getAmountSumInRange(type: String, fromDate: String, toDate: String): Double

    @Query("UPDATE finance_transactions SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, isSynced = 0, timestamp = :deletedAt, updated_by = :deletedBy WHERE id = :id AND is_deleted = 0")
    suspend fun deleteTransactionById(id: String, deletedAt: String, deletedBy: String?)
}
