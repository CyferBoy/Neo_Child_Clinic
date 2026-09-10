package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    suspend fun getExpenseById(id: String): Expense?

    suspend fun addExpense(expense: Expense, user: String)
    suspend fun updateExpense(expense: Expense, user: String)

    // Soft delete only (task section 5/20: "do not permanently delete synchronized
    // records"). Marks isDeleted = true and re-syncs the row as an UPDATE, rather than
    // physically removing it the way WasteRepository.deleteWaste does.
    suspend fun deleteExpense(id: String, user: String)

    suspend fun refreshExpenses()
    fun getExpenseCount(): Flow<Int>

    suspend fun getExpensesInDateRange(fromDate: String, toDate: String): List<Expense>

    suspend fun getFilteredExpensesPage(
        category: String? = null,
        paymentMethod: String? = null,
        fromDate: String? = null,
        toDate: String? = null,
        query: String? = null,
        sortBy: String = "DATE_DESC",
        limit: Int = 50,
        offset: Int = 0
    ): List<Expense>
}
