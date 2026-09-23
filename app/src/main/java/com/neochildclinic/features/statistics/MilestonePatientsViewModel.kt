package com.neochildclinic.features.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.data.repository.PatientRepositoryImpl
import com.neochildclinic.domain.usecase.sync.RefreshDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MilestonePatientsViewModel @Inject constructor(
    patientRepository: PatientRepositoryImpl,
    private val refreshDataUseCase: RefreshDataUseCase
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val patients: StateFlow<List<Patient>> = patientRepository.allPatients
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                refreshDataUseCase()
            } catch (_: Exception) {
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}