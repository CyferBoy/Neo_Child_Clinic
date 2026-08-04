package com.neochildclinic.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.UserRole
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
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
    private val postgrest: Postgrest
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
                val profile = postgrest.from("profiles").select {
                    filter { eq("id", currentUser.id) }
                }.decodeSingleOrNull<Profile>()

                if (profile != null) {
                    _uiState.value = _uiState.value.copy(profile = profile, isLoading = false)
                } else {
                    // Try to find by email
                    val email = currentUser.email
                    if (email != null) {
                        val profileByEmail = postgrest.from("profiles").select {
                            filter { eq("email", email) }
                        }.decodeSingleOrNull<Profile>()
                        
                        if (profileByEmail != null) {
                            _uiState.value = _uiState.value.copy(profile = profileByEmail, isLoading = false)
                            return@launch
                        }
                    }

                    // Fallback
                    val profileFallback = Profile(
                        id = currentUser.id,
                        email = currentUser.email ?: "",
                        displayName = currentUser.userMetadata?.get("name")?.toString() ?: "User",
                        role = UserRole.nurse
                    )
                    _uiState.value = _uiState.value.copy(profile = profileFallback, isLoading = false)
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
                // 1. Update Supabase Auth User Metadata
                auth.updateUser {
                    data {
                        put("name", newName)
                    }
                }

                // 2. Update profiles table
                postgrest.from("profiles").update(mapOf("display_name" to newName)) {
                    filter { eq("id", currentUser.id) }
                }
                
                _uiState.value = _uiState.value.copy(
                    profile = _uiState.value.profile?.copy(displayName = newName),
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
                postgrest.from("profiles").update(mapOf("phone_number" to newPhone)) {
                    filter { eq("id", currentUser.id) }
                }
                _uiState.value = _uiState.value.copy(
                    profile = _uiState.value.profile?.copy(phoneNumber = newPhone),
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
