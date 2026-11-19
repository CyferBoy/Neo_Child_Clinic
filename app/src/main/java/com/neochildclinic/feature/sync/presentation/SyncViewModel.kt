package com.neochildclinic.feature.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.sync.RefreshDataUseCase
import com.neochildclinic.domain.model.SyncItem
import com.neochildclinic.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val refreshDataUseCase: RefreshDataUseCase
) : ViewModel() {

    val syncQueue: StateFlow<List<SyncItem>> = syncRepository.getSyncQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // Last forceRefreshFromCloud() failure, surfaced on the Sync screen. Mirrors the
    // _refreshError pattern the statistics/patient ViewModels already use.
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError = _refreshError.asStateFlow()

    fun processSync() {
        viewModelScope.launch {
            try {
                syncRepository.processNextItems()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Uncaught here would reach the default handler and kill the process;
                // per-item failures are already counted by the queue and SyncWorker.
                _refreshError.update { e.message ?: "Unable to sync" }
            }
        }
    }

    fun forceRefreshFromCloud() {
        viewModelScope.launch {
            _isRefreshing.update { true }
            _refreshError.update { null }
            try {
                refreshDataUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _refreshError.update { e.message ?: "Unable to refresh from cloud" }
            } finally {
                _isRefreshing.update { false }
            }
        }
    }

    fun clearSynced() {
        viewModelScope.launch {
            syncRepository.clearSyncedItems()
        }
    }

    fun retryItem(queueId: Long) {
        viewModelScope.launch {
            syncRepository.retryItem(queueId)
        }
    }

    fun deleteItem(queueId: Long) {
        viewModelScope.launch {
            syncRepository.deleteQueueItem(queueId)
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            syncRepository.retryFailedItems()
        }
    }

    fun deleteAllFailed() {
        viewModelScope.launch {
            syncRepository.deleteAllFailed()
        }
    }
}
