package com.neochildclinic.feature.inventory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.preferences.NotificationSettings
import com.neochildclinic.core.preferences.NotificationSettingsManager
import com.neochildclinic.domain.model.InventoryFilter
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventorySort
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.core.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaccineInventoryUiState(
    val inventory: List<InventoryItem> = emptyList(),
    val searchQuery: String = "",
    val filter: InventoryFilter = InventoryFilter.ALL,
    val sort: InventorySort = InventorySort.ALPHABETICAL,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hideExpiredEmpty: Boolean = true,
    val warnDaysBeforeExpiry: Int = 30,
    val showStockValueEnabled: Boolean = true
)

@HiltViewModel
class VaccineInventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val sessionManager: SessionManager,
    private val settingsManager: NotificationSettingsManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _filter = MutableStateFlow(InventoryFilter.ALL)
    private val _sort = MutableStateFlow<InventorySort?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VaccineInventoryUiState> = combine(
        _searchQuery,
        _filter,
        _sort,
        _isRefreshing,
        settingsManager.settingsFlow
    ) { query, filter, sortOverride, refreshing, settings ->
        InventoryStateParams(query, filter, sortOverride, refreshing, settings)
    }.flatMapLatest { params ->
        val activeSort = params.sortOverride ?: when (params.settings.sortMethod) {
            "Expiry Date" -> InventorySort.EXPIRY
            "Quantity" -> InventorySort.HIGHEST_STOCK
            else -> InventorySort.ALPHABETICAL
        }
        inventoryRepository.getInventoryItems(params.query, params.filter, activeSort)
            .map { items ->
                VaccineInventoryUiState(
                    inventory = items,
                    searchQuery = params.query,
                    filter = params.filter,
                    sort = activeSort,
                    isLoading = false,
                    isRefreshing = params.refreshing,
                    hideExpiredEmpty = params.settings.hideExpiredEmpty,
                    warnDaysBeforeExpiry = params.settings.warnDaysBeforeExpiry,
                    showStockValueEnabled = params.settings.showStockValueEnabled
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaccineInventoryUiState()
    )

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                inventoryRepository.refreshInventory()
            } catch (e: Exception) {
                android.util.Log.e("VaccineInventoryVM", "Refresh failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterChange(filter: InventoryFilter) {
        _filter.value = filter
    }

    fun onSortChange(sort: InventorySort) {
        _sort.value = sort
    }

    fun deleteBatch(batchId: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val user = sessionManager.getCurrentUserEmail()
                inventoryRepository.deleteBatch(batchId, user)
            } catch (e: Exception) {
                android.util.Log.e("VaccineInventoryVM", "Delete batch failed", e)
                onError(e.message ?: "Failed to delete batch")
            }
        }
    }

    fun deleteVaccine(vaccineId: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val user = sessionManager.getCurrentUserEmail()
                inventoryRepository.deleteVaccine(vaccineId, user)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete vaccine")
            }
        }
    }
}

private data class InventoryStateParams(
    val query: String,
    val filter: InventoryFilter,
    val sortOverride: InventorySort?,
    val refreshing: Boolean,
    val settings: NotificationSettings
)
