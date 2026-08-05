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
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class AddConsultationUiState(
    val patient: Patient? = null,
    val doctorProfile: Profile? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddConsultationViewModel @Inject constructor(
    private val clinicalService: ClinicalVaccinationService,
    private val patientRepository: PatientRepository,
    private val profileRepository: ProfileRepository,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddConsultationUiState())
    val uiState: StateFlow<AddConsultationUiState> = _uiState.asStateFlow()

    init {
        loadDoctorProfile()
    }

    fun loadPatient(patientId: String) {
        viewModelScope.launch {
            val patient = patientRepository.getPatientById(patientId)
            _uiState.update { it.copy(patient = patient) }
        }
    }

    private fun loadDoctorProfile() {
        val userId = auth.currentSessionOrNull()?.user?.id ?: return
        viewModelScope.launch {
            val profile = profileRepository.getProfileById(userId)
            _uiState.update { it.copy(doctorProfile = profile) }
        }
    }

    fun saveConsultation(
        patientId: String,
        doctorName: String,
        date: String,
        cash: Double,
        online: Double,
        problem: String,
        nextFollowUpDate: String
    ) {
        val totalAmount = cash + online
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                val doctorId = auth.currentSessionOrNull()?.user?.id ?: ""
                
                val consultation = Consultation(
                    id = UUID.randomUUID().toString(),
                    patientId = patientId,
                    doctorId = doctorId,
                    doctorName = doctorName,
                    date = date,
                    amount = totalAmount,
                    cashAmount = cash,
                    onlineAmount = online,
                    problem = problem,
                    nextFollowUpDate = nextFollowUpDate
                )
                
                clinicalService.recordConsultation(consultation, user)
                
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun resetState() {
        _uiState.update { it.copy(isSaved = false, error = null) }
    }
}
