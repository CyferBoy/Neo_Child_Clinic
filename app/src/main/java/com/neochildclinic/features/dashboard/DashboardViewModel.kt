package com.neochildclinic.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.repository.DashboardRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.SyncState
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
    val userRole: UserRole = UserRole.nurse,
    val profile: Profile? = null,
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

    private val _profile = MutableStateFlow<Profile?>(null)

    init {
        fetchProfile()
    }

    private fun fetchProfile() {
        val currentUser = auth.currentSessionOrNull()?.user ?: return
        
        viewModelScope.launch {
            try {
                val profile = postgrest.from("profiles").select {
                    filter { eq("id", currentUser.id) }
                }.decodeSingleOrNull<Profile>()

                if (profile != null) {
                    _profile.value = profile
                } else {
                    // Try to find by email
                    val email = currentUser.email
                    if (email != null) {
                        val profileByEmail = postgrest.from("profiles").select {
                            filter { eq("email", email) }
                        }.decodeSingleOrNull<Profile>()
                        
                        if (profileByEmail != null) {
                            _profile.value = profileByEmail
                            return@launch
                        }
                    }

                    // Fallback profile object
                    _profile.value = Profile(
                        id = currentUser.id,
                        email = currentUser.email ?: "",
                        displayName = currentUser.userMetadata?.get("name")?.toString() ?: currentUser.email?.substringBefore("@") ?: "User",
                        role = UserRole.nurse
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
        _profile
    ) { args ->
        val pCount = args[0] as Int
        val lowStock = args[1] as Int
        val bCount = args[2] as Int
        val dCount = args[3] as Int
        val wCount = args[4] as Int
        val syncState = args[5] as SyncState
        val profile = args[6] as? Profile

        DashboardUiState(
            patientCount = pCount,
            lowStockCount = lowStock,
            borrowedCount = bCount,
            dueTodayCount = dCount,
            wasteCount = wCount,
            userName = profile?.displayName ?: "User",
            userRole = profile?.role ?: UserRole.nurse,
            profile = profile,
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
