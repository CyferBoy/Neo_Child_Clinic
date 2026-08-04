package com.neochildclinic.features.vaccination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.utils.InventoryUtils
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.InventoryFilter
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Vaccine
import com.neochildclinic.domain.usecase.inventory.ReconcileInventoryUseCase
import com.neochildclinic.domain.usecase.vaccination.CompleteVaccinationUseCase
import com.neochildclinic.domain.usecase.vaccination.GetVaccinationsUseCase
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.features.settings.NotificationSettingsManager
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddVaccinationUiState(
    val isLoading: Boolean = false,
    val availableVaccines: List<Vaccine> = emptyList(),
    val activeBatches: Map<String, List<VaccineBatchEntity>> = emptyMap(),
    val error: String? = null,
    val isSaved: Boolean = false,
    val savedVaccination: Vaccination? = null,
    
    // Form State
    val patientId: String = "",
    val receiptNumber: String = "",
    val selectedVaccines: List<String> = emptyList(),
    val selectedVaccineIds: List<String> = emptyList(),
    val selectedBatchIds: List<String> = emptyList(),
    val batchNumbers: List<String> = emptyList(),
    val expiryDates: List<String> = emptyList(),
    val nextBrandSearch: String = "",
    val dateGiven: String = "",
    val nextDueDate: String = "",
    val cost: String = "",
    val cashAmount: String = "",
    val onlineAmount: String = "",
    val totalPaid: Double = 0.0,
    val withFees: Boolean = false,
    val doctorsAcc: Boolean = false,
    val vaccineRequiringBatchSelection: Vaccine? = null
)

