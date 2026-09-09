package com.neochildclinic.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.BatchStatus
import com.neochildclinic.domain.model.InventoryFilter
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventorySort
import com.neochildclinic.domain.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * One batch row within a vaccine section on the Add Stock screen. Field values are
 * kept as raw strings (like AddBatchViewModel) so the text fields can hold partial /
 * invalid input while typing; they're parsed and validated in [AddStockViewModel.submit].
 */
data class StockBatchFormState(
    val localId: String = UUID.randomUUID().toString(),
    val batchNumber: String = "",
    val expiryDate: String = "",
    val quantity: String = "",
    val mrp: String = "",
    val netRate: String = "",
    val manufacturer: String = ""
)

/** One vaccine section on the Add Stock screen, containing one or more batch rows. */
data class StockVaccineFormState(
    val localId: String = UUID.randomUUID().toString(),
    val vaccineId: String = "",
    val brandName: String = "",
    val companyName: String = "",
    val batches: List<StockBatchFormState> = listOf(StockBatchFormState())
)

data class AddStockUiState(
    val availableVaccines: List<InventoryItem> = emptyList(),
    val vaccineSections: List<StockVaccineFormState> = listOf(StockVaccineFormState()),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddStockViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddStockUiState())
    val uiState: StateFlow<AddStockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Reuses the existing inventory list (same source the main Inventory screen
            // uses) purely as the vaccine picker's data source - no separate vaccine
            // list query.
            inventoryRepository.getInventoryItems(sort = InventorySort.ALPHABETICAL, filter = InventoryFilter.ALL)
                .collect { items ->
                    _uiState.update { it.copy(availableVaccines = items, isLoading = false) }
                }
        }
    }

    fun addVaccineSection() {
        _uiState.update { it.copy(vaccineSections = it.vaccineSections + StockVaccineFormState()) }
    }

    fun removeVaccineSection(sectionId: String) {
        _uiState.update { state ->
            val updated = state.vaccineSections.filterNot { it.localId == sectionId }
            state.copy(vaccineSections = updated.ifEmpty { listOf(StockVaccineFormState()) })
        }
    }

    fun selectVaccine(sectionId: String, vaccine: InventoryItem) {
        _uiState.update { state ->
            state.copy(vaccineSections = state.vaccineSections.map { section ->
                if (section.localId == sectionId) {
                    section.copy(
                        vaccineId = vaccine.id,
                        brandName = vaccine.brandName,
                        companyName = vaccine.company,
                        batches = section.batches.map { batch ->
                            batch.copy(
                                mrp = if (vaccine.mrp > 0) vaccine.mrp.toString() else batch.mrp,
                                netRate = if (vaccine.netRate > 0) vaccine.netRate.toString() else batch.netRate,
                                manufacturer = vaccine.company.ifBlank { batch.manufacturer }
                            )
                        }
                    )
                } else section
            })
        }
    }

    fun addBatchRow(sectionId: String) {
        _uiState.update { state ->
            state.copy(vaccineSections = state.vaccineSections.map { section ->
                if (section.localId == sectionId) {
                    val defaultBatch = if (section.vaccineId.isNotBlank()) {
                        StockBatchFormState(
                            mrp = if (section.batches.firstOrNull()?.mrp?.isNotBlank() == true) section.batches.first().mrp else "",
                            netRate = if (section.batches.firstOrNull()?.netRate?.isNotBlank() == true) section.batches.first().netRate else "",
                            manufacturer = section.companyName
                        )
                    } else {
                        StockBatchFormState()
                    }
                    section.copy(batches = section.batches + defaultBatch)
                } else section
            })
        }
    }

    fun removeBatchRow(sectionId: String, batchLocalId: String) {
        _uiState.update { state ->
            state.copy(vaccineSections = state.vaccineSections.map { section ->
                if (section.localId == sectionId) {
                    val updated = section.batches.filterNot { it.localId == batchLocalId }
                    section.copy(batches = updated.ifEmpty { listOf(StockBatchFormState()) })
                } else section
            })
        }
    }

    fun updateBatch(sectionId: String, batchLocalId: String, update: (StockBatchFormState) -> StockBatchFormState) {
        _uiState.update { state ->
            state.copy(vaccineSections = state.vaccineSections.map { section ->
                if (section.localId == sectionId) {
                    section.copy(batches = section.batches.map { batch ->
                        if (batch.localId == batchLocalId) update(batch) else batch
                    })
                } else section
            })
        }
    }

    fun submit() {
        val state = _uiState.value
        val sections = state.vaccineSections

        if (sections.all { it.vaccineId.isBlank() }) {
            _uiState.update { it.copy(error = "Select at least one vaccine to add stock for.") }
            return
        }

        val selectedVaccineIds = mutableSetOf<String>()
        for (section in sections) {
            if (section.vaccineId.isBlank()) {
                _uiState.update { it.copy(error = "Select a vaccine for every section, or use \"Remove Vaccine\" on the empty one.") }
                return
            }
            if (!selectedVaccineIds.add(section.vaccineId)) {
                _uiState.update { it.copy(error = "${section.brandName} is selected more than once. Add extra batches to its existing section instead.") }
                return
            }
            for (batch in section.batches) {
                if (batch.batchNumber.isBlank() || batch.expiryDate.isBlank() || batch.quantity.isBlank() ||
                    batch.mrp.isBlank() || batch.netRate.isBlank()
                ) {
                    _uiState.update { it.copy(error = "${section.brandName}: fill in all required batch fields.") }
                    return
                }
                val quantity = batch.quantity.toIntOrNull()
                if (quantity == null || quantity <= 0) {
                    _uiState.update { it.copy(error = "${section.brandName} (${batch.batchNumber}): quantity must be a whole number greater than zero.") }
                    return
                }
                val mrp = batch.mrp.toDoubleOrNull()
                val netRate = batch.netRate.toDoubleOrNull()
                if (mrp == null || netRate == null || mrp < 0 || netRate < 0) {
                    _uiState.update { it.copy(error = "${section.brandName} (${batch.batchNumber}): enter valid, non-negative MRP/Net Rate values.") }
                    return
                }
            }

            val batchNumbersInSection = section.batches.map { it.batchNumber.trim().lowercase() }
            if (batchNumbersInSection.toSet().size != batchNumbersInSection.size) {
                _uiState.update { it.copy(error = "${section.brandName}: duplicate batch numbers were entered for this vaccine.") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                val today = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())

                val entriesByVaccine = sections
                    .filter { it.vaccineId.isNotBlank() }
                    .associate { section ->
                        section.vaccineId to section.batches.map { batch ->
                            VaccineBatchEntity(
                                batchId = UUID.randomUUID().toString(),
                                vaccineId = section.vaccineId,
                                batchNumber = batch.batchNumber.trim(),
                                manufacturer = batch.manufacturer.trim().ifBlank { section.companyName },
                                purchaseDate = today,
                                expiryDate = batch.expiryDate,
                                purchaseQuantity = batch.quantity.toIntOrNull() ?: 0,
                                remainingQuantity = batch.quantity.toIntOrNull() ?: 0,
                                supplier = "Manual Entry",
                                purchaseCost = batch.netRate.toDoubleOrNull() ?: 0.0,
                                sellingPrice = batch.mrp.toDoubleOrNull() ?: 0.0,
                                status = BatchStatus.ACTIVE.name
                            )
                        }
                    }

                inventoryRepository.addStockBatch(entriesByVaccine, user)
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save stock.") }
            }
        }
    }

    fun resetState() {
        _uiState.update { it.copy(isSaved = false, error = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
