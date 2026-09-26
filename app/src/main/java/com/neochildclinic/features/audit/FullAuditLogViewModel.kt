package com.neochildclinic.features.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.data.local.entity.AuditLogEntity
import com.neochildclinic.data.repository.AuditLogRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Audit logs are intentionally remote-only.
 * The Room audit table is never used as a source for this screen.
 * Older records are fetched from Supabase in pages as the user scrolls.
 */
@HiltViewModel
class FullAuditLogViewModel @Inject constructor(
    private val auditLogRepository: AuditLogRepositoryImpl
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 50
    }

    data class AuditLogUiState(
        val logs: List<AuditLogEntity> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val current = _uiState.value
        if (current.isLoading || current.isRefreshing) return
        val isInitialLoad = current.logs.isEmpty()
        viewModelScope.launch {
            _isRefreshing.value = true
            if (isInitialLoad) _uiState.value = current.copy(isLoading = true)
            try {
                val firstPage = fetchPage(0)
                _uiState.value = AuditLogUiState(
                    logs = firstPage,
                    isLoading = false,
                    hasMore = firstPage.size == PAGE_SIZE,
                    error = null
                )
            } catch (e: Exception) {
                // Refresh failures keep the existing rows on screen; only surface the error.
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Unable to load audit logs")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true, error = null)
            try {
                val page = fetchPage(state.logs.size)
                val merged = (state.logs + page).distinctBy { it.id }
                _uiState.value = _uiState.value.copy(
                    logs = merged,
                    isLoadingMore = false,
                    hasMore = page.size == PAGE_SIZE
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = e.message ?: "Unable to load more audit logs"
                )
            }
        }
    }

    private suspend fun fetchPage(offset: Int): List<AuditLogEntity> {
        return auditLogRepository.getPaged(null, offset, PAGE_SIZE)
    }
}
