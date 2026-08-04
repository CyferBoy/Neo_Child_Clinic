package com.neochildclinic.features.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.*
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.usecase.patient.GetPatientsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompletedDismissedUiState(
    val patients: List<Patient> = emptyList(),
    val completedRecords: List<CompletedDueRecord> = emptyList(),
    val dismissedRecords: List<DismissedDueRecord> = emptyList(),
    val otherRecords: List<OtherEstablishmentDueRecord> = emptyList(),
    val processedVaccinations: List<Vaccination> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class CompletedDismissedViewModel @Inject constructor(
    private val getPatientsUseCase: GetPatientsUseCase,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<CompletedDismissedUiState> = combine(
        getPatientsUseCase(),
        reminderRepository.getCompletedDueRecords(),
        reminderRepository.getDismissedDueRecords(),
        reminderRepository.getOtherEstablishmentDueRecords(),
        reminderRepository.getDueList("", listOf(ReminderStatus.COMPLETED, ReminderStatus.DISMISSED, ReminderStatus.EXTERNAL)),
        _isRefreshing
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val patients = args[0] as List<Patient>
        @Suppress("UNCHECKED_CAST")
        val completed = args[1] as List<CompletedDueRecord>
        @Suppress("UNCHECKED_CAST")
        val dismissed = args[2] as List<DismissedDueRecord>
        @Suppress("UNCHECKED_CAST")
        val other = args[3] as List<OtherEstablishmentDueRecord>
        @Suppress("UNCHECKED_CAST")
        val processed = args[4] as List<Vaccination>
        val refreshing = args[5] as Boolean

        CompletedDismissedUiState(
            patients = patients,
            completedRecords = completed.sortedByDescending { "${it.completedDate} ${it.completedTime}" },
            dismissedRecords = dismissed.sortedByDescending { "${it.dismissedDate} ${it.dismissedTime}" },
            otherRecords = other.sortedByDescending { "${it.recordedDate} ${it.recordedTime}" },
            processedVaccinations = processed,
            isLoading = false,
            isRefreshing = refreshing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompletedDismissedUiState(isLoading = true))

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                reminderRepository.refreshReminders()
            } catch (_: Exception) { }
            finally {
                _isRefreshing.value = false
            }
        }
    }
}
