package com.neochildclinic.features.doctorslots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.DoctorWeeklySlot
import com.neochildclinic.domain.model.PredefinedSlots
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.model.SlotExceptionType
import com.neochildclinic.domain.model.TimeRange
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.repository.DoctorAvailabilityRepository
import com.neochildclinic.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeeklyDoctorSlotsUiState(
    val allDoctors: List<Profile> = emptyList(),
    val selectedDoctor: Profile? = null,
    val currentUserRole: UserRole? = null,
    // Admin: any doctor. Doctor: only themself, and the doctor picker is locked. Anyone
    // else (receptionist etc.): view-only, no Edit Slot button (req. 5/6/7/8/19).
    val canManageSelectedDoctor: Boolean = false,
    val isEditMode: Boolean = false,
    val weeklySlots: List<DoctorWeeklySlot> = emptyList(),
    val exceptions: List<DoctorSlotException> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class WeeklyDoctorSlotsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val repository: DoctorAvailabilityRepository,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyDoctorSlotsUiState())
    val uiState: StateFlow<WeeklyDoctorSlotsUiState> = _uiState.asStateFlow()

    private val selectedDoctorId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val currentUserId = auth.currentSessionOrNull()?.user?.id
            profileRepository.allProfiles.collect { profiles ->
                val me = profiles.find { it.id == currentUserId }
                val doctors = profiles.filter { it.role == UserRole.doctor && it.isActive }.sortedBy { it.displayName }

                val visibleDoctors = if (me?.role == UserRole.doctor) {
                    // req. 7: a doctor may only view/edit their own slots - the picker on
                    // this screen never offers another doctor's schedule to choose from.
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
                selectedDoctorId.value = defaultDoctor()
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

    private fun defaultDoctor(): String? = _uiState.value.selectedDoctor?.id

    fun selectDoctor(doctor: Profile) {
        val state = _uiState.value
        if (state.currentUserRole == UserRole.doctor && doctor.id != auth.currentSessionOrNull()?.user?.id) {
            return // req. 7: enforced here too, not just by hiding the picker.
        }
        _uiState.update {
            it.copy(
                selectedDoctor = doctor,
                canManageSelectedDoctor = it.currentUserRole == UserRole.admin || it.currentUserRole == UserRole.doctor,
                isEditMode = false
            )
        }
        selectedDoctorId.value = doctor.id
    }

    fun setEditMode(enabled: Boolean) {
        if (!_uiState.value.canManageSelectedDoctor) return
        _uiState.update { it.copy(isEditMode = enabled) }
    }

    /** Checkbox toggle for one predefined range on one weekday (req. 4). */
    fun toggleSlot(dayOfWeek: Int, range: TimeRange, enabled: Boolean) {
        val state = _uiState.value
        val doctor = state.selectedDoctor ?: return
        if (!state.canManageSelectedDoctor) return
        viewModelScope.launch {
            try {
                repository.setWeeklySlotEnabled(doctor.id, dayOfWeek, range, enabled, actor = currentActor())
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Unable to update slot.") }
            }
        }
    }

    fun addFullDayException(date: String, reason: String?) {
        addException(date, SlotExceptionType.FULL_DAY, null, reason)
    }

    fun addSlotException(date: String, weeklySlotId: String, reason: String?) {
        addException(date, SlotExceptionType.SLOT, weeklySlotId, reason)
    }

    private fun addException(date: String, type: SlotExceptionType, weeklySlotId: String?, reason: String?) {
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

    private fun currentActor(): String? = auth.currentSessionOrNull()?.user?.email

    companion object {
        val PREDEFINED_RANGES: List<TimeRange> = PredefinedSlots.ALL
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
