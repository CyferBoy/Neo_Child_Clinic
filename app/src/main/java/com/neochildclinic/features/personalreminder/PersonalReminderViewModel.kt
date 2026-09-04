package com.neochildclinic.features.personalreminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.utils.DateCategory
import com.neochildclinic.core.utils.DateClassifier
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.PersonalReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PersonalReminderTab { ACTIVE, COMPLETED, CANCELLED }

data class PersonalReminderUiState(
    val selectedTab: PersonalReminderTab = PersonalReminderTab.ACTIVE,
    val active: List<PersonalReminderEntity> = emptyList(),
    val completed: List<PersonalReminderEntity> = emptyList(),
    val cancelled: List<PersonalReminderEntity> = emptyList(),
    val patientsById: Map<String, Patient> = emptyMap(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class PersonalReminderViewModel @Inject constructor(
    private val repository: PersonalReminderRepository,
    private val patientRepository: PatientRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(PersonalReminderTab.ACTIVE)
    private val _isRefreshing = MutableStateFlow(false)

    init {
        // Self-heal on open: the Room cache backing this screen is only otherwise
        // populated by RefreshDataUseCase (login/manual sync elsewhere) or by pull-
        // to-refresh on this screen itself - neither is guaranteed to have run yet
        // by the time someone opens Personal Reminders, so a reminder created on
        // another device (or before this screen was ever visited) could sit
        // invisible in the remote DB indefinitely. Kick off a background pull every
        // time this screen is opened so it never just shows an empty list forever.
        refresh()
    }

    val uiState: StateFlow<PersonalReminderUiState> = combine(
        _selectedTab,
        // Sort Active so items needing attention appear first: Overdue, then Today,
        // then Upcoming; ties broken by the reminder date itself.
        repository.getActiveReminders().map { list ->
            list.sortedWith(
                compareBy(
                    { reminderPriority(it.reminderDate) },
                    { it.reminderDate?.let(DateClassifier::getSortWeight) ?: Long.MAX_VALUE }
                )
            )
        },
        repository.getCompletedReminders(),
        repository.getCancelledReminders(),
        patientRepository.allPatients,
        _isRefreshing
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tab = values[0] as PersonalReminderTab
        @Suppress("UNCHECKED_CAST")
        val active = values[1] as List<PersonalReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val completed = values[2] as List<PersonalReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val cancelled = values[3] as List<PersonalReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val patients = values[4] as List<Patient>
        val refreshing = values[5] as Boolean

        PersonalReminderUiState(
            selectedTab = tab,
            active = active,
            completed = completed,
            cancelled = cancelled,
            patientsById = patients.associateBy { it.id },
            isLoading = false,
            isRefreshing = refreshing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PersonalReminderUiState())

    // 0 = Overdue (most attention), 1 = Today, 2 = Upcoming.
    private fun reminderPriority(reminderDate: String?): Int = when {
        reminderDate.isNullOrBlank() -> 3
        DateClassifier.classify(reminderDate) is DateCategory.Overdue -> 0
        DateClassifier.classify(reminderDate) is DateCategory.Today -> 1
        else -> 2
    }

    fun selectTab(tab: PersonalReminderTab) {
        _selectedTab.value = tab
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refresh()
            } catch (_: Exception) {
                // Best-effort refresh; local data remains authoritative offline.
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun markReady(id: String) = viewModelScope.launch { repository.markReady(id) }
    fun markPending(id: String) = viewModelScope.launch { repository.markPending(id) }
    fun markCompleted(id: String) = viewModelScope.launch { repository.markCompleted(id) }
    fun cancel(id: String) = viewModelScope.launch { repository.cancel(id) }
    fun delete(id: String) = viewModelScope.launch { repository.deleteReminder(id) }
}
