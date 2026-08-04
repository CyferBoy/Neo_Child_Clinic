package com.neochildclinic.features.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.usecase.patient.GetPatientsUseCase
import com.neochildclinic.domain.usecase.vaccination.GetVaccinationsUseCase
import com.neochildclinic.domain.usecase.sync.RefreshDataUseCase
import com.neochildclinic.domain.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatisticsUiState(
    val patients: List<Patient> = emptyList(),
    val vaccinations: List<Vaccination> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTab: Int = 0,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getPatientsUseCase: GetPatientsUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase,
    private val inventoryRepository: InventoryRepository,
    private val refreshDataUseCase: RefreshDataUseCase
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<StatisticsUiState> = combine(
        getPatientsUseCase(),
        getVaccinationsUseCase(),
        inventoryRepository.getInventoryItems(),
        _selectedTab,
        _isRefreshing
    ) { patients, vaccinations, inventory, tab, refreshing ->
        StatisticsUiState(patients, vaccinations, inventory, false, tab, refreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsUiState(isLoading = true))

    fun updateTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                refreshDataUseCase()
            } catch (_: Exception) { }
            finally {
                _isRefreshing.value = false
            }
        }
    }
}
