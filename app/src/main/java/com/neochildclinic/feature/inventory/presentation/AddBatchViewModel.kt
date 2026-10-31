package com.neochildclinic.feature.inventory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.common.Constants
import com.neochildclinic.feature.inventory.data.newestBatch
import com.neochildclinic.domain.model.VaccineBatch
import com.neochildclinic.domain.model.BatchStatus
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.core.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

data class AddBatchUiState(
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val batch: VaccineBatch? = null,
    val defaultBatch: VaccineBatch? = null,
    val defaultMrp: Double? = null,
    val defaultNetRate: Double? = null,
    val defaultManufacturer: String? = null
)

@HiltViewModel
class AddBatchViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val sessionManager: SessionManager
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
                // Parsed, not string-max: the stored date format is not zero-padded, so a
                // textual max picked the OLDEST batch (NCC-040).
                ?.let { batches ->
                    batches.newestBatch()
                        ?: batches.maxByOrNull { com.neochildclinic.core.common.PatientUtils.parseDate(it.expiryDate)?.time ?: Long.MIN_VALUE }

                }

            _uiState.update {
                it.copy(
                    defaultBatch = latest,
                    defaultMrp = latest?.sellingPrice ?: vaccine?.mrp,
                    defaultNetRate = latest?.purchaseCost ?: vaccine?.netRate,
                    defaultManufacturer = latest?.manufacturer ?: vaccine?.manufacturer ?: vaccine?.companyName
                )
            }
        }
    }

    fun saveBatch(
        batchId: String?,
        vaccineId: String,
        batchNumber: String,
        quantity: String,
        expiryDate: String,
        mrp: Double,
        netRate: Double,
        manufacturer: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = sessionManager.getCurrentUserEmail()
                // The screen used to coerce junk to 0 ("1.5" silently saved as 0 stock).
                // Parse here so bad input surfaces through the same error snackbar every
                // other save failure uses; negative quantities are rejected in
                // InventoryRepositoryImpl.updateBatch (the trust boundary).
                val quantityValue = quantity.toIntOrNull()
                    ?: throw IllegalStateException("Quantity must be a whole number, e.g. 5 or 10.")

                if (batchId != null) {
                    val existing = _uiState.value.batch ?: throw IllegalStateException("Batch not loaded")
                    val updated = existing.copy(
                        batchNumber = batchNumber,
                        remainingQuantity = quantityValue,
                        expiryDate = expiryDate,
                        sellingPrice = mrp,
                        purchaseCost = netRate,
                        manufacturer = manufacturer
                    )
                    inventoryRepository.updateBatch(updated, user)
                } else {
                    val newBatchId = UUID.randomUUID().toString()
                    val today = com.neochildclinic.core.common.DateUtils.todayDateString()
                    val batch = VaccineBatch(
                        batchId = newBatchId,
                        vaccineId = vaccineId,
                        batchNumber = batchNumber,
                        manufacturer = manufacturer,
                        purchaseDate = today,
                        expiryDate = expiryDate,
                        purchaseQuantity = quantityValue,
                        remainingQuantity = quantityValue,
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
