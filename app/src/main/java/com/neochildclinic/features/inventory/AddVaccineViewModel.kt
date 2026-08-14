package com.neochildclinic.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.data.local.entity.VaccineEntity
import com.neochildclinic.domain.repository.InventoryRepository
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class AddVaccineUiState(
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val vaccine: VaccineEntity? = null,
    val allTypes: List<String> = emptyList(),
    val brandSuggestions: Map<String, List<String>> = emptyMap() // Type -> List of Brands
)

@HiltViewModel
class AddVaccineViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddVaccineUiState())
    val uiState: StateFlow<AddVaccineUiState> = _uiState.asStateFlow()

    init {
        loadSuggestions()
    }

    private fun loadSuggestions() {
        viewModelScope.launch {
            inventoryRepository.getInventoryItems().collect { items ->
                val types = items.map { it.type }.distinct().sorted()
                val brands = items.groupBy { it.type }
                    .mapValues { entry -> entry.value.map { it.brandName }.distinct().sorted() }
                
                _uiState.update { it.copy(
                    allTypes = types,
                    brandSuggestions = brands
                ) }
            }
        }
    }

    fun loadVaccine(vaccineId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val items = inventoryRepository.getInventoryItems().first()
            val item = items.find { it.id == vaccineId }
            if (item != null) {
                val entity = VaccineEntity(
                    id = item.id,
                    type = item.type,
                    brandName = item.brandName,
                    companyName = item.company,
                    mrp = item.mrp,
                    netRate = item.netRate
                )
                _uiState.update { it.copy(vaccine = entity, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Vaccine not found") }
            }
        }
    }

    fun saveVaccine(
        id: String?,
        brandName: String,
        type: String,
        companyName: String,
        mrp: Double,
        netRate: Double
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                val vaccineId = id ?: UUID.randomUUID().toString()
                val vaccine = VaccineEntity(
                    id = vaccineId,
                    type = type,
                    brandName = brandName,
                    companyName = companyName,
                    mrp = mrp,
                    netRate = netRate
                )

                if (id != null) {
                    inventoryRepository.updateVaccine(vaccine, user)
                } else {
                    inventoryRepository.addVaccine(vaccine, user)
                }
                _uiState.update { it.copy(isSaved = true, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun resetState() {
        _uiState.update { it.copy(isSaved = false, error = null) }
    }
}
