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

    // --- Pagination & date-range filtering (large-data scalability pass) ---
    // Additive. getAllTransactions()/getAllTransactionsSnapshot() above are left exactly
    // as-is: FinanceCalculator (features/statistics) does bespoke per-row parsing (COGS
    // snapshot markers, invalid-date counting, profit-completeness flags) over the full
    // list that these new SQL-level aggregates deliberately do not attempt to replicate,
    // so the displayed statistics/business rules cannot silently change. These are the
    // building blocks for a future date-range-scoped dashboard: narrow to the financial
    // year (or any window) in SQL first, then run the existing per-row logic only over
    // that narrower set instead of the entire transaction history.

    @Query(
        "SELECT * FROM finance_transactions " +
        "ORDER BY COALESCE(transaction_date, substr(timestamp, 1, 10)) DESC, timestamp DESC " +
        "LIMIT :limit OFFSET :offset"
    )
    suspend fun getAllTransactionsPage(limit: Int, offset: Int): List<FinanceEntity>

    @Query(
        "SELECT * FROM finance_transactions WHERE patientId = :patientId " +
        "ORDER BY COALESCE(transaction_date, substr(timestamp, 1, 10)) DESC, timestamp DESC " +
        "LIMIT :limit OFFSET :offset"
    )
    suspend fun getTransactionsForPatientPage(patientId: String, limit: Int, offset: Int): List<FinanceEntity>

    // fromDate/toDate are inclusive "yyyy-MM-dd" bounds, matched against the same
    // COALESCE(transaction_date, timestamp-date) used everywhere else in this DAO, so a
    // financial-year (Apr 1 -> Mar 31) query is just fromDate="2025-04-01", toDate="2026-03-31".
    @Query(
        """
        SELECT * FROM finance_transactions
        WHERE COALESCE(transaction_date, substr(timestamp, 1, 10)) BETWEEN :fromDate AND :toDate
        ORDER BY COALESCE(transaction_date, substr(timestamp, 1, 10)) DESC, timestamp DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getTransactionsInRangePage(fromDate: String, toDate: String, limit: Int, offset: Int): List<FinanceEntity>

    // Row counts and simple sums for a date range, computed by SQLite rather than by
    // summing a fully-materialized Kotlin list. Use for "how many transactions / what's
    // the raw revenue-minus-expense total in this window" - anything needing the finer
    // COGS/validity business rules still goes through FinanceCalculator on a page/range
    // fetched via the methods above.
    @Query(
        "SELECT COUNT(*) FROM finance_transactions " +
        "WHERE COALESCE(transaction_date, substr(timestamp, 1, 10)) BETWEEN :fromDate AND :toDate"
    )
    suspend fun getTransactionCountInRange(fromDate: String, toDate: String): Int

    @Query(
        "SELECT COALESCE(SUM(amount), 0.0) FROM finance_transactions " +
        "WHERE type = :type AND COALESCE(transaction_date, substr(timestamp, 1, 10)) BETWEEN :fromDate AND :toDate"
    )
    suspend fun getAmountSumInRange(type: String, fromDate: String, toDate: String): Double

    @Query("DELETE FROM finance_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)
}
