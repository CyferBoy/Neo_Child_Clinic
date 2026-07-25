package com.clinic.neochild.features.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.data.local.entity.ReminderEntity
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.PendingRequirement
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.vaccination.CompleteVaccinationUseCase
import com.clinic.neochild.core.utils.PatientUtils
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class FollowUpUiState(
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FollowUpViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val completeVaccinationUseCase: CompleteVaccinationUseCase,
    private val getPatientsUseCase: GetPatientsUseCase,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowUpUiState())
    val uiState: StateFlow<FollowUpUiState> = _uiState.asStateFlow()

    private val currentUserEmail: String
        get() = auth.currentUser?.email ?: "Unknown Staff"

    fun getPatientFollowUps(patientId: String): Flow<List<ReminderEntity>> {
        return reminderRepository.getPatientFollowUps(patientId)
    }

    fun scheduleFollowUp(
        patientId: String,
        originalVisitId: String,
        vaccineNames: List<String>,
        dueDate: String,
        notes: String,
        priority: String,
        reminderEnabled: Boolean,
        onSuccess: () -> Unit
    ) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                reminderRepository.scheduleFollowUp(
                    patientId = patientId,
                    originalVisitId = originalVisitId,
                    vaccineNames = vaccineNames,
                    dueDate = dueDate,
                    notes = notes,
                    priority = priority,
                    reminderEnabled = reminderEnabled,
                    performedBy = currentUserEmail
                )
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    // Removed markAsDone background action to satisfy "no automatic entry" requirement.

    fun reschedule(reminder: ReminderEntity, newDate: String, reason: String) {
        viewModelScope.launch {
            reminderRepository.reschedule(reminder.toPendingRequirement(), newDate, newDate, reason, currentUserEmail)
        }
    }

    fun markVaccinatedElsewhere(reminder: ReminderEntity, hospitalName: String, date: String, notes: String) {
        viewModelScope.launch {
            reminderRepository.markVaccinatedElsewhere(reminder.toPendingRequirement(), hospitalName, date, notes, currentUserEmail)
        }
    }

    fun dismissReminder(reminder: ReminderEntity, reason: String) {
        viewModelScope.launch {
            reminderRepository.dismissReminder(reminder.toPendingRequirement(), reason, currentUserEmail)
        }
    }

    fun restoreReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderRepository.restoreReminder(reminder.toPendingRequirement(), currentUserEmail)
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderRepository.deleteReminder(reminder.toPendingRequirement(), currentUserEmail)
        }
    }

    private fun ReminderEntity.toPendingRequirement() = PendingRequirement(
        patientId = patientId,
        vaccineName = vaccineName,
        dueDate = PatientUtils.parseDate(dueDate) ?: Date(),
        originalVisitId = originalVisitId
    )
}
