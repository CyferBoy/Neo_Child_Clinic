package com.neochildclinic.features.dashboard

import androidx.lifecycle.ViewModel
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.repository.ProfileRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions
import kotlinx.serialization.Serializable
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
    private val profileRepository: ProfileRepository,
    private val auth: Auth,
    private val functions: Functions
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        fetchStaff()
        observeStaff()
    }

    private fun observeStaff() {
        viewModelScope.launch {
            profileRepository.allProfiles.collect { list ->
                _uiState.value = _uiState.value.copy(staffList = list)
            }
        }
    }

    fun fetchStaff() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            profileRepository.refreshProfiles()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun createStaffAccount(name: String, email: String, pass: String, role: UserRole, employeeId: String?, phoneNumber: String?) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please fill all fields")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)

        viewModelScope.launch {
            try {
                functions.invoke("manage-staff", CreateStaffRequest(
                    name = name, 
                    email = email, 
                    password = pass, 
                    role = role.name, 
                    employeeId = employeeId,
                    phoneNumber = phoneNumber,
                    action = "CREATE" // Explicitly pass the action
                ))
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Staff account created for $name ($role). They can now log in."
                )
                fetchStaff()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteStaff(staffId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                profileRepository.deleteProfile(staffId)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Staff data deleted successfully",
                    error = "Note: Auth account must be deleted via Supabase Dashboard."
                )
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
                profileRepository.updateProfileRole(staffId, newRole)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Role updated to ${newRole.name}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun toggleStaffStatus(staffId: String, isActive: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
        viewModelScope.launch {
            try {
                profileRepository.toggleProfileStatus(staffId, isActive)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = if (isActive) "Staff activated" else "Staff deactivated"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun updateStaffDetails(staffId: String, name: String, phone: String, role: UserRole) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
        viewModelScope.launch {
            try {
                val profile = profileRepository.getProfileById(staffId) ?: return@launch
                val updated = profile.copy(
                    displayName = name,
                    phoneNumber = phone,
                    role = role
                )
                profileRepository.updateProfile(updated)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Staff updated successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, success = null)
    }
}

@Serializable
data class CreateStaffRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    val employeeId: String? = null,
    val phoneNumber: String? = null,
    val action: String = "CREATE"
)
