package com.neochildclinic.features.dashboard

import androidx.lifecycle.ViewModel
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val staffList: List<Profile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: String? = null,
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        fetchStaff()
    }

    fun fetchStaff() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch {
            try {
                val staffList = postgrest.from("profiles").select().decodeList<Profile>()
                _uiState.value = _uiState.value.copy(
                    staffList = staffList.sortedBy { it.displayName },
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    fun createStaffAccount(name: String, email: String, pass: String, role: UserRole) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please fill all fields")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)

        viewModelScope.launch {
            try {
                // Call RPC to create new staff (Admin only)
                // We pass the role to the backend logic (Edge Function recommended)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Staff creation ($role) must be done via Supabase Edge Functions to securely manage auth.admin API."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteStaff(staffId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch {
            try {
                postgrest.from("profiles").delete {
                    filter { eq("id", staffId) }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Staff data deleted successfully",
                    error = "Note: Auth account must be deleted via Supabase Dashboard."
                )
                fetchStaff()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun resetStaffPassword(email: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
        viewModelScope.launch {
            try {
                auth.resetPasswordForEmail(email)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Password reset email sent to $email"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateStaffRole(staffId: String, newRole: UserRole) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
        viewModelScope.launch {
            try {
                postgrest.from("profiles").update(mapOf("role" to newRole.name)) {
                    filter { eq("id", staffId) }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Role updated to ${newRole.name}"
                )
                fetchStaff()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun toggleStaffStatus(staffId: String, isActive: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
        viewModelScope.launch {
            try {
                postgrest.from("profiles").update(mapOf("is_active" to isActive)) {
                    filter { eq("id", staffId) }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = if (isActive) "Staff activated" else "Staff deactivated"
                )
                fetchStaff()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateStaffDetails(staffId: String, name: String, phone: String, role: UserRole) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
        viewModelScope.launch {
            try {
                postgrest.from("profiles").update(
                    mapOf(
                        "display_name" to name,
                        "phone_number" to phone,
                        "role" to role.name
                    )
                ) {
                    filter { eq("id", staffId) }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Staff updated successfully"
                )
                fetchStaff()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, success = null)
    }
}
