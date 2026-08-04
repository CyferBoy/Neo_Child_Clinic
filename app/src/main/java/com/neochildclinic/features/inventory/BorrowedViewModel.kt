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

data class BorrowedUiState(
    val borrowedList: List<BorrowedVaccine> = emptyList(),
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

    private val _borrowedList = MutableStateFlow<List<BorrowedVaccine>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _selectedTab = MutableStateFlow(0)

    val uiState: StateFlow<BorrowedUiState> = combine(
        _borrowedList, 
        inventoryRepository.getInventoryItems(),
        _isLoading, 
        _selectedTab
    ) { borrowed, inv, loading, tab ->
        BorrowedUiState(borrowed, inv, loading, tab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BorrowedUiState(isLoading = true))

    init {
        fetchBorrowed()
    }

    private fun fetchBorrowed() {
        viewModelScope.launch {
            try {
                val list = postgrest.from("borrow_records").select().decodeList<BorrowedVaccine>()
                processAndSetBorrowed(list)
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    private fun processAndSetBorrowed(list: List<BorrowedVaccine>) {
        val fifteenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -15) }.time
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        
        val filtered = list.filter { item ->
            if (!item.isReturned || item.returnedDate == null) true
            else {
                val returnedDate = try { sdf.parse(item.returnedDate) } catch (ex: Exception) { null }
                returnedDate == null || returnedDate.after(fifteenDaysAgo)
            }
        }.sortedByDescending { it.borrowedDate }
        
        _borrowedList.value = filtered
        _isLoading.value = false
    }

    fun updateTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun saveBorrowedItem(item: BorrowedVaccine) {
        viewModelScope.launch {
            val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
            
            if (item.id.isEmpty()) {
                // New Borrow - Deduct from inventory
                inventoryRepository.getInventoryItems().first().find { it.brandName.equals(item.vaccineName, true) }?.let { invItem ->
                    inventoryRepository.deductStock(
                        vaccineId = invItem.id,
                        quantity = 1,
                        user = user,
                        transactionType = InventoryTransactionType.OTHER
                    )
                }
                postgrest.from("borrow_records").insert(item.copy(id = UUID.randomUUID().toString()))
            } else {
                postgrest.from("borrow_records").update(item) {
                    filter { eq("id", item.id) }
                }
            }
            fetchBorrowed()
        }
    }

    fun markAsReturned(item: BorrowedVaccine) {
        viewModelScope.launch {
            val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            val updated = item.copy(isReturned = true, returnedDate = sdf.format(Date()))
            
            // Returned - Restore stock
            inventoryRepository.getInventoryItems().first().find { it.brandName.equals(item.vaccineName, true) }?.let { invItem ->
                val batchId = invItem.batches.find { it.batchNumber == item.batchNumber }?.batchId
                if (batchId != null) {
                    inventoryRepository.addStockToBatch(batchId, 1, user, InventoryTransactionType.RETURN)
                }
            }
            
            postgrest.from("borrow_records").update(updated) {
                filter { eq("id", item.id) }
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
