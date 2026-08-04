package com.neochildclinic.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Staff
import com.neochildclinic.domain.repository.DashboardRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.SyncState
import com.neochildclinic.domain.usecase.statistics.GetClinicStatsUseCase
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
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
    val userName: String = "User",
    val userRole: String = "Staff",
    val staff: Staff? = null,
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
    private val auth: Auth,
    private val postgrest: Postgrest
) : ViewModel() {

    private val _staff = MutableStateFlow<Staff?>(null)

    init {
        fetchStaffProfile()
    }

    private fun fetchStaffProfile() {
        val currentUser = auth.currentSessionOrNull()?.user ?: return
        
        viewModelScope.launch {
            try {
                val staff = postgrest.from("staff").select {
                    filter { eq("id", currentUser.id) }
                }.decodeSingleOrNull<Staff>()

                if (staff != null) {
                    _staff.value = staff
                } else {
                    // Try to find by email
                    val email = currentUser.email
                    if (email != null) {
                        val staffByEmail = postgrest.from("staff").select {
                            filter { eq("email", email) }
                        }.decodeSingleOrNull<Staff>()
                        
                        if (staffByEmail != null) {
                            _staff.value = staffByEmail
                            return@launch
                        }
                    }

                    // Fallback staff object
                    _staff.value = Staff(
                        id = currentUser.id,
                        email = currentUser.email ?: "",
                        name = currentUser.userMetadata?.get("name")?.toString() ?: currentUser.email?.substringBefore("@") ?: "User",
                        role = "User",
                        createdAt = 0L
                    )
                }
            } catch (e: Exception) {
                // Keep default or handle error
            }
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        dashboardRepository.getPatientCount(),
        dashboardRepository.getLowStockCount(),
        dashboardRepository.getBorrowedCount(),
        dashboardRepository.getDueCount(),
        dashboardRepository.getWasteCount(),
        syncRepository.syncState,
        _staff
    ) { args ->
        val pCount = args[0] as Int
        val lowStock = args[1] as Int
        val bCount = args[2] as Int
        val dCount = args[3] as Int
        val wCount = args[4] as Int
        val syncState = args[5] as SyncState
        val staff = args[6] as? Staff

        DashboardUiState(
            patientCount = pCount,
            lowStockCount = lowStock,
            borrowedCount = bCount,
            dueTodayCount = dCount,
            wasteCount = wCount,
            userName = staff?.name ?: "User",
            userRole = staff?.role ?: "Staff",
            staff = staff,
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
