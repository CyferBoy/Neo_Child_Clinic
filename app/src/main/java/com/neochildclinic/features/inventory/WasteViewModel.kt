package com.neochildclinic.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.WasteRecord
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.WasteRepository
import io.github.jan.supabase.auth.Auth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class WasteInventoryItem(
    val vaccineId: String,
    val batchId: String,
    val brandName: String,
    val batchNumber: String,
    val expiryDate: String,
    val remainingQuantity: Int
)

data class WasteUiState(
    val wasteRecords: List<WasteRecord> = emptyList(),
    val inventory: List<WasteInventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WasteViewModel @Inject constructor(
    private val wasteRepository: WasteRepository,
    private val inventoryRepository: InventoryRepository,
    private val auth: Auth
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WasteUiState> = combine(
        wasteRepository.getAllWaste(),
        inventoryRepository.getInventoryItems(),
        _isSaving,
        _error
    ) { waste, inventory, saving, err ->
        val inventoryItems = inventory.flatMap { item ->
            item.batches.filter { it.remainingQuantity > 0 }.map { batch ->
                WasteInventoryItem(
                    vaccineId = item.id,
                    batchId = batch.batchId,
                    brandName = item.brandName,
                    batchNumber = batch.batchNumber,
                    expiryDate = batch.expiryDate,
                    remainingQuantity = batch.remainingQuantity
                )
            }
        }
        
        WasteUiState(
            wasteRecords = waste,
            inventory = inventoryItems,
            isLoading = false,
            isSaving = saving,
            error = err
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WasteUiState(isLoading = true))

    fun recordWaste(
        vaccineId: String,
        batchId: String,
        brandName: String,
        batchNumber: String,
        expiryDate: String,
        dateWasted: String,
        reason: String,
        quantity: Int,
        onSuccess: () -> Unit
    ) {
        if (!validateInput(vaccineId, batchId, quantity, dateWasted, reason)) return

        viewModelScope.launch {
            // Duplicate detection
            val isDuplicate = uiState.value.wasteRecords.any {
                it.vaccineId == vaccineId &&
                it.batchNumber == batchNumber &&
                it.dateWasted == dateWasted &&
                it.quantity == quantity &&
                it.reason == reason
            }

            if (isDuplicate) {
                _error.value = "Duplicate entry detected. This waste record already exists."
                return@launch
            }

            _isSaving.value = true
            val record = WasteRecord(
                id = UUID.randomUUID().toString(),
                vaccineId = vaccineId,
                batchId = batchId,
                brandName = brandName,
                batchNumber = batchNumber,
                expiryDate = expiryDate,
                dateWasted = dateWasted,
                reason = reason,
                quantity = quantity
            )
            
            try {
                wasteRepository.recordWaste(record, getCurrentUser())
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateWaste(
        id: String,
        vaccineId: String,
        batchId: String,
        brandName: String,
        batchNumber: String,
        expiryDate: String,
        dateWasted: String,
        reason: String,
        quantity: Int,
        onSuccess: () -> Unit
    ) {
        if (!validateInput(vaccineId, batchId, quantity, dateWasted, reason, isEdit = true, wasteId = id)) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val oldRecord = wasteRepository.getWasteById(id) 
                    ?: throw IllegalStateException("Original record not found")
                
                val newRecord = WasteRecord(
                    id = id,
                    vaccineId = vaccineId,
                    batchId = batchId,
                    brandName = brandName,
                    batchNumber = batchNumber,
                    expiryDate = expiryDate,
                    dateWasted = dateWasted,
                    reason = reason,
                    quantity = quantity
                )

                wasteRepository.updateWaste(oldRecord, newRecord, getCurrentUser())
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun validateInput(
        vaccineId: String, 
        batchId: String, 
        quantity: Int, 
        date: String, 
        reason: String,
        isEdit: Boolean = false,
        wasteId: String? = null
    ): Boolean {
        if (vaccineId.isBlank() || batchId.isBlank()) {
            _error.value = "Please select a vaccine and batch"
            return false
        }
        if (quantity <= 0) {
            _error.value = "Quantity must be greater than zero"
            return false
        }
        if (date.isBlank()) {
            _error.value = "Please select a valid date"
            return false
        }
        if (reason.isBlank()) {
            _error.value = "Please provide a reason"
            return false
        }

        // Check available stock
        val batch = uiState.value.inventory.find { it.batchId == batchId }
        val currentRecord = if (isEdit && wasteId != null) uiState.value.wasteRecords.find { it.id == wasteId } else null
        
        // If editing, we need to account for the fact that the old quantity will be restored
        val effectiveStock = if (isEdit && currentRecord != null && currentRecord.batchId == batchId) {
            (batch?.remainingQuantity ?: 0) + currentRecord.quantity
        } else {
            batch?.remainingQuantity ?: 0
        }

        if (effectiveStock < quantity) {
            _error.value = "Requested quantity exceeds available stock ($effectiveStock)"
            return false
        }

        return true
    }

    fun deleteWaste(id: String) {
        viewModelScope.launch {
            try {
                wasteRepository.deleteWaste(id, getCurrentUser())
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun getCurrentUser(): String {
        val user = auth.currentSessionOrNull()?.user
        return user?.userMetadata?.get("name")?.toString() ?: user?.email ?: "System User"
    }

    fun clearError() {
        _error.value = null
    }
}
