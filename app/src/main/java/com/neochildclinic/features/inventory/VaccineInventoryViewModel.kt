package com.neochildclinic.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.InventoryFilter
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventorySort
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.features.settings.NotificationSettingsManager
import io.github.jan.supabase.auth.Auth
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
    val isRefreshing: Boolean = false
)

@HiltViewModel
class VaccineInventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val settingsManager: NotificationSettingsManager,
    private val patientRepository: PatientRepository,
    private val auth: Auth
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(InventoryFilter.ALL)
    private val _sort = MutableStateFlow(InventorySort.ALPHABETICAL)
    private val _isRefreshing = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VaccineInventoryUiState> = combine(
        _searchQuery,
        _filter,
        _sort,
        _isRefreshing
    ) { query, filter, sort, refreshing ->
        Quad(query, filter, sort, refreshing)
    }.flatMapLatest { internal ->
        inventoryRepository.getInventoryItems(internal.first, internal.second, internal.third)
            .map { items ->
                VaccineInventoryUiState(
                    inventory = items,
                    searchQuery = internal.first,
                    filter = internal.second,
                    sort = internal.third,
                    isLoading = false,
                    isRefreshing = internal.fourth
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
            } catch (_: Exception) { }
            finally {
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

    fun deleteBatch(batchId: String) {
        viewModelScope.launch {
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                inventoryRepository.deleteBatch(batchId, user)
            } catch (_: Exception) { }
        }
    }

    fun deleteVaccine(vaccineId: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                inventoryRepository.deleteVaccine(vaccineId, user)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete vaccine")
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
