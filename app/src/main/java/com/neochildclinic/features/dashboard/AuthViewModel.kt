package com.neochildclinic.features.dashboard

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.neochildclinic.domain.manager.SyncManager
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import androidx.compose.runtime.State
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: Auth,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    val currentUser = auth.currentSessionOrNull()?.user

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
        }
    }
}
