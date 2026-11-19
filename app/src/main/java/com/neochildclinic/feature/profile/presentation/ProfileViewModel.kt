package com.neochildclinic.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.common.Constants
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.core.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val sessionManager: SessionManager,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        val currentUserId = sessionManager.getCurrentUserId() ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                var profile = profileRepository.getProfileById(currentUserId)

                if (profile == null) {
                    // First load requires network: pull once, then re-read Room.
                    // No metadata-based fallback - user_metadata never carries a role.
                    profileRepository.refreshProfiles()
                    profile = profileRepository.getProfileById(currentUserId)
                }

                if (profile == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Unable to load your profile. Check your connection and try again."
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(profile = profile, isLoading = false) }
                
                profileRepository.refreshProfiles()
                val refreshed = profileRepository.getProfileById(currentUserId)
                if (refreshed != null) {
                    _uiState.update { it.copy(profile = refreshed) }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun updateName(newName: String) {
        if (newName.isBlank()) return
        val currentUserId = sessionManager.getCurrentUserId() ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = null) }
            try {
                profileRepository.updateAuthName(newName)

                val currentProfile = _uiState.value.profile ?: profileRepository.getProfileById(currentUserId)
                val updated = currentProfile?.copy(displayName = newName)
                if (updated == null) {
                    // Silently returning left isLoading = true forever, so Save stayed disabled
                    // with no message (NCC-016).
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Could not load your profile. Please try again.",
                        )
                    }
                    return@launch
                }
                
                profileRepository.updateProfile(updated)
                
                _uiState.update {
                    it.copy(
                        profile = updated,
                        isLoading = false,
                        success = "Name updated successfully"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Update failed", isLoading = false) }
            }
        }
    }

    fun updatePhoneNumber(newPhone: String) {
        val currentUserId = sessionManager.getCurrentUserId() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = null) }
            try {
                val currentProfile = _uiState.value.profile ?: profileRepository.getProfileById(currentUserId)
                val updated = currentProfile?.copy(phoneNumber = newPhone)
                if (updated == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Could not load your profile. Please try again.",
                        )
                    }
                    return@launch
                }
                
                profileRepository.updateProfile(updated)
                
                _uiState.update {
                    it.copy(
                        profile = updated,
                        isLoading = false,
                        success = "Phone number updated"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun changePassword(newPassword: String) {
        // Must match MIN_PASSWORD_LENGTH in supabase/functions/manage-staff/index.ts and
        // minimum_password_length in supabase/config.toml. The admin-provisioning path
        // already enforces 12; letting self-service go lower was audit finding M2, half-fixed.
        if (newPassword.length < Constants.MIN_PASSWORD_LENGTH) {
            _uiState.update { it.copy(error = Constants.PASSWORD_MIN_LENGTH_ERROR) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, success = null) }
            try {
                profileRepository.updateAuthPassword(newPassword)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        success = "Password changed successfully"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, success = null) }
    }
}
