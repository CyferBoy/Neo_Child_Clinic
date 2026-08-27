package com.neochildclinic.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.BatchStatus
import com.neochildclinic.domain.repository.InventoryRepository
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class AddBatchUiState(
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val batch: VaccineBatchEntity? = null,
    val defaultBatch: VaccineBatchEntity? = null,
    val defaultMrp: Double? = null,
    val defaultNetRate: Double? = null,
    val defaultManufacturer: String? = null
)

@HiltViewModel
class AddBatchViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBatchUiState())
    val uiState: StateFlow<AddBatchUiState> = _uiState.asStateFlow()

    fun loadBatch(batchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val batch = inventoryRepository.getBatchById(batchId)
            if (batch != null) {
                _uiState.update { it.copy(batch = batch, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Batch not found") }
            }
        }
    }

    fun loadDefaults(vaccineId: String) {
        viewModelScope.launch {
            // Prefer the latest existing batch's pricing/manufacturer; fall back to the
            // vaccine's own defaults (e.g. for a vaccine's very first batch).
            val vaccine = inventoryRepository.getVaccineById(vaccineId)
            val latest = inventoryRepository.getVaccineBatches(vaccineId).firstOrNull()
                ?.let { batches -> batches.maxByOrNull { it.purchaseDate } ?: batches.maxByOrNull { it.expiryDate } }

            _uiState.update {
                it.copy(
                    defaultBatch = latest,
                    defaultMrp = latest?.sellingPrice ?: vaccine?.mrp,
                    defaultNetRate = latest?.purchaseCost ?: vaccine?.netRate,
                    defaultManufacturer = latest?.manufacturer ?: vaccine?.manufacturer
                )
            }
        }
    }

    fun saveBatch(
        batchId: String?,
        vaccineId: String,
        batchNumber: String,
        quantity: Int,
        expiryDate: String,
        mrp: Double,
        netRate: Double,
        manufacturer: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                
                if (batchId != null) {
                    val existing = _uiState.value.batch ?: throw IllegalStateException("Batch not loaded")
                    val updated = existing.copy(
                        batchNumber = batchNumber,
                        remainingQuantity = quantity,
                        expiryDate = expiryDate,
                        sellingPrice = mrp,
                        purchaseCost = netRate,
                        manufacturer = manufacturer
                    )
                    inventoryRepository.updateBatch(updated, user)
                } else {
                    val newBatchId = UUID.randomUUID().toString()
                    val today = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
                    val batch = VaccineBatchEntity(
                        batchId = newBatchId,
                        vaccineId = vaccineId,
                        batchNumber = batchNumber,
                        manufacturer = manufacturer,
                        purchaseDate = today,
                        expiryDate = expiryDate,
                        purchaseQuantity = quantity,
                        remainingQuantity = quantity,
                        supplier = "Manual Entry",
                        purchaseCost = netRate,
                        sellingPrice = mrp,
                        status = BatchStatus.ACTIVE.name
                    )
                    inventoryRepository.addBatch(batch, user)
                }

                // If this batch's price differs from the vaccine's current default price,
                // that new price becomes the vaccine's default going forward.
                val vaccine = inventoryRepository.getVaccineById(vaccineId)
                if (vaccine != null && (vaccine.mrp != mrp || vaccine.netRate != netRate)) {
                    inventoryRepository.updateVaccine(
                        vaccine.copy(mrp = mrp, netRate = netRate),
                        user
                    )
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
