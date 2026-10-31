package com.neochildclinic.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.preferences.PreferenceManager
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.SyncState
import com.neochildclinic.feature.profile.domain.repository.ProfileRepository
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val profileRepository: ProfileRepository,
    private val sessionManager: SessionManager,
    private val syncRepository: SyncRepository
) : ViewModel() {

    val themeMode: StateFlow<String> = preferenceManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val userProfile: StateFlow<Profile?> = profileRepository.allProfiles
        .map { profiles ->
            val currentUserId = sessionManager.getCurrentUserId()
            profiles.find { it.id == currentUserId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val staffCount: StateFlow<Int> = profileRepository.allProfiles
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingSyncCount: StateFlow<Int> = syncRepository.getPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val syncState: StateFlow<SyncState> = syncRepository.syncState

    init {
        viewModelScope.launch {
            try {
                profileRepository.refreshProfiles()
            } catch (e: Exception) {
                // Offline or initial load failure: Room cache will be emitted
                android.util.Log.e("SettingsVM", "Initial profile refresh failed", e)
            }
        }
    }

    fun getCurrentUserDisplayName(): String {
        return sessionManager.getCurrentUserDisplayName("Doctor")
    }

    fun getCurrentUserEmail(): String {
        return sessionManager.getCurrentUserEmail()
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferenceManager.setThemeMode(mode)
        }
    }
}
