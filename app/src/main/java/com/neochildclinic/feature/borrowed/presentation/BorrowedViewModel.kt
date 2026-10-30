package com.neochildclinic.feature.borrowed.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.BorrowReturnRecord
import com.neochildclinic.domain.model.BorrowedVaccine
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.model.VaccineBatch
import com.neochildclinic.feature.borrowed.domain.repository.BorrowRepository
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository
import com.neochildclinic.feature.borrowed.domain.repository.NewBatchInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

enum class BorrowStatus { BORROWED, PARTIALLY_RETURNED, RETURNED }

// Outer Borrowed/Returned tab.
enum class BorrowMainTab { BORROWED, RETURNED }

data class BorrowReturnDisplayItem(
    val id: String,
    val quantity: Int,
    val batchNumber: String,
    val expiryDate: String,
    val returnedDate: String,
    val notes: String?
)

data class BorrowedDisplayItem(
    val id: String,
    val doctorName: String,
    val vaccineId: String,
    val vaccineName: String,
    val batchNumber: String,
    val expiryDate: String,
    val borrowedDate: String,
    val borrowedQuantity: Int,
    val returnedQuantity: Int,
    val remainingQuantity: Int,
    val status: BorrowStatus,
    val type: String,
    val notes: String?,
    val returns: List<BorrowReturnDisplayItem>,
    val latestDate: String,
    val record: BorrowedVaccine
)

data class BorrowedUiState(
    val borrowedList: List<BorrowedDisplayItem> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val mainTab: BorrowMainTab = BorrowMainTab.BORROWED,
    val selectedTab: Int = 0, // By(0) / From(1)
    val actionError: String? = null
)

