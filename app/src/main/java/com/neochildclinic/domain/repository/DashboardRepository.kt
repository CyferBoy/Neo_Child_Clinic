package com.neochildclinic.domain.repository

import kotlinx.coroutines.flow.Flow

interface DashboardRepository {
    fun getPatientCount(): Flow<Int>
    fun getLowStockCount(): Flow<Int>
    fun getBorrowedCount(): Flow<Int>
    fun getDueCount(): Flow<Int>
    fun getWasteCount(): Flow<Int>
    suspend fun refreshDashboardData()
}
