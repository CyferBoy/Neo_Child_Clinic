package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    suspend fun getExpenseById(id: String): Expense?
    suspend fun addExpense(expense: Expense, user: String)
    suspend fun updateExpense(expense: Expense, user: String)
    suspend fun deleteExpense(id: String, user: String)
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
    suspend fun refreshExpenses()
}