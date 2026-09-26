package com.neochildclinic.features.statistics

import androidx.lifecycle.ViewModel
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.domain.usecase.sync.RefreshDataUseCase
import com.neochildclinic.domain.repository.VaccinationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FinanceDetailsViewModel @Inject constructor(
    financeRepository: FinanceRepository,
    patientRepository: PatientRepository,
    vaccinationRepository: VaccinationRepository,
    private val refreshDataUseCase: RefreshDataUseCase
) : ViewModel() {

    private val transactionsFlow = financeRepository.getAllTransactions()
    private val patientsFlow = patientRepository.allPatients
    private val vaccinationsFlow = vaccinationRepository.allVaccinations

    val transactions: StateFlow<List<FinanceEntity>> = transactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val patients = patientsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vaccinations: StateFlow<List<Vaccination>> = vaccinationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            transactionsFlow.first()
            patientsFlow.first()
            vaccinationsFlow.first()
            _isLoading.value = false
        }
    }

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