@HiltViewModel
class BorrowedViewModel @Inject constructor(
    private val borrowRepository: BorrowRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _mainTab = MutableStateFlow(BorrowMainTab.BORROWED)
    private val _selectedTab = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)

    // BorrowedUiState.actionError used to be a plain field on a state derived from
    // combine(), so it had no writer and could never change: saveBorrowedItem had no
    // try/catch at all, and submitReturn/deleteBorrowedItem only logged. A failed borrow
    // or return was therefore indistinguishable from a successful one (NCC-014).
    private val _actionError = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            borrowRepository.refreshBorrows()
        }
    }

    val uiState: StateFlow<BorrowedUiState> = combine(
        borrowRepository.getActiveBorrowedRecords(),
        borrowRepository.getReturnedRecords(),
        borrowRepository.getReturnRecords(),
        inventoryRepository.getInventoryItems(),
        _mainTab,
        _selectedTab,
        _isRefreshing,
        _actionError
    ) { values ->
        @Suppress("UNCHECKED_CAST") val activeRecords = values[0] as List<BorrowedVaccine>
        @Suppress("UNCHECKED_CAST") val returnedRecords = values[1] as List<BorrowedVaccine>
        @Suppress("UNCHECKED_CAST") val returns = values[2] as List<BorrowReturnRecord>
        @Suppress("UNCHECKED_CAST") val inv = values[3] as List<InventoryItem>
        val mainTab = values[4] as BorrowMainTab
        val tab = values[5] as Int
        val refreshing = values[6] as Boolean
        val actionError = values[7] as String?

        val records = activeRecords + returnedRecords
        val allBatches: List<VaccineBatch> = inv.flatMap { it.batches }

        val displayItems = records.map { record ->
            val vaccine = inv.find { it.id == record.vaccineId }
            val originalBatch = allBatches.find { it.batchId == record.batchId }

            val recordReturns = returns.filter { it.borrowRecordId == record.id }
            val returnedQty = recordReturns.sumOf { it.quantity }
            val remainingQty = (record.quantity - returnedQty).coerceAtLeast(0)
            val status = when {
                returnedQty <= 0 -> BorrowStatus.BORROWED
                returnedQty < record.quantity -> BorrowStatus.PARTIALLY_RETURNED
                else -> BorrowStatus.RETURNED
            }

            val returnDisplays = recordReturns
                .sortedByDescending { it.returnedDate }
                .map { r ->
                    val batch = allBatches.find { it.batchId == r.batchId }
                    BorrowReturnDisplayItem(
                        id = r.id,
                        quantity = r.quantity,
                        batchNumber = batch?.batchNumber ?: "Unknown Batch",
                        expiryDate = batch?.expiryDate ?: "",
                        returnedDate = r.returnedDate,
                        notes = r.notes
                    )
                }

            val latestDate = recordReturns.maxOfOrNull { it.returnedDate } ?: record.borrowedDate

            BorrowedDisplayItem(
                id = record.id,
                doctorName = record.doctorName,
                vaccineId = record.vaccineId,
                vaccineName = vaccine?.brandName ?: "Unknown Vaccine",
                batchNumber = originalBatch?.batchNumber ?: "Unknown Batch",
                expiryDate = originalBatch?.expiryDate ?: "Unknown Expiry",
                borrowedDate = record.borrowedDate,
                borrowedQuantity = record.quantity,
                returnedQuantity = returnedQty,
                remainingQuantity = remainingQty,
                status = status,
                type = record.type,
                notes = record.notes,
                returns = returnDisplays,
                latestDate = latestDate,
                record = record
            )
        }

        BorrowedUiState(
            borrowedList = displayItems,
            inventory = inv,
            isLoading = false, // If we reach here, we have a result
            isRefreshing = refreshing,
            mainTab = mainTab,
            selectedTab = tab,
            actionError = actionError
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BorrowedUiState(isLoading = true))

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                borrowRepository.refreshBorrows()
                inventoryRepository.refreshInventory()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("BorrowedViewModel", "Refresh failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun selectMainTab(tab: BorrowMainTab) {
        _mainTab.value = tab
    }

    fun updateTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun saveBorrowedItem(item: BorrowedVaccine) {
        viewModelScope.launch {
            // Had no try/catch at all: a repository failure escaped viewModelScope and
            // crashed the process (NCC-014, NCC-036).
            try {
                borrowRepository.saveBorrowedItem(item)
                _actionError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Save borrowed item failed", e)
                _actionError.value = e.message ?: "Could not record the borrow."
            }
        }
    }

    /** Clears [BorrowedUiState.actionError] once the snackbar has been shown. */
    fun clearActionError() {
        _actionError.value = null
    }

    /**
     * Records a single return transaction against [item]. Always creates a new
     * borrow_returns row - never overwrites the original borrow record or any
     * prior return. Multiple partial returns against the same record are fully
     * supported by calling this repeatedly.
     *
     * [quantity] must already be validated by the caller (UI) to be > 0 and
     * <= item.remainingQuantity - this function re-checks defensively but does
     * not surface a UI-facing error, so the dialog should validate first.
     */
    fun submitReturn(
        item: BorrowedDisplayItem,
        quantity: Int,
        batchId: String,
        notes: String?,
        newBatchInfo: NewBatchInfo? = null
    ) {
        if (quantity <= 0 || quantity > item.remainingQuantity || (batchId.isBlank() && newBatchInfo == null)) return

        viewModelScope.launch {
            try {
                borrowRepository.submitReturn(item.id, item.record.batchId, item.vaccineId, item.vaccineName, quantity, batchId, notes, newBatchInfo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Return failed", e)
                // Was log-only: the screen had no error surface at all, so a rejected
                // return looked identical to a successful one (NCC-014).
                _actionError.value = e.message ?: "Could not record the return."
            }
        }
    }

    fun deleteBorrowedItem(id: String) {
        viewModelScope.launch {
            try {
                borrowRepository.deleteBorrowedItem(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Delete borrowed item failed", e)
                _actionError.value = e.message ?: "Could not delete the borrow."
            }
        }
    }

    private companion object {
        const val TAG = "BorrowedViewModel"
    }
}
