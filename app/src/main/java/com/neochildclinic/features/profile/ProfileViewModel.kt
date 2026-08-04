package com.neochildclinic.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Staff
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val staff: Staff? = null,
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
                val staff = postgrest.from("staff").select {
                    filter { eq("id", currentUser.id) }
                }.decodeSingleOrNull<Staff>()

                if (staff != null) {
                    _uiState.value = _uiState.value.copy(staff = staff, isLoading = false)
                } else {
                    // Try to find by email
                    val email = currentUser.email
                    if (email != null) {
                        val staffByEmail = postgrest.from("staff").select {
                            filter { eq("email", email) }
                        }.decodeSingleOrNull<Staff>()
                        
                        if (staffByEmail != null) {
                            _uiState.value = _uiState.value.copy(staff = staffByEmail, isLoading = false)
                            return@launch
                        }
                    }

                    // Fallback
                    val staffFallback = Staff(
                        id = currentUser.id,
                        email = currentUser.email ?: "",
                        name = currentUser.userMetadata?.get("name")?.toString() ?: "User",
                        role = "User",
                        createdAt = 0L
                    )
                    _uiState.value = _uiState.value.copy(staff = staffFallback, isLoading = false)
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

                // 2. Update staff table
                postgrest.from("staff").update(mapOf("name" to newName)) {
                    filter { eq("id", currentUser.id) }
                }
                
                _uiState.value = _uiState.value.copy(
                    staff = _uiState.value.staff?.copy(name = newName),
                    isLoading = false,
                    success = "Name updated successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Update failed", isLoading = false)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, success = null)
    }
}
