package com.neochildclinic.features.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.ReminderStats
import com.neochildclinic.domain.usecase.patient.GetPatientsUseCase
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.core.utils.DateClassifier
import com.neochildclinic.core.utils.DateCategory
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val reminderRepository: ReminderRepository,
    private val auth: Auth
) : ViewModel() {

    val stats = reminderRepository.getDashboardStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderStats())

    private val _selectedFilter = MutableStateFlow("Today")
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)

    private val currentUserEmail: String
        get() = auth.currentSessionOrNull()?.user?.email ?: "Unknown Staff"

    val uiState: StateFlow<DueUiState> = combine(
        getPatientsUseCase(),
        _searchQuery,
        _selectedFilter,
        _isRefreshing
    ) { patients, query, filter, refreshing ->
        
        val filterStatus = when (filter) {
            "Completed" -> listOf(ReminderStatus.COMPLETED)
            "Dismissed" -> listOf(ReminderStatus.DISMISSED)
            else -> listOf(ReminderStatus.ACTIVE)
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
                     else reminderRepository.getDueList("", listOf(ReminderStatus.ACTIVE)).first()

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

    private suspend fun remindersFor(vaccination: Vaccination): List<com.neochildclinic.data.local.entity.ReminderEntity> {
        val reminders = reminderRepository.getRemindersByVisitId(vaccination.id)
        if (vaccination.nextVaccinations.isEmpty()) return reminders
        val ids = vaccination.nextVaccinations.map { it.reminderId }.filter { it.isNotBlank() }.toSet()
        return if (ids.isNotEmpty()) reminders.filter { it.id in ids } else reminders
    }

    fun dismissReminder(vaccination: Vaccination, reason: String) {
        viewModelScope.launch {
            remindersFor(vaccination).forEach { reminder ->
                reminderRepository.dismissReminder(reminder, reason, currentUserEmail)
            }
        }
    }

    fun rescheduleVaccination(vaccination: Vaccination, newDate: String, reminderDate: String, reason: String) {
        viewModelScope.launch {
            remindersFor(vaccination).forEach { reminder ->
                reminderRepository.reschedule(reminder, newDate, reminderDate, reason, currentUserEmail)
            }
        }
    }

    fun restoreReminder(vaccination: Vaccination) {
        viewModelScope.launch {
            remindersFor(vaccination).forEach { reminder ->
                reminderRepository.restoreReminder(reminder, currentUserEmail)
            }
        }
    }

}
