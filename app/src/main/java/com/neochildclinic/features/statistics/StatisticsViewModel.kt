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
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.domain.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatisticsUiState(
    val patients: List<Patient> = emptyList(),
    val vaccinations: List<Vaccination> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val financeTransactions: List<FinanceEntity> = emptyList(),
    val vaccinationReminders: List<ReminderEntity> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTab: Int = 0,
    val isRefreshing: Boolean = false,
    val refreshError: String? = null
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getPatientsUseCase: GetPatientsUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase,
    private val inventoryRepository: InventoryRepository,
    private val financeRepository: FinanceRepository,
    private val reminderRepository: ReminderRepository,
    private val refreshDataUseCase: RefreshDataUseCase
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<StatisticsUiState> = combine(
        listOf(
            getPatientsUseCase(),
            getVaccinationsUseCase(),
            inventoryRepository.getInventoryItems(),
            financeRepository.getAllTransactions(),
            reminderRepository.getAllReminders(),
            _selectedTab,
            _isRefreshing,
            _refreshError
        )
    ) { values ->
        @Suppress("UNCHECKED_CAST") val patients = values[0] as List<Patient>
        @Suppress("UNCHECKED_CAST") val vaccinations = values[1] as List<Vaccination>
        @Suppress("UNCHECKED_CAST") val inventory = values[2] as List<InventoryItem>
        @Suppress("UNCHECKED_CAST") val financeTransactions = values[3] as List<FinanceEntity>
        @Suppress("UNCHECKED_CAST") val vaccinationReminders = values[4] as List<ReminderEntity>
        val tab = values[5] as Int
        val refreshing = values[6] as Boolean
        val refreshError = values[7] as String?
        StatisticsUiState(patients, vaccinations, inventory, financeTransactions, vaccinationReminders, false, tab, refreshing, refreshError)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsUiState(isLoading = true))

    fun updateTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshError.value = null
            try {
                refreshDataUseCase()
            } catch (e: Exception) {
                _refreshError.value = e.message ?: "Unable to refresh statistics"
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
