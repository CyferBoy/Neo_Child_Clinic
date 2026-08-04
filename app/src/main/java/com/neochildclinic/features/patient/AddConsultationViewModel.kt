package com.neochildclinic.features.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.core.constants.Constants
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class AddConsultationUiState(
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddConsultationViewModel @Inject constructor(
    private val consultationRepository: ConsultationRepository,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddConsultationUiState())
    val uiState: StateFlow<AddConsultationUiState> = _uiState.asStateFlow()

    fun saveConsultation(
        patientId: String,
        date: String,
        amount: Double,
        notes: String,
        nextFollowUpDate: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val consultation = Consultation(
                    id = UUID.randomUUID().toString(),
                    patientId = patientId,
                    date = date,
                    amount = amount,
                    notes = notes,
                    nextFollowUpDate = nextFollowUpDate
                )
                consultationRepository.addConsultation(consultation)
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
