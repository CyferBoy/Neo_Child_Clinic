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

    @Query("SELECT * FROM expenses WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Query("UPDATE expenses SET is_deleted = 1, deleted_at = :deletedAt, deleted_by = :deletedBy, isSynced = 0, updatedAt = :deletedAt, updated_by = :deletedBy WHERE id = :id AND is_deleted = 0")
    suspend fun deleteExpense(id: String, deletedAt: String, deletedBy: String?)

    @Query("SELECT * FROM expenses WHERE is_deleted = 0 ORDER BY expenseDate DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE is_deleted = 0")
    suspend fun getAllExpensesSnapshot(): List<ExpenseEntity>

    @Query(
        """
        SELECT * FROM expenses
        WHERE is_deleted = 0
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

    @Query("SELECT COUNT(*) FROM expenses WHERE is_deleted = 0")
    fun getExpenseCount(): Flow<Int>
}
