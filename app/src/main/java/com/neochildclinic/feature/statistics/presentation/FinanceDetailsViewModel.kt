package com.neochildclinic.feature.statistics.presentation

import androidx.lifecycle.ViewModel
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.ExpenseRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.core.sync.RefreshDataUseCase
import com.neochildclinic.domain.repository.VaccinationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FinanceDetailsViewModel @Inject constructor(
    financeRepository: FinanceRepository,
    patientRepository: PatientRepository,
    vaccinationRepository: VaccinationRepository,
    expenseRepository: ExpenseRepository,
    private val refreshDataUseCase: RefreshDataUseCase
) : ViewModel() {

    private val transactionsFlow = financeRepository.getAllTransactions()
    private val patientsFlow = patientRepository.allPatients
    private val vaccinationsFlow = vaccinationRepository.allVaccinations
    private val expensesFlow = expenseRepository.getAllExpenses()

    val transactions: StateFlow<List<FinanceTransaction>> = transactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val patients = patientsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vaccinations: StateFlow<List<Vaccination>> = vaccinationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenses: StateFlow<List<Expense>> = expensesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                transactionsFlow.first()
                patientsFlow.first()
                vaccinationsFlow.first()
                expensesFlow.first()
            } finally {
                // Without this the spinner never clears if any Room flow throws (NCC-016).
                _isLoading.update { false }
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.update { true }
            try {
                refreshDataUseCase()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                android.util.Log.e("FinanceDetailsVM", "Refresh failed", e)
            } finally {
                _isRefreshing.update { false }
            }
        }
    }
}