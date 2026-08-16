package com.neochildclinic.features.statistics

import androidx.lifecycle.ViewModel
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.usecase.vaccination.GetVaccinationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class FinanceDetailsViewModel @Inject constructor(
    financeRepository: FinanceRepository,
    getVaccinationsUseCase: GetVaccinationsUseCase
) : ViewModel() {
    val transactions: StateFlow<List<FinanceEntity>> = financeRepository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vaccinations: StateFlow<List<Vaccination>> = getVaccinationsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
