package com.neochildclinic.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.repository.DashboardRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val patientCount: Int = 0,
    val lowStockCount: Int = 0,
    val borrowedCount: Int = 0,
    val dueTodayCount: Int = 0,
    val wasteCount: Int = 0,
    val syncState: SyncState = SyncState.IDLE,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Orchestrates Dashboard data using unified data streams.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        dashboardRepository.getPatientCount(),
        dashboardRepository.getLowStockCount(),
        dashboardRepository.getBorrowedCount(),
        dashboardRepository.getDueCount(),
        dashboardRepository.getWasteCount(),
        syncRepository.syncState,
    ) { args ->
        val pCount = args[0] as Int
        val lowStock = args[1] as Int
        val bCount = args[2] as Int
        val dCount = args[3] as Int
        val wCount = args[4] as Int
        val syncState = args[5] as SyncState

        DashboardUiState(
            patientCount = pCount,
            lowStockCount = lowStock,
            borrowedCount = bCount,
            dueTodayCount = dCount,
            wasteCount = wCount,
            syncState = syncState,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState(isLoading = true))

    fun refresh() {
        viewModelScope.launch {
            try {
                dashboardRepository.refreshDashboardData()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
