package com.clinic.neochild.features.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clinic.neochild.domain.model.Patient
import com.clinic.neochild.domain.model.ReminderStatus
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.domain.model.VaccinationSource
import com.clinic.neochild.domain.model.PendingRequirement
import com.clinic.neochild.domain.repository.ReminderRepository
import com.clinic.neochild.domain.repository.ReminderStats
import com.clinic.neochild.domain.usecase.patient.GetPatientsUseCase
import com.clinic.neochild.domain.usecase.vaccination.CompleteVaccinationUseCase
import com.clinic.neochild.core.utils.PatientUtils
import com.clinic.neochild.core.utils.DateClassifier
import com.clinic.neochild.core.utils.DateCategory
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class DueUiState(
    val patients: List<Patient> = emptyList(),
    val vaccinations: List<Vaccination> = emptyList(),
    val filteredVaccinations: List<Vaccination> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFilter: String = "Today",
    val overdueCount: Int = 0,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class DueViewModel @Inject constructor(
    private val getPatientsUseCase: GetPatientsUseCase,
    private val completeVaccinationUseCase: CompleteVaccinationUseCase,
    private val reminderRepository: ReminderRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    val stats = reminderRepository.getDashboardStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderStats())

    private val _selectedFilter = MutableStateFlow("Today")
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    private val currentUserEmail: String
        get() = auth.currentUser?.email ?: "Unknown Staff"

    val uiState: StateFlow<DueUiState> = combine(
        getPatientsUseCase(),
        _searchQuery,
        _selectedFilter,
        _isRefreshing
    ) { patients, query, filter, refreshing ->
        
        val filterStatus = when (filter) {
            "Completed" -> listOf(ReminderStatus.COMPLETED)
            "Dismissed" -> listOf(ReminderStatus.DISMISSED)
            "Vaccinated Elsewhere" -> listOf(ReminderStatus.EXTERNAL)
            else -> listOf(ReminderStatus.ACTIVE, ReminderStatus.RESCHEDULED)
        }

        val processedVaccinations = reminderRepository.getDueList(query, filterStatus).first()
        
        var filtered = when (filter) {
            "Today", "Tomorrow", "This Week", "Upcoming", "Overdue", "All" -> {
                PatientUtils.filterVaccinationsByPeriod(processedVaccinations, filter)
            }
            "Week" -> PatientUtils.filterVaccinationsByPeriod(processedVaccinations, "This Week")
            else -> processedVaccinations
        }

        // Apply custom sorting for Overdue (latest first as per request)
        if (filter == "Overdue") {
            filtered = filtered.sortedByDescending { PatientUtils.parseDate(it.nextDueDate)?.time ?: 0L }
        }
        
        // Count overall overdue from the "Active/Due" pool for the badge, regardless of current tab
        val allDue = if (filterStatus.contains(ReminderStatus.ACTIVE)) processedVaccinations 
                     else reminderRepository.getDueList("", listOf(ReminderStatus.ACTIVE, ReminderStatus.RESCHEDULED)).first()

        val overdue = allDue.count { 
            val cat = DateClassifier.classify(it.nextDueDate)
            cat is DateCategory.Overdue || cat is DateCategory.Yesterday
        }

        DueUiState(
            patients = patients,
            vaccinations = processedVaccinations,
            filteredVaccinations = filtered,
            isLoading = false,
            selectedFilter = filter,
            overdueCount = overdue,
            isRefreshing = refreshing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DueUiState(isLoading = true))

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

    fun updateFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun dismissReminder(vaccination: Vaccination, reason: String) {
        viewModelScope.launch {
            val dueDate = PatientUtils.parseDate(vaccination.nextDueDate) ?: return@launch
            vaccination.nxtVaccineNames.forEach { name ->
                val req = PendingRequirement(
                    patientId = vaccination.patientId,
                    vaccineName = name,
                    dueDate = dueDate,
                    originalVisitId = vaccination.id
                )
                reminderRepository.dismissReminder(req, reason, currentUserEmail)
            }
        }
    }

    fun rescheduleVaccination(vaccination: Vaccination, newDate: String, reminderDate: String, reason: String) {
        viewModelScope.launch {
            val dueDate = PatientUtils.parseDate(vaccination.nextDueDate) ?: return@launch
            vaccination.nxtVaccineNames.forEach { name ->
                val req = PendingRequirement(
                    patientId = vaccination.patientId,
                    vaccineName = name,
                    dueDate = dueDate,
                    originalVisitId = vaccination.id
                )
                reminderRepository.reschedule(req, newDate, reminderDate, reason, currentUserEmail)
            }
        }
    }

    fun markVaccinatedElsewhere(
        vaccination: Vaccination,
        hospitalName: String,
        date: String,
        notes: String
    ) {
        viewModelScope.launch {
            val dueDate = PatientUtils.parseDate(vaccination.nextDueDate) ?: return@launch
            vaccination.nxtVaccineNames.forEach { name ->
                val req = PendingRequirement(
                    patientId = vaccination.patientId,
                    vaccineName = name,
                    dueDate = dueDate,
                    originalVisitId = vaccination.id
                )
                reminderRepository.markVaccinatedElsewhere(req, hospitalName, date, notes, currentUserEmail)
            }
        }
    }

    fun restoreReminder(vaccination: Vaccination) {
        viewModelScope.launch {
            val dueDate = PatientUtils.parseDate(vaccination.nextDueDate) ?: return@launch
            vaccination.nxtVaccineNames.forEach { name ->
                val req = PendingRequirement(
                    patientId = vaccination.patientId,
                    vaccineName = name,
                    dueDate = dueDate,
                    originalVisitId = vaccination.id
                )
                reminderRepository.restoreReminder(req, currentUserEmail)
            }
        }
    }

}
