package com.neochildclinic.feature.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.core.security.CurrentUserProvider
import com.neochildclinic.core.security.SessionManager
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.ExpenseCategory
import com.neochildclinic.domain.model.ExpensePaymentMethod
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.feature.finance.domain.repository.ExpenseRepository
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.common.toLocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.neochildclinic.feature.statistics.domain.StatisticsUtils
import java.time.format.DateTimeFormatter
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
    val selectedFinancialYear: String = StatisticsUtils.getCurrentFinancialYear(),
    val availableFinancialYears: List<String> = emptyList(),
    val fromDate: String = "",
    val toDate: String = "",
    val sort: ExpenseSortOption = ExpenseSortOption.DATE_DESC,
    val canManage: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
    val deletedMessage: String? = null
)

@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseListUiState())
    val uiState: StateFlow<ExpenseListUiState> = _uiState.asStateFlow()

    private val isRefreshing get() = _uiState.value.isRefreshing

    init {
        loadPermission()
        loadAvailableYears()
        loadPage(reset = true)
    }

    private fun loadAvailableYears() {
        viewModelScope.launch {
            expenseRepository.getAllExpenses().collect { allExpenses ->
                val dates = allExpenses.map { it.expenseDate }
                val years = StatisticsUtils.getAvailableFinancialYears(dates).map { "FY $it" }
                val currentFy = StatisticsUtils.getCurrentFinancialYear()
                val merged = (listOf(currentFy) + years).distinct().sortedDescending()
                _uiState.update { it.copy(availableFinancialYears = merged) }
            }
        }
    }

    private fun loadPermission() {
        // UX mirror of the RLS split: expenses update/delete are admin-or-doctor
        // (see 20260909/20260919 policies). RLS enforces independently.
        val role = currentUserProvider.getCurrentUserRole()
        _uiState.update { it.copy(canManage = role == UserRole.admin || role == UserRole.doctor) }
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

    fun onFinancialYearChange(fy: String) {
        _uiState.update { it.copy(selectedFinancialYear = fy, fromDate = "", toDate = "") }
        loadPage(reset = true)
    }

    fun onSortChange(sort: ExpenseSortOption) {
        _uiState.update { it.copy(sort = sort) }
        loadPage(reset = true)
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                query = "",
                categoryFilter = null,
                paymentMethodFilter = null,
                fromDate = "",
                toDate = "",
                selectedFinancialYear = StatisticsUtils.getCurrentFinancialYear()
            )
        }
        loadPage(reset = true)
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || isRefreshing || !_uiState.value.canLoadMore) return
        loadPage(reset = false)
    }

    fun refresh() {
        if (isRefreshing || _uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                loadPageInternal(reset = true)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // fromDate/toDate in state are display-format strings (DateDropdownPicker's format,
    // "d MMM yyyy") - converted to ISO (yyyy-MM-dd) here since that's what expenseDate is
    // actually stored/sorted as (see ExpenseEntity/AddExpenseViewModel.submit). Filtering
    // happens entirely in SQL (ExpenseDao.getFilteredExpensesPage), never by loading the
    // full table and filtering in Kotlin.
    private fun toIsoDate(displayDate: String): String? {
        if (displayDate.isBlank()) return null
        val date = PatientUtils.parseDate(displayDate) ?: return null
        return date.toLocalDate().toString()
    }

    private fun loadPage(reset: Boolean) {
        viewModelScope.launch {
            loadPageInternal(reset)
        }
    }

    private fun getFyDateRange(fyString: String): Pair<String, String> {
        val shortYear = fyString.substringAfter("FY ").substringBefore("-").trim().toIntOrNull() ?: 26
        val startYear = 2000 + shortYear
        return "$startYear-04-01" to "${startYear + 1}-03-31"
    }

    private suspend fun loadPageInternal(reset: Boolean) {
        val state = _uiState.value
        _uiState.update { if (reset) it.copy(isLoading = true, error = null) else it.copy(isLoadingMore = true) }
        try {
            val offset = if (reset) 0 else state.expenses.size
            val (fyFrom, fyTo) = getFyDateRange(state.selectedFinancialYear)
            val effectiveFrom = toIsoDate(state.fromDate) ?: fyFrom
            val effectiveTo = toIsoDate(state.toDate) ?: fyTo

            val page = expenseRepository.getFilteredExpensesPage(
                category = state.categoryFilter?.name,
                paymentMethod = state.paymentMethodFilter?.name,
                fromDate = effectiveFrom,
                toDate = effectiveTo,
                query = state.query,
                sortBy = state.sort.sqlKey,
                limit = PAGE_SIZE,
                offset = offset
            )
            val cleanPage = page.filter { com.neochildclinic.feature.statistics.domain.FinanceCalculator.isCleanExpense(it) }
            _uiState.update {
                it.copy(
                    expenses = if (reset) cleanPage else it.expenses + cleanPage,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = page.size == PAGE_SIZE,
                    error = null
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, isLoadingMore = false, error = e.message ?: "Failed to load expenses") }
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
