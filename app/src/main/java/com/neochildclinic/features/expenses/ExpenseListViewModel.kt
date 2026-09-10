package com.neochildclinic.features.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.session.SessionManager
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.ExpenseCategory
import com.neochildclinic.domain.model.ExpensePaymentMethod
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.repository.ExpenseRepository
import com.neochildclinic.domain.repository.ProfileRepository
import com.neochildclinic.core.utils.PatientUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

enum class ExpenseSortOption(val label: String, val sqlKey: String) {
    DATE_DESC("Newest first", "DATE_DESC"),
    DATE_ASC("Oldest first", "DATE_ASC"),
    AMOUNT_DESC("Amount: high to low", "AMOUNT_DESC"),
    AMOUNT_ASC("Amount: low to high", "AMOUNT_ASC")
}

private const val PAGE_SIZE = 40

data class ExpenseListUiState(
    val expenses: List<Expense> = emptyList(),
    val query: String = "",
    val categoryFilter: ExpenseCategory? = null,
    val paymentMethodFilter: ExpensePaymentMethod? = null,
    val fromDate: String = "",
    val toDate: String = "",
    val sort: ExpenseSortOption = ExpenseSortOption.DATE_DESC,
    val canManage: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
    val deletedMessage: String? = null
)

@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val profileRepository: ProfileRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseListUiState())
    val uiState: StateFlow<ExpenseListUiState> = _uiState.asStateFlow()

    init {
        loadPermission()
        loadPage(reset = true)
    }

    private fun loadPermission() {
        viewModelScope.launch {
            // Reuses the same edit/delete permission split already used for patient
            // records (PatientDetailsScreen: isAdmin || role == doctor) - task section 13
            // explicitly says to reuse an existing financial-adjacent permission rather
            // than invent a new system, and no dedicated "financial" permission exists
            // in this codebase to reuse instead. Add/view stays open to every role,
            // matching Financial Statistics' current (unrestricted) access.
            val userId = sessionManager.getCurrentUserId()
            val role = userId?.let { profileRepository.getProfileById(it)?.role }
            val canManage = role == UserRole.admin || role == UserRole.doctor
            _uiState.update { it.copy(canManage = canManage) }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        loadPage(reset = true)
    }

    fun onCategoryFilterChange(value: ExpenseCategory?) {
        _uiState.update { it.copy(categoryFilter = value) }
        loadPage(reset = true)
    }

    fun onPaymentMethodFilterChange(value: ExpensePaymentMethod?) {
        _uiState.update { it.copy(paymentMethodFilter = value) }
        loadPage(reset = true)
    }

    fun onDateRangeChange(from: String, to: String) {
        _uiState.update { it.copy(fromDate = from, toDate = to) }
        loadPage(reset = true)
    }

    fun onSortChange(sort: ExpenseSortOption) {
        _uiState.update { it.copy(sort = sort) }
        loadPage(reset = true)
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(query = "", categoryFilter = null, paymentMethodFilter = null, fromDate = "", toDate = "")
        }
        loadPage(reset = true)
    }

    fun refresh() = loadPage(reset = true)

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.canLoadMore) return
        loadPage(reset = false)
    }

    // fromDate/toDate in state are display-format strings (DateDropdownPicker's format,
    // "d MMM yyyy") - converted to ISO (yyyy-MM-dd) here since that's what expenseDate is
    // actually stored/sorted as (see ExpenseEntity/AddExpenseViewModel.submit). Filtering
    // happens entirely in SQL (ExpenseDao.getFilteredExpensesPage), never by loading the
    // full table and filtering in Kotlin.
    private fun toIsoDate(displayDate: String): String? {
        if (displayDate.isBlank()) return null
        val date = PatientUtils.parseDate(displayDate) ?: return null
        return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(date)
    }

    private fun loadPage(reset: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { if (reset) it.copy(isLoading = true, error = null) else it.copy(isLoadingMore = true) }
            try {
                val offset = if (reset) 0 else state.expenses.size
                val page = expenseRepository.getFilteredExpensesPage(
                    category = state.categoryFilter?.name,
                    paymentMethod = state.paymentMethodFilter?.name,
                    fromDate = toIsoDate(state.fromDate),
                    toDate = toIsoDate(state.toDate),
                    query = state.query,
                    sortBy = state.sort.sqlKey,
                    limit = PAGE_SIZE,
                    offset = offset
                )
                _uiState.update {
                    it.copy(
                        expenses = if (reset) page else it.expenses + page,
                        isLoading = false,
                        isLoadingMore = false,
                        canLoadMore = page.size == PAGE_SIZE
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isLoadingMore = false, error = e.message ?: "Failed to load expenses") }
            }
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch {
            try {
                val user = sessionManager.getCurrentUserName()
                expenseRepository.deleteExpense(id, user)
                _uiState.update { it.copy(deletedMessage = "Expense deleted") }
                loadPage(reset = true)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to delete expense") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun clearDeletedMessage() = _uiState.update { it.copy(deletedMessage = null) }
}
