package com.neochildclinic.features.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.Reminder
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.domain.usecase.sync.RefreshDataUseCase
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.data.local.entity.toDomain
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.ExpenseRepository
import com.neochildclinic.domain.model.Expense
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatisticsUiState(
    val patients: List<Patient> = emptyList(),
    val vaccinations: List<Vaccination> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val financeTransactions: List<FinanceTransaction> = emptyList(),
    val vaccinationReminders: List<Reminder> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTab: Int = 0,
    val isRefreshing: Boolean = false,
    val refreshError: String? = null,
    val isCalculatingStats: Boolean = false
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val vaccinationRepository: VaccinationRepository,
    private val inventoryRepository: InventoryRepository,
    private val financeRepository: FinanceRepository,
    private val reminderRepository: ReminderRepository,
    private val expenseRepository: ExpenseRepository,
    private val refreshDataUseCase: RefreshDataUseCase
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    private val _refreshError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<StatisticsUiState> = combine(
        listOf(
            patientRepository.allPatients,
            vaccinationRepository.allVaccinations,
            inventoryRepository.getInventoryItems(),
            financeRepository.getAllTransactions(),
            reminderRepository.getAllReminders(),
            expenseRepository.getAllExpenses(),
            _selectedTab,
            _isRefreshing,
            _refreshError
        )
    ) { values ->
        @Suppress("UNCHECKED_CAST") val patients = values[0] as List<Patient>
        @Suppress("UNCHECKED_CAST") val vaccinations = values[1] as List<Vaccination>
        @Suppress("UNCHECKED_CAST") val inventory = values[2] as List<InventoryItem>
        @Suppress("UNCHECKED_CAST") val financeEntities = values[3] as List<com.neochildclinic.data.local.entity.FinanceEntity>
        @Suppress("UNCHECKED_CAST") val reminderEntities = values[4] as List<com.neochildclinic.data.local.entity.ReminderEntity>
        @Suppress("UNCHECKED_CAST") val expenses = values[5] as List<Expense>
        val tab = values[6] as Int
        val refreshing = values[7] as Boolean
        val refreshError = values[8] as String?
        
        StatisticsUiState(
            patients = patients,
            vaccinations = vaccinations,
            inventory = inventory,
            financeTransactions = financeEntities.map { it.toDomain() },
            vaccinationReminders = reminderEntities.map { it.toDomain() },
            expenses = expenses,
            selectedTab = tab,
            isRefreshing = refreshing,
            refreshError = refreshError,
            isLoading = false
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatisticsUiState(isLoading = true))

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
