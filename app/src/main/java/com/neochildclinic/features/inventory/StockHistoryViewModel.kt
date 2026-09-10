package com.neochildclinic.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.InventoryFilter
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventorySort
import com.neochildclinic.domain.model.StockHistoryTypeFilter
import com.neochildclinic.domain.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

private const val PAGE_SIZE = 40

data class StockHistoryUiState(
    val transactions: List<InventoryTransactionEntity> = emptyList(),
    val vaccines: List<InventoryItem> = emptyList(),
    val batchesForSelectedVaccine: List<VaccineBatchEntity> = emptyList(),
    // batchId -> "batchNumber" and vaccineId -> brandName, built from the same inventory
    // list already loaded for the vaccine filter, so history rows can show readable
    // names without a second lookup query per row.
    val batchLabelById: Map<String, String> = emptyMap(),
    val vaccineLabelById: Map<String, String> = emptyMap(),
    val selectedVaccineId: String? = null,
    val selectedBatchId: String? = null,
    val selectedTypeFilter: StockHistoryTypeFilter = StockHistoryTypeFilter.ALL,
    val fromDate: String = "",
    val toDate: String = "",
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null
) {
    val selectedVaccineName: String get() = selectedVaccineId?.let { vaccineLabelById[it] } ?: "All Vaccines"
    val selectedBatchLabel: String get() = selectedBatchId?.let { batchLabelById[it] } ?: "All Batches"
}

@HiltViewModel
class StockHistoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockHistoryUiState())
    val uiState: StateFlow<StockHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            inventoryRepository.getInventoryItems(sort = InventorySort.ALPHABETICAL, filter = InventoryFilter.ALL)
                .collect { items ->
                    val batchLabels = items.flatMap { it.batches }.associate { it.batchId to it.batchNumber }
                    val vaccineLabels = items.associate { it.id to it.brandName }
                    _uiState.update {
                        it.copy(vaccines = items, batchLabelById = batchLabels, vaccineLabelById = vaccineLabels)
                    }
                }
        }
        loadPage(reset = true)
    }

    fun onVaccineFilterChange(vaccineId: String?) {
        _uiState.update {
            it.copy(selectedVaccineId = vaccineId, selectedBatchId = null, batchesForSelectedVaccine = emptyList())
        }
        if (vaccineId != null) {
            viewModelScope.launch {
                inventoryRepository.getVaccineBatches(vaccineId).collect { batches ->
                    _uiState.update { it.copy(batchesForSelectedVaccine = batches) }
                }
            }
        }
        loadPage(reset = true)
    }

    fun onBatchFilterChange(batchId: String?) {
        _uiState.update { it.copy(selectedBatchId = batchId) }
        loadPage(reset = true)
    }

    fun onTypeFilterChange(filter: StockHistoryTypeFilter) {
        _uiState.update { it.copy(selectedTypeFilter = filter) }
        loadPage(reset = true)
    }

    fun onDateRangeChange(fromDate: String, toDate: String) {
        _uiState.update { it.copy(fromDate = fromDate, toDate = toDate) }
        loadPage(reset = true)
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedVaccineId = null,
                selectedBatchId = null,
                selectedTypeFilter = StockHistoryTypeFilter.ALL,
                fromDate = "",
                toDate = "",
                batchesForSelectedVaccine = emptyList()
            )
        }
        loadPage(reset = true)
    }

    fun refresh() = loadPage(reset = true)

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.canLoadMore) return
        loadPage(reset = false)
    }

    private fun loadPage(reset: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            if (reset) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(isLoadingMore = true) }
            }

            try {
                val offset = if (reset) 0 else state.transactions.size
                val page = inventoryRepository.getStockHistoryPage(
                    vaccineId = state.selectedVaccineId,
                    batchId = state.selectedBatchId,
                    types = state.selectedTypeFilter.transactionTypes,
                    fromDateIso = toIsoDateOnly(state.fromDate),
                    toDateIso = toIsoDateOnly(state.toDate),
                    limit = PAGE_SIZE,
                    offset = offset,
                    remoteOnly = true
                )

                _uiState.update {
                    it.copy(
                        transactions = if (reset) page else it.transactions + page,
                        isLoading = false,
                        isLoadingMore = false,
                        canLoadMore = page.size == PAGE_SIZE
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isLoadingMore = false, error = e.message ?: "Failed to load stock history")
                }
            }
        }
    }

    private fun toIsoDateOnly(displayDate: String): String? {
        if (displayDate.isBlank()) return null
        val date = PatientUtils.parseDate(displayDate) ?: return null
        return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(date)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
