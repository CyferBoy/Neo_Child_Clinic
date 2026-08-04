package com.neochildclinic.features.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import com.neochildclinic.domain.model.Staff
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
    val staffList: List<Staff> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: String? = null
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
                val staffList = postgrest.from("staff").select().decodeList<Staff>()
                _uiState.value = _uiState.value.copy(
                    staffList = staffList.sortedBy { it.name },
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

    fun createStaffAccount(name: String, email: String, pass: String) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please fill all fields")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)

        viewModelScope.launch {
            try {
                // Supabase doesn't allow creating another user without switching context or using Service Role.
                // For now, we suggest adding them via Supabase Dashboard or an Edge Function.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Staff creation must be done via Supabase Dashboard or Edge Functions for security."
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
                postgrest.from("staff").delete {
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

    fun updateStaffRole(staffId: String, newRole: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
        viewModelScope.launch {
            try {
                postgrest.from("staff").update(mapOf("role" to newRole)) {
                    filter { eq("id", staffId) }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Role updated to $newRole"
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
