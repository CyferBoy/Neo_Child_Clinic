package com.neochildclinic.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neochildclinic.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY expenseDate DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    // Used by financial-statistics integration (task section 7/8): non-deleted expenses
    // for a given expense_date window, so financial-year/monthly totals reflect
    // expense_date (when the cost happened) rather than createdAt (when it was entered).
    @Query("SELECT * FROM expenses WHERE isDeleted = 0 AND expenseDate BETWEEN :fromDate AND :toDate")
    suspend fun getExpensesInDateRange(fromDate: String, toDate: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0")
    suspend fun getAllExpensesSnapshot(): List<ExpenseEntity>

    // List-screen filtering entirely in SQL (task section 15/5) - never loads the full
    // table into memory. category/paymentMethod are matched against the enum name
    // (see ExpenseEntity.category), null means "no filter for this field".
    @Query(
        """
        SELECT * FROM expenses
        WHERE isDeleted = 0
        AND (:category IS NULL OR category = :category)
        AND (:paymentMethod IS NULL OR paymentMethod = :paymentMethod)
        AND (:fromDate IS NULL OR expenseDate >= :fromDate)
        AND (:toDate IS NULL OR expenseDate <= :toDate)
        AND (:query IS NULL OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR referenceNumber LIKE '%' || :query || '%')
        ORDER BY
            CASE WHEN :sortBy = 'DATE_ASC' THEN expenseDate END ASC,
            CASE WHEN :sortBy = 'AMOUNT_DESC' THEN amountPaise END DESC,
            CASE WHEN :sortBy = 'AMOUNT_ASC' THEN amountPaise END ASC,
            expenseDate DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getFilteredExpensesPage(
        category: String?,
        paymentMethod: String?,
        fromDate: String?,
        toDate: String?,
        query: String?,
        sortBy: String,
        limit: Int,
        offset: Int
    ): List<ExpenseEntity>

    @Query("UPDATE expenses SET isSynced = 1, syncedAt = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: String)

    @Query("SELECT COUNT(*) FROM expenses WHERE isDeleted = 0")
    fun getExpenseCount(): Flow<Int>
}
