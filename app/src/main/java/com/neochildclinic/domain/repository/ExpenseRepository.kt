package com.neochildclinic.domain.repository

interface ExpenseRepository {
    suspend fun refreshExpenses()
}