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

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesSnapshot(): List<ExpenseEntity>

    @Query(
        """
        SELECT * FROM expenses
        WHERE (:category IS NULL OR category = :category)
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

    @Query("SELECT COUNT(*) FROM expenses")
    fun getExpenseCount(): Flow<Int>
}
