package com.neochildclinic.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.repository.ProfileRepository
import io.github.jan.supabase.auth.Auth
import kotlinx.serialization.json.put
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: Profile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: Auth,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        val currentUser = auth.currentSessionOrNull()?.user ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Try to get from local repository first
                var profile = profileRepository.getProfileById(currentUser.id)

                if (profile == null) {
                    // Fallback to initial profile from auth metadata
                    profile = Profile(
                        id = currentUser.id,
                        email = currentUser.email ?: "",
                        displayName = currentUser.userMetadata?.get("display_name")?.toString() 
                            ?: currentUser.userMetadata?.get("name")?.toString() 
                            ?: currentUser.email?.substringBefore("@") ?: "User",
                        phoneNumber = currentUser.userMetadata?.get("phone_number")?.toString() ?: "",
                        employeeId = currentUser.userMetadata?.get("employee_id")?.toString(),
                        role = try { 
                            UserRole.valueOf(currentUser.userMetadata?.get("role")?.toString() ?: "nurse") 
                        } catch (_: Exception) { UserRole.nurse }
                    )
                    profileRepository.saveLocalProfile(profile)
                }
                
                _uiState.value = _uiState.value.copy(profile = profile, isLoading = false)
                
                // Refresh from remote
                profileRepository.refreshProfiles()
                val refreshed = profileRepository.getProfileById(currentUser.id)
                if (refreshed != null) {
                    _uiState.value = _uiState.value.copy(profile = refreshed)
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun updateName(newName: String) {
        if (newName.isBlank()) return
        val currentUser = auth.currentSessionOrNull()?.user ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
            try {
                // 1. Update Supabase Auth User Metadata (Internal to Auth)
                auth.updateUser {
                    data {
                        put("name", newName)
                    }
                }

                // 2. Update via Repository (Handles local DB + Sync)
                val currentProfile = _uiState.value.profile ?: profileRepository.getProfileById(currentUser.id)
                val updated = currentProfile?.copy(displayName = newName) ?: return@launch
                
                profileRepository.updateProfile(updated)
                
                _uiState.value = _uiState.value.copy(
                    profile = updated,
                    isLoading = false,
                    success = "Name updated successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Update failed", isLoading = false)
            }
        }
    }

    fun updatePhoneNumber(newPhone: String) {
        val currentUser = auth.currentSessionOrNull()?.user ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
            try {
                val currentProfile = _uiState.value.profile ?: profileRepository.getProfileById(currentUser.id)
                val updated = currentProfile?.copy(phoneNumber = newPhone) ?: return@launch
                
                profileRepository.updateProfile(updated)
                
                _uiState.value = _uiState.value.copy(
                    profile = updated,
                    isLoading = false,
                    success = "Phone number updated"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun changePassword(newPassword: String) {
        if (newPassword.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, success = null)
            try {
                auth.updateUser {
                    password = newPassword
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = "Password changed successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, success = null)
    }
}
