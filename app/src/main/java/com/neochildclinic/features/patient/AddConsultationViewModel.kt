package com.neochildclinic.features.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.ui.SlotsUiState
import com.neochildclinic.core.ui.loadUiState
import com.neochildclinic.domain.model.AvailableSlot
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.domain.service.ClinicalVaccinationService
import com.neochildclinic.domain.service.ConsultationEditEngine
import com.neochildclinic.domain.usecase.doctor.GetAvailableSlotsUseCase
import com.neochildclinic.core.utils.PatientUtils
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class AddConsultationUiState(
    val patient: Patient? = null,
    val allDoctors: List<Profile> = emptyList(),
    val selectedDoctor: Profile? = null,
    val editingConsultation: Consultation? = null,
    val doctorError: Boolean = false,
    val slotsState: SlotsUiState = SlotsUiState.Idle,
    val selectedSlot: AvailableSlot? = null,
    val slotError: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddConsultationViewModel @Inject constructor(
    private val clinicalService: ClinicalVaccinationService,
    private val patientRepository: PatientRepository,
    private val profileRepository: ProfileRepository,
    private val auth: Auth,
    private val consultationRepository: ConsultationRepository,
    private val consultationEditEngine: ConsultationEditEngine,
    private val getAvailableSlotsUseCase: GetAvailableSlotsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddConsultationUiState())
    val uiState: StateFlow<AddConsultationUiState> = _uiState.asStateFlow()

    // Drives loadDoctors() alongside the profiles flow so an inactive doctor on the
    // consultation being edited is still included once the consultation has loaded -
    // reading _uiState.value.editingConsultation inside the profiles onEach doesn't work
    // because the profiles flow typically emits once, before loadForEdit() finishes.
    private val editingDoctorId = MutableStateFlow<String?>(null)

    // Guards against a stale async slot-load response (from the doctor/date combo that
    // was selected a moment ago) overwriting the state for the combo selected right after
    // it - only the most recently requested load is allowed to update slotsState.
    private var slotLoadToken = 0

    init {
        loadDoctors()
    }

    fun loadForEdit(consultationId: String) {
        viewModelScope.launch {
            val consultation = consultationRepository.getConsultationById(consultationId) ?: return@launch
            val patient = patientRepository.getPatientById(consultation.patientId)
            _uiState.update { it.copy(patient = patient, editingConsultation = consultation) }
            if (consultation.doctorId.isNotBlank()) {
                editingDoctorId.value = consultation.doctorId
                kotlinx.coroutines.withTimeoutOrNull(5000) {
                    uiState.filter { state ->
                        state.allDoctors.any { it.employeeId == consultation.doctorId || it.id == consultation.doctorId }
                    }.first()
                }
            }
        }
    }

    fun loadPatient(patientId: String) {
        viewModelScope.launch {
            val patient = patientRepository.getPatientById(patientId)
            _uiState.update { it.copy(patient = patient) }
        }
    }

    private fun loadDoctors() {
        combine(profileRepository.allProfiles, editingDoctorId) { profiles, editId -> profiles to editId }
            .onEach { (profiles, editId) ->
                val doctors = profiles.filter {
                    it.role == com.neochildclinic.domain.model.UserRole.doctor &&
                        (it.isActive || (!editId.isNullOrBlank() &&
                            (it.employeeId == editId || it.id == editId)))
                }.sortedBy { it.displayName }

                val currentUserId = auth.currentSessionOrNull()?.user?.id
                val currentUserProfile = profiles.find { it.id == currentUserId }
                val defaultDoctor = if (currentUserProfile?.role == com.neochildclinic.domain.model.UserRole.doctor) currentUserProfile else null

                _uiState.update { state ->
                    val editDoctor = editId?.let { id -> doctors.firstOrNull { it.employeeId == id || it.id == id } }
                    state.copy(
                        allDoctors = doctors,
                        selectedDoctor = editDoctor ?: if (state.selectedDoctor == null) defaultDoctor else state.selectedDoctor
                    )
                }
            }.launchIn(viewModelScope)
    }

    fun selectDoctor(doctor: Profile) {
        // Changing the doctor invalidates whatever slot was selected for the previous
        // doctor (req. 1: "never allow a slot belonging to the previous doctor to remain
        // selected"). The caller is expected to follow up with loadAvailableSlots(date)
        // for the currently entered date.
        _uiState.update { it.copy(selectedDoctor = doctor, doctorError = false, selectedSlot = null, slotError = false) }
    }

    /** Recomputes available slots for the currently selected doctor + [date] (req. 1/22). */
    fun loadAvailableSlots(date: String) {
        val doctor = _uiState.value.selectedDoctor
        if (doctor == null || date.isBlank()) {
            _uiState.update { it.copy(slotsState = SlotsUiState.Idle, selectedSlot = null) }
            return
        }
        val token = ++slotLoadToken
        _uiState.update { it.copy(slotsState = SlotsUiState.Loading, selectedSlot = null) }
        viewModelScope.launch {
            // Availability rows are keyed by profiles.id (the auth uid), same as
            // doctor_weekly_slots.doctor_id / RLS's auth.uid() check - not by
            // employeeId, which is what consultations.doctorId stores for
            // attribution/display and may differ.
            val result = getAvailableSlotsUseCase.loadUiState(doctor.id, date)
            if (token != slotLoadToken) return@launch // superseded by a newer request
            _uiState.update { current ->
                val preselect = current.editingConsultation?.availabilitySlotId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { slotId -> (result as? SlotsUiState.Loaded)?.slots?.firstOrNull { it.weeklySlotId == slotId } }
                current.copy(slotsState = result, selectedSlot = preselect ?: current.selectedSlot)
            }
        }
    }

    fun selectSlot(slot: AvailableSlot) {
        _uiState.update { it.copy(selectedSlot = slot, slotError = false) }
    }

    fun saveConsultation(
        patientId: String,
        date: String,
        cash: Double,
        online: Double,
        problem: String,
        nextFollowUpDate: String
    ) {
        val state = _uiState.value
        if (state.selectedDoctor == null) {
            _uiState.update { it.copy(doctorError = true, error = "Please select a doctor.") }
            return
        }
        if (state.selectedSlot == null) {
            _uiState.update { it.copy(slotError = true, error = "Please select an available slot.") }
            return
        }

        val totalAmount = cash + online
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Revalidate (req. 17): another device may have changed this doctor's
                // availability (a new date exception, or the weekly slot being toggled
                // off) in the time between loading slots and tapping Save.
                val stillAvailable = getAvailableSlotsUseCase.isSlotStillAvailable(
                    state.selectedDoctor.id, date, state.selectedSlot.weeklySlotId
                )
                if (!stillAvailable) {
                    _uiState.update { it.copy(isLoading = false, slotError = true, selectedSlot = null) }
                    loadAvailableSlots(date)
                    _uiState.update { it.copy(error = "That slot is no longer available. Please choose another.") }
                    return@launch
                }

                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                val original = state.editingConsultation

                if (original != null) {
                    // EDIT: preserve the existing consultation/visit identity and creation time.
                    // Never take patientId from the caller here - the Edit Consultation route
                    // (edit_consultation/{consultationId}) has no patientId in its path, so
                    // Navigation always passes an empty string. The correct patientId is the one
                    // already on the persisted record (there's no patient-picker on this screen).
                    // The edit engine compares the persisted state with the new state and
                    // applies only the changes that actually occurred.
                    val updated = original.copy(
                        doctorId = state.selectedDoctor.employeeId ?: state.selectedDoctor.id,
                        doctorName = state.selectedDoctor.displayName,
                        availabilitySlotId = state.selectedSlot.weeklySlotId,
                        date = date,
                        amount = totalAmount,
                        cashAmount = cash,
                        onlineAmount = online,
                        problem = problem,
                        nextFollowUpDate = nextFollowUpDate
                    )

                    val result = consultationEditEngine.edit(
                        original = original,
                        updated = updated,
                        user = user
                    )
                    if (result == ConsultationEditEngine.Result.NO_CHANGES) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSaved = false,
                                error = "No changes to save."
                            )
                        }
                        return@launch
                    }
                } else {
                    // CREATE: keep the existing new-consultation flow.
                    val consultation = Consultation(
                        id = UUID.randomUUID().toString(),
                        patientId = patientId,
                        createdAt = PatientUtils.getCurrentIsoTimestamp(),
                        doctorId = state.selectedDoctor.employeeId ?: state.selectedDoctor.id,
                        doctorName = state.selectedDoctor.displayName,
                        availabilitySlotId = state.selectedSlot.weeklySlotId,
                        date = date,
                        amount = totalAmount,
                        cashAmount = cash,
                        onlineAmount = online,
                        problem = problem,
                        nextFollowUpDate = nextFollowUpDate
                    )

                    clinicalService.recordConsultation(consultation, user)
                }

                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Unable to save consultation.") }
            }
        }
    }

    fun resetState() {
        _uiState.update { it.copy(isSaved = false, error = null) }
    }
}
