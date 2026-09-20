package com.neochildclinic.features.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.usecase.patient.GetPatientsUseCase
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.usecase.sync.RefreshDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaccineDetailEntry(
    val reminder: ReminderEntity,
    val patient: Patient?,
    val brandName: String
)

data class VaccineDetailUiState(
    val entries: List<VaccineDetailEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class VaccineDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    reminderRepository: ReminderRepository,
    getPatientsUseCase: GetPatientsUseCase,
    private val refreshDataUseCase: RefreshDataUseCase
) : ViewModel() {

    private val type: String = savedStateHandle["type"] ?: ""
    private val brandName: String = savedStateHandle["brandName"] ?: ""

    private val _isRefreshing = MutableStateFlow(false)
    private val _uiState = MutableStateFlow(VaccineDetailUiState())

    val uiState: StateFlow<VaccineDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                reminderRepository.getAllReminders(),
                getPatientsUseCase()
            ) { reminders, patients ->
                val patientMap = patients.associateBy { it.id }
                val active = reminders.filter {
                    it.status == "ACTIVE" &&
                        it.reminderEnabled &&
                        it.category.equals("VACCINATION", ignoreCase = true) &&
                        it.type.trim().equals(type, ignoreCase = false)
                }

                val entries = active.flatMap { reminder ->
                    val names = reminder.vaccineName.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val ids = reminder.nxtVaccineId
                    val verifiedNames = if (ids.isNullOrEmpty()) {
                        names
                    } else {
                        names.filterIndexed { index, _ -> ids.getOrNull(index) != null }
                    }
                    verifiedNames
                        .map { com.neochildclinic.core.utils.PatientUtils.cleanVaccineName(it) }
                        .filter { it.isNotBlank() && it.equals(brandName, ignoreCase = true) }
                        .distinct()
                        .map { VaccineDetailEntry(reminder, patientMap[reminder.patientId], it) }
                }.sortedBy { it.reminder.dueDate }

                VaccineDetailUiState(entries = entries, isLoading = false)
            }.flowOn(Dispatchers.Default)
                .collect { _uiState.value = it }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                refreshDataUseCase()
            } catch (_: Exception) {
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
