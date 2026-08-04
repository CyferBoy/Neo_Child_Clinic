package com.neochildclinic.features.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.data.local.dao.AuditLogDao
import com.neochildclinic.data.local.entity.AuditLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuditLogUiState(
    val logs: List<AuditLogEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FullAuditLogViewModel @Inject constructor(
    private val auditLogDao: AuditLogDao
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    
    val uiState: StateFlow<AuditLogUiState> = combine(
        auditLogDao.getAllLogs(),
        _isRefreshing
    ) { logs, refreshing ->
        AuditLogUiState(
            logs = logs,
            isLoading = refreshing
        )
    }.catch { e ->
        emit(AuditLogUiState(error = e.message))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuditLogUiState(isLoading = true)
    )

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            kotlinx.coroutines.delay(500) // Visual feedback
            _isRefreshing.value = false
        }
    }
}
