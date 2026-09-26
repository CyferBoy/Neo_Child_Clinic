package com.neochildclinic.features.doctorslots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.DoctorWeeklySlot
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.SlotExceptionType
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.data.repository.DoctorAvailabilityRepositoryImpl
import com.neochildclinic.data.repository.ProfileRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import com.neochildclinic.core.session.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class WeeklyDoctorSlotsUiState(
    val allDoctors: List<Profile> = emptyList(),
    val selectedDoctor: Profile? = null,
    val currentUserRole: UserRole? = null,
    val canManageSelectedDoctor: Boolean = false,
    val selectedTab: Int = 0,
    val isWeeklySlotsEditMode: Boolean = false,
    val weeklySlots: List<DoctorWeeklySlot> = emptyList(),
    val exceptions: List<DoctorSlotException> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class WeeklyDoctorSlotsViewModel @Inject constructor(
    private val profileRepository: ProfileRepositoryImpl,
    private val repository: DoctorAvailabilityRepositoryImpl,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyDoctorSlotsUiState())
    val uiState: StateFlow<WeeklyDoctorSlotsUiState> = _uiState.asStateFlow()

    private val selectedDoctorId = MutableStateFlow<String?>(null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        if (_isRefreshing.value || _uiState.value.isLoading) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refresh()
                profileRepository.refreshProfiles()
            } catch (_: Exception) {
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    init {
        viewModelScope.launch {
            val currentUserId = sessionManager.getCurrentUserId()
            profileRepository.allProfiles.collect { profiles ->
                val me = profiles.find { it.id == currentUserId }
                val doctors = profiles.filter { it.role == UserRole.doctor && it.isActive }.sortedBy { it.displayName }

                val visibleDoctors = if (me?.role == UserRole.doctor) {
                    doctors.filter { it.id == currentUserId }
                } else {
                    doctors
                }

                _uiState.update { state ->
                    val stillValid = state.selectedDoctor?.let { sel -> visibleDoctors.any { it.id == sel.id } } == true
                    val defaultDoctor = if (stillValid) state.selectedDoctor else visibleDoctors.firstOrNull()
                    state.copy(
                        allDoctors = visibleDoctors,
                        currentUserRole = me?.role,
                        selectedDoctor = defaultDoctor,
                        canManageSelectedDoctor = me?.role == UserRole.admin ||
                            (me?.role == UserRole.doctor && defaultDoctor?.id == currentUserId),
                        isLoading = false
                    )
                }
                selectedDoctorId.value = defaultDoctor()?.id
            }
        }

        selectedDoctorId
            .filterNotNull()
            .flatMapLatest { doctorId -> repository.getWeeklySlots(doctorId) }
            .onEach { slots -> _uiState.update { it.copy(weeklySlots = slots) } }
            .launchIn(viewModelScope)

        selectedDoctorId
            .filterNotNull()
            .flatMapLatest { doctorId -> repository.getExceptions(doctorId) }
            .onEach { exceptions -> _uiState.update { it.copy(exceptions = exceptions) } }
            .launchIn(viewModelScope)
    }

    private fun defaultDoctor(): Profile? = _uiState.value.selectedDoctor

    fun selectDoctor(doctor: Profile) {
        val state = _uiState.value
        if (state.currentUserRole == UserRole.doctor && doctor.id != sessionManager.getCurrentUserId()) {
            return
        }
        _uiState.update {
            it.copy(
                selectedDoctor = doctor,
                canManageSelectedDoctor = it.currentUserRole == UserRole.admin || it.currentUserRole == UserRole.doctor,
                isWeeklySlotsEditMode = false
            )
        }
        selectedDoctorId.value = doctor.id
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab, isWeeklySlotsEditMode = false) }
    }

    fun setWeeklySlotsEditMode(enabled: Boolean) {
        if (!_uiState.value.canManageSelectedDoctor) return
        _uiState.update { it.copy(isWeeklySlotsEditMode = enabled) }
    }

    fun addWeeklySlot(dayOfWeek: Int, startMinute: Int, endMinute: Int) {
        val state = _uiState.value
        val doctor = state.selectedDoctor ?: return
        if (!state.canManageSelectedDoctor) return
        viewModelScope.launch {
            try {
                repository.addWeeklySlot(doctor.id, dayOfWeek, startMinute, endMinute, actor = currentActor())
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Unable to add slot.") }
            }
        }
    }

    fun removeWeeklySlot(slotId: String) {
        if (!_uiState.value.canManageSelectedDoctor) return
        viewModelScope.launch {
            try {
                repository.removeWeeklySlot(slotId, actor = currentActor())
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Unable to remove slot.") }
            }
        }
    }

    fun addFullDayException(date: String, reason: String?) {
        addException(date, SlotExceptionType.FULL_DAY, null, null, null, reason)
    }

    fun addCustomTimeException(date: String, startMinute: Int, endMinute: Int, reason: String?) {
        addException(date, SlotExceptionType.SLOT, null, startMinute, endMinute, reason)
    }

    fun addSlotException(date: String, weeklySlotId: String, reason: String?) {
        addException(date, SlotExceptionType.SLOT, weeklySlotId, null, null, reason)
    }

    private fun addException(
        date: String,
        type: SlotExceptionType,
        weeklySlotId: String?,
        startMinute: Int?,
        endMinute: Int?,
        reason: String?
    ) {
        val state = _uiState.value
        val doctor = state.selectedDoctor ?: return
        if (!state.canManageSelectedDoctor) return
        viewModelScope.launch {
            try {
                repository.addException(
                    DoctorSlotException(
                        doctorId = doctor.id,
                        exceptionDate = date,
                        exceptionType = type,
                        weeklySlotId = weeklySlotId,
                        startMinute = startMinute,
                        endMinute = endMinute,
                        reason = reason?.ifBlank { null }
                    ),
                    actor = currentActor()
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Unable to add exception.") }
            }
        }
    }

    fun deleteException(id: String) {
        if (!_uiState.value.canManageSelectedDoctor) return
        viewModelScope.launch {
            try {
                repository.deleteException(id, actor = currentActor())
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Unable to delete exception.") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun currentActor(): String? = sessionManager.getCurrentUserEmail().takeIf { it != "Unknown" }

    companion object {
        val WEEKDAYS: List<Pair<Int, String>> = listOf(
            java.util.Calendar.MONDAY to "Monday",
            java.util.Calendar.TUESDAY to "Tuesday",
            java.util.Calendar.WEDNESDAY to "Wednesday",
            java.util.Calendar.THURSDAY to "Thursday",
            java.util.Calendar.FRIDAY to "Friday",
            java.util.Calendar.SATURDAY to "Saturday",
            java.util.Calendar.SUNDAY to "Sunday"
        )
    }
}
