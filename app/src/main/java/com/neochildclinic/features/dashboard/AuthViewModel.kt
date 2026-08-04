package com.neochildclinic.features.dashboard

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.neochildclinic.domain.manager.SyncManager
import com.neochildclinic.domain.model.Profile
import io.github.jan.supabase.postgrest.Postgrest
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
    private val postgrest: Postgrest,
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
            val p = postgrest.from("profiles").select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<Profile>()
            _profile.value = p
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
                val session = auth.signInWith(Email) {
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
