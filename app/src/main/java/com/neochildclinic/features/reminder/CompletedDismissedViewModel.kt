package com.neochildclinic.features.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.*
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.usecase.patient.GetPatientsUseCase
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompletedDismissedUiState(
    val patients: List<Patient> = emptyList(),
    val processedVaccinations: List<Vaccination> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class CompletedDismissedViewModel @Inject constructor(
    private val getPatientsUseCase: GetPatientsUseCase,
    private val reminderRepository: ReminderRepository,
    private val auth: Auth
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<CompletedDismissedUiState> = combine(
        getPatientsUseCase(),
        reminderRepository.getDueList("", listOf(ReminderStatus.COMPLETED, ReminderStatus.DISMISSED)),
        _isRefreshing
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val patients = args[0] as List<Patient>
        @Suppress("UNCHECKED_CAST")
        val processed = args[1] as List<Vaccination>
        val refreshing = args[2] as Boolean

        CompletedDismissedUiState(
            patients = patients,
            processedVaccinations = processed,
            isLoading = false,
            isRefreshing = refreshing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompletedDismissedUiState(isLoading = true))

    fun restoreReminders(vaccination: Vaccination) {
        viewModelScope.launch {
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                vaccination.nextVaccinations.forEach { summary ->
                    val reminder = reminderRepository.getReminderById(summary.reminderId)
                    if (reminder != null) {
                        reminderRepository.restoreReminder(reminder, user)
                    }
                }
            } catch (_: Exception) { }
        }
    }

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
