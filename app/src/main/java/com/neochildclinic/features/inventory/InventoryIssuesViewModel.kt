package com.neochildclinic.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.data.local.dao.VaccinationDao
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.service.InventoryProcessingService
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryIssuesUiState(
    val pendingVisits: List<VisitEntity> = emptyList(),
    val inventoryItems: List<InventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InventoryIssuesViewModel @Inject constructor(
    private val vaccinationDao: VaccinationDao,
    private val inventoryRepository: InventoryRepository,
    private val inventoryService: InventoryProcessingService,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryIssuesUiState())
    val uiState: StateFlow<InventoryIssuesUiState> = _uiState.asStateFlow()

    init {
        observePendingIssues()
    }

    private fun observePendingIssues() {
        viewModelScope.launch {
            combine(
                vaccinationDao.getPendingInventoryVisits(),
                inventoryRepository.getInventoryItems(),
                _uiState
            ) { pending, items, state ->
                state.copy(
                    pendingVisits = pending,
                    inventoryItems = items,
                    isLoading = false
                )
            }.collect { updatedState ->
                _uiState.value = updatedState
            }
        }
    }

    fun retryDeduction(visit: VisitEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
            val error = inventoryService.retryDeduction(
                vaccinationId = visit.id,
                patientId = visit.patientId,
                vaccineIds = if (visit.vaccineIds.isBlank()) emptyList() else visit.vaccineIds.split(","),
                user = user
            )
            _uiState.update { it.copy(isLoading = false, error = error) }
        }
    }

    fun resolveManual(visit: VisitEntity, batchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
            val error = inventoryService.resolveManual(
                vaccinationId = visit.id,
                batchIds = listOf(batchId),
                user = user
            )
            _uiState.update { it.copy(isLoading = false, error = error) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
