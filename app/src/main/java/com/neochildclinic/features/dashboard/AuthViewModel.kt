package com.neochildclinic.features.dashboard

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.neochildclinic.domain.manager.SyncManager
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.repository.ProfileRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import androidx.compose.runtime.State
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: Auth,
    private val profileRepository: ProfileRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    val currentUser: UserInfo? get() = auth.currentSessionOrNull()?.user

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    init {
        // Fetch profile if already logged in
        viewModelScope.launch {
            auth.currentSessionOrNull()?.user?.id?.let { userId ->
                fetchProfile(userId)
            }
        }
    }

    private suspend fun fetchProfile(userId: String) {
        try {
            // Get from repository (handles local fallback and remote sync)
            var p = profileRepository.getProfileById(userId)
            val authLastLogin = auth.currentSessionOrNull()?.user?.lastSignInAt?.toString()
            
            if (p == null) {
                val currentUser = auth.currentSessionOrNull()?.user
                if (currentUser != null) {
                    p = Profile(
                        id = currentUser.id,
                        email = currentUser.email ?: "",
                        displayName = currentUser.userMetadata?.get("display_name")?.toString() 
                            ?: currentUser.userMetadata?.get("name")?.toString() 
                            ?: currentUser.email?.substringBefore("@") ?: "User",
                        phoneNumber = currentUser.userMetadata?.get("phone_number")?.toString() ?: "",
                        employeeId = currentUser.userMetadata?.get("employee_id")?.toString(),
                        role = try { 
                            com.neochildclinic.domain.model.UserRole.valueOf(currentUser.userMetadata?.get("role")?.toString() ?: "nurse") 
                        } catch (_: Exception) { com.neochildclinic.domain.model.UserRole.nurse },
                        lastLogin = authLastLogin
                    )
                    profileRepository.saveLocalProfile(p)
                }
            } else if (p.lastLogin != authLastLogin) {
                p = p.copy(lastLogin = authLastLogin)
                profileRepository.updateProfile(p)
            }
            _profile.value = p
            
            // Background refresh
            profileRepository.refreshProfiles()
            profileRepository.getProfileById(userId)?.let {
                _profile.value = it
            }
        } catch (e: Exception) {
            _error.value = "Failed to load profile: ${e.message}"
        }
    }

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        if (email.isBlank() || pass.isBlank()) {
            _error.value = "Please fill all fields"
            return
        }
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = pass
                }
                
                auth.currentSessionOrNull()?.user?.id?.let { fetchProfile(it) }

                _isLoading.value = false
                syncManager.scheduleImmediateSync()
                onSuccess()
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = e.message
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            auth.signOut()
            _profile.value = null
        }
    }
}
