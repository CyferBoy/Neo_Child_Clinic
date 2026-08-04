package com.neochildclinic.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.domain.usecase.inventory.BackfillInventoryUsageUseCase
import com.neochildclinic.domain.usecase.inventory.BackfillResult
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val settingsManager: NotificationSettingsManager,
    private val backfillInventoryUsageUseCase: BackfillInventoryUsageUseCase,
    private val auth: Auth
) : ViewModel() {

    val settings: StateFlow<NotificationSettings?> = settingsManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isBackfilling = MutableStateFlow(false)
    val isBackfilling = _isBackfilling.asStateFlow()

    private val _backfillResults = MutableStateFlow<List<BackfillResult>?>(null)
    val backfillResults = _backfillResults.asStateFlow()

    val isAdmin: Boolean
        get() = Constants.ADMIN_EMAILS.contains(auth.currentSessionOrNull()?.user?.email)

    fun updateSettings(settings: NotificationSettings) {
        viewModelScope.launch {
            settingsManager.updateSettings(settings)
        }
    }

    fun runInventoryBackfill() {
        if (!isAdmin) return
        
        viewModelScope.launch {
            _isBackfilling.value = true
            _backfillResults.value = null
            val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown Admin"
            val results = backfillInventoryUsageUseCase.execute(user)
            _backfillResults.value = results
            _isBackfilling.value = false
        }
    }

    fun clearBackfillResults() {
        _backfillResults.value = null
    }
}
