package com.neochildclinic.feature.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.sync.SyncRepositoryImpl
import com.neochildclinic.feature.sync.domain.RefreshDataUseCase
import com.neochildclinic.domain.model.SyncItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    // Deliberately the impl, not the SyncRepository contract: this screen manages the queue
    // (retry / clear / delete individual items), and the contract deliberately exposes only
    // enqueue + state (see its own doc comment). Widening the contract to cover a single
    // screen's needs would be a worse trade than this one injection.
    private val syncRepository: SyncRepositoryImpl,
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
                _refreshError.value = e.message ?: "Unable to sync"
            }
        }
    }

    fun forceRefreshFromCloud() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshError.value = null
            try {
                refreshDataUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _refreshError.value = e.message ?: "Unable to refresh from cloud"
            } finally {
                _isRefreshing.value = false
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
