package com.neochildclinic.features.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.model.BorrowedVaccine
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.repository.InventoryRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class BorrowedDisplayItem(
    val id: String,
    val doctorName: String,
    val vaccineName: String,
    val batchNumber: String,
    val expiryDate: String,
    val borrowedDate: String,
    val quantity: Int,
    val isReturned: Boolean,
    val returnedDate: String?,
    val type: String,
    val notes: String?,
    val record: BorrowedVaccine
)

data class BorrowedUiState(
    val borrowedList: List<BorrowedDisplayItem> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val selectedTab: Int = 0
)

@HiltViewModel
class BorrowedViewModel @Inject constructor(
    private val postgrest: Postgrest,
    private val auth: Auth,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _borrowedRecords = MutableStateFlow<List<BorrowedVaccine>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _selectedTab = MutableStateFlow(0)

    val uiState: StateFlow<BorrowedUiState> = combine(
        _borrowedRecords, 
        inventoryRepository.getInventoryItems(),
        _isLoading, 
        _selectedTab
    ) { records, inv, loading, tab ->
        val displayItems = records.map { record ->
            val vaccine = inv.find { it.id == record.vaccineId }
            val batch = vaccine?.batches?.find { it.batchId == record.batchId }
            
            BorrowedDisplayItem(
                id = record.id,
                doctorName = record.doctorName,
                vaccineName = vaccine?.brandName ?: "Unknown Vaccine",
                batchNumber = batch?.batchNumber ?: "Unknown Batch",
                expiryDate = batch?.expiryDate ?: "Unknown Expiry",
                borrowedDate = record.borrowedDate,
                quantity = record.quantity,
                isReturned = record.isReturned,
                returnedDate = record.returnedDate,
                type = record.type,
                notes = record.notes,
                record = record
            )
        }
        BorrowedUiState(displayItems, inv, loading, tab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BorrowedUiState(isLoading = true))

    init {
        fetchBorrowed()
    }

    private fun fetchBorrowed() {
        viewModelScope.launch {
            try {
                val list = postgrest.from("borrow_records").select().decodeList<BorrowedVaccine>()
                _borrowedRecords.value = list.sortedByDescending { it.borrowedDate }
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    fun updateTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun saveBorrowedItem(item: BorrowedVaccine) {
        viewModelScope.launch {
            val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
            
            if (item.id.isEmpty()) {
                // New Borrow - Deduct from inventory
                inventoryRepository.deductStock(
                    vaccineId = item.vaccineId,
                    quantity = item.quantity,
                    user = user,
                    transactionType = InventoryTransactionType.BORROWED
                )
                postgrest.from("borrow_records").insert(item.copy(id = UUID.randomUUID().toString()))
            } else {
                postgrest.from("borrow_records").update(item) {
                    filter { eq("id", item.id) }
                }
            }
            fetchBorrowed()
        }
    }

    fun markAsReturned(record: BorrowedVaccine, returnToBatchId: String? = null) {
        viewModelScope.launch {
            val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            val updated = record.copy(isReturned = true, returnedDate = sdf.format(Date()))
            
            // Returned - Restore stock. If returnToBatchId differs from the batch it was
            // borrowed from, the original batch keeps its borrowedQuantity as outstanding.
            inventoryRepository.returnBorrowedStock(
                originalBatchId = record.batchId,
                returnToBatchId = returnToBatchId ?: record.batchId,
                quantity = record.quantity,
                user = user
            )
            
            postgrest.from("borrow_records").update(updated) {
                filter { eq("id", record.id) }
            }
            fetchBorrowed()
        }
    }

    fun deleteBorrowedItem(id: String) {
        viewModelScope.launch {
            postgrest.from("borrow_records").delete {
                filter { eq("id", id) }
            }
            fetchBorrowed()
        }
    }
}
