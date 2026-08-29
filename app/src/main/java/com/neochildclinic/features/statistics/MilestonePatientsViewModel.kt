package com.neochildclinic.features.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.usecase.patient.GetPatientsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MilestonePatientsViewModel @Inject constructor(
    getPatientsUseCase: GetPatientsUseCase
) : ViewModel() {
    val patients: StateFlow<List<Patient>> = getPatientsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