@HiltViewModel
class AddVaccinationViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val completeVaccinationUseCase: CompleteVaccinationUseCase,
    private val getVaccinationsUseCase: GetVaccinationsUseCase,
    private val reconcileInventoryUseCase: ReconcileInventoryUseCase,
    private val settingsManager: NotificationSettingsManager,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddVaccinationUiState())
    val uiState: StateFlow<AddVaccinationUiState> = _uiState.asStateFlow()

    private val _vaccination = MutableStateFlow<Vaccination?>(null)
    val vaccination: StateFlow<Vaccination?> = _vaccination.asStateFlow()

    init {
        observeInventory()
    }

    private fun observeInventory() {
        viewModelScope.launch {
            combine(
                inventoryRepository.getInventoryItems(filter = InventoryFilter.ALL),
                settingsManager.settingsFlow
            ) { items, _ ->
                val vaccines = items.filter { it.stock > 0 }.map { item ->
                    Vaccine(
                        id = item.id,
                        type = item.type,
                        brandName = item.brandName,
                        companyName = item.company,
                        stock = item.stock,
                        isLowStock = item.isLowStock
                    )
                }
                
                val batchesMap = items.associate { item ->
                    item.id to item.batches.filter { 
                        it.remainingQuantity > 0 && !InventoryUtils.isExpired(it.expiryDate) 
                    }.sortedBy { it.expiryDate }
                }

                _uiState.update { 
                    it.copy(
                        availableVaccines = vaccines,
                        activeBatches = batchesMap
                    )
                }
            }.collect()
        }
    }

    val currentUserEmail: String
        get() = auth.currentSessionOrNull()?.user?.email ?: "Unknown Staff"

    fun onVaccineSelected(vaccine: Vaccine) {
        val batches = _uiState.value.activeBatches[vaccine.id] ?: emptyList()
        if (batches.isEmpty()) {
            // Out of stock case
            addVaccineToForm(vaccine, null)
        } else if (batches.size == 1) {
            addVaccineToForm(vaccine, batches.first())
        } else {
            _uiState.update { it.copy(vaccineRequiringBatchSelection = vaccine) }
        }
    }

    fun onBatchSelected(vaccine: Vaccine, batch: VaccineBatchEntity) {
        addVaccineToForm(vaccine, batch)
        _uiState.update { it.copy(vaccineRequiringBatchSelection = null) }
    }

    fun dismissBatchSelection() {
        _uiState.update { it.copy(vaccineRequiringBatchSelection = null) }
    }

    private fun addVaccineToForm(vaccine: Vaccine, batch: VaccineBatchEntity?) {
        _uiState.update { state ->
            state.copy(
                selectedVaccines = state.selectedVaccines + vaccine.brandName,
                selectedVaccineIds = state.selectedVaccineIds + vaccine.id,
                selectedBatchIds = state.selectedBatchIds + (batch?.batchId ?: ""),
                batchNumbers = state.batchNumbers + (batch?.batchNumber ?: ""),
                expiryDates = state.expiryDates + (batch?.expiryDate ?: "")
            )
        }
    }

    fun onRemoveVaccine(index: Int) {
        _uiState.update { state ->
            state.copy(
                selectedVaccines = state.selectedVaccines.toMutableList().apply { removeAt(index) },
                selectedVaccineIds = state.selectedVaccineIds.toMutableList().apply { removeAt(index) },
                selectedBatchIds = state.selectedBatchIds.toMutableList().apply { removeAt(index) },
                batchNumbers = state.batchNumbers.toMutableList().apply { removeAt(index) },
                expiryDates = state.expiryDates.toMutableList().apply { removeAt(index) }
            )
        }
    }

    fun saveVaccination(
        vaccination: Vaccination,
        isNew: Boolean,
        selectedVaccineIds: List<String>,
        selectedBatchIds: List<String>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                completeVaccinationUseCase(
                    vaccination = vaccination,
                    isNew = isNew,
                    selectedVaccineIds = selectedVaccineIds,
                    user = user,
                    selectedBatchIds = selectedBatchIds
                )
                _uiState.update { it.copy(
                    isSaved = true, 
                    savedVaccination = vaccination, 
                    isLoading = false
                ) }
                onSuccess()
                
                // STEP 5: Trigger reconciliation after save (fire-and-forget, non-blocking)
                if (isNew) {
                    viewModelScope.launch {
                        try {
                            reconcileInventoryUseCase.execute(user)
                        } catch (e: Exception) {
                            android.util.Log.e("AddVaccinationVM", "Reconciliation failed: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun prefillForm(v: Vaccination) {
        _uiState.update { it.copy(
            patientId = v.patientId,
            receiptNumber = v.receiptNumber,
            selectedVaccines = v.vaccineNames,
            selectedVaccineIds = v.vaccineIds,
            batchNumbers = v.batchNumbers,
            selectedBatchIds = v.batchIds,
            expiryDates = v.expiryDates,
            nextBrandSearch = v.nxtVaccineNames.joinToString(", "),
            dateGiven = v.dateGiven,
            nextDueDate = v.nextDueDate,
            cost = if (v.cost % 1.0 == 0.0) v.cost.toInt().toString() else v.cost.toString(),
            cashAmount = if (v.cashAmount % 1.0 == 0.0) v.cashAmount.toInt().toString() else v.cashAmount.toString(),
            onlineAmount = if (v.onlineAmount % 1.0 == 0.0) v.onlineAmount.toInt().toString() else v.onlineAmount.toString(),
            totalPaid = v.totalPaid,
            withFees = v.withFees,
            doctorsAcc = v.doctorsAcc
        ) }
    }

    fun loadVaccination(id: String) {
        viewModelScope.launch {
            getVaccinationsUseCase().collect { all ->
                _vaccination.value = all.find { it.id == id }
            }
        }
    }

    fun initializeDates(today: String) {
        if (_uiState.value.dateGiven.isEmpty()) {
            _uiState.update { it.copy(dateGiven = today) }
        }
    }

    fun resetSaveState() {
        _uiState.update { it.copy(isSaved = false, savedVaccination = null) }
    }

    fun onPatientIdChange(id: String) { _uiState.update { it.copy(patientId = id) } }
    fun onNextBrandChange(brand: String) { _uiState.update { it.copy(nextBrandSearch = brand) } }
    fun onDateGivenChange(date: String) { _uiState.update { it.copy(dateGiven = date) } }
    fun onNextDueDateChange(date: String) { _uiState.update { it.copy(nextDueDate = date) } }
    fun onCostChange(amount: String) { _uiState.update { it.copy(cost = amount) } }
    fun onFeesToggle(enabled: Boolean) { _uiState.update { it.copy(withFees = enabled) } }
    fun onAccToggle(enabled: Boolean) { _uiState.update { it.copy(doctorsAcc = enabled) } }
    
    fun onCashChange(amount: String) {
        _uiState.update { state ->
            val cash = amount.toDoubleOrNull() ?: 0.0
            val online = state.onlineAmount.toDoubleOrNull() ?: 0.0
            state.copy(cashAmount = amount, totalPaid = cash + online)
        }
    }

    fun onOnlineChange(amount: String) {
        _uiState.update { state ->
            val online = amount.toDoubleOrNull() ?: 0.0
            val cash = state.cashAmount.toDoubleOrNull() ?: 0.0
            state.copy(onlineAmount = amount, totalPaid = cash + online)
        }
    }
}
