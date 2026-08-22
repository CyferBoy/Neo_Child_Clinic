package com.neochildclinic.features.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Profile
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.domain.service.ClinicalVaccinationService
import com.neochildclinic.domain.service.ConsultationEditEngine
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
    private val consultationEditEngine: ConsultationEditEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddConsultationUiState())
    val uiState: StateFlow<AddConsultationUiState> = _uiState.asStateFlow()

    // Drives loadDoctors() alongside the profiles flow so an inactive doctor on the
    // consultation being edited is still included once the consultation has loaded -
    // reading _uiState.value.editingConsultation inside the profiles onEach doesn't work
    // because the profiles flow typically emits once, before loadForEdit() finishes.
    private val editingDoctorId = MutableStateFlow<String?>(null)

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
        _uiState.update { it.copy(selectedDoctor = doctor, doctorError = false) }
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

        val totalAmount = cash + online
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
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
