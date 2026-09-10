package com.neochildclinic.features.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.ExpenseCategory
import com.neochildclinic.domain.model.ExpensePaymentMethod
import com.neochildclinic.domain.repository.DocumentRepository
import com.neochildclinic.domain.repository.ExpenseRepository
import com.neochildclinic.core.utils.PatientUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class AddExpenseUiState(
    val isEditMode: Boolean = false,
    val expenseId: String = java.util.UUID.randomUUID().toString(),
    val expenseDate: String = "",
    val category: ExpenseCategory? = null,
    val title: String = "",
    val description: String = "",
    val amount: String = "",
    val paymentMethod: ExpensePaymentMethod? = null,
    val referenceNumber: String = "",
    val attachmentPath: String? = null,
    val isUploadingAttachment: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val documentRepository: DocumentRepository,
    private val auth: Auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    private var loadedForId: String? = null

    // Called once from the Screen's LaunchedEffect(expenseId) - matches the
    // AddBatchScreen/AddBatchViewModel convention of passing the nav-arg id in as a plain
    // parameter rather than reading it via SavedStateHandle.
    fun loadForEdit(expenseId: String) {
        if (loadedForId == expenseId) return
        loadedForId = expenseId
        _uiState.update { it.copy(isLoading = true, isEditMode = true, expenseId = expenseId) }
        viewModelScope.launch {
            val expense = expenseRepository.getExpenseById(expenseId)
            if (expense != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        // expense.expenseDate is stored/synced as ISO (yyyy-MM-dd) so SQL
                        // range filters/sorts work correctly (see ExpenseDao) - converted
                        // here to the display format DateDropdownPicker expects, matching
                        // the rest of the app's date pickers.
                        expenseDate = PatientUtils.formatDateForDisplay(expense.expenseDate),
                        category = expense.category,
                        title = expense.title,
                        description = expense.description ?: "",
                        amount = formatPaiseForInput(expense.amountPaise),
                        paymentMethod = expense.paymentMethod,
                        referenceNumber = expense.referenceNumber ?: "",
                        attachmentPath = expense.attachmentPath
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Expense not found.") }
            }
        }
    }

    private fun formatPaiseForInput(paise: Long): String {
        val rupees = paise / 100.0
        return if (paise % 100 == 0L) rupees.toLong().toString() else "%.2f".format(rupees)
    }

    fun onDateChange(value: String) = _uiState.update { it.copy(expenseDate = value) }
    fun onCategoryChange(value: ExpenseCategory) = _uiState.update { it.copy(category = value) }
    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value) }
    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }
    fun onAmountChange(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _uiState.update { it.copy(amount = value) }
        }
    }
    fun onPaymentMethodChange(value: ExpensePaymentMethod) = _uiState.update { it.copy(paymentMethod = value) }
    fun onReferenceNumberChange(value: String) = _uiState.update { it.copy(referenceNumber = value) }

    fun uploadAttachment(fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAttachment = true) }
            try {
                // Reuses the existing patient-docs document system (task section 4/16):
                // DocumentRepository.uploadDocument() only uses its first parameter as a
                // storage folder key, nothing patient-specific about the storage call
                // itself, so the expense's own id is passed in that slot to keep receipts
                // grouped under expenses/<expenseId>/... instead of a patient folder.
                val path = documentRepository.uploadDocument(_uiState.value.expenseId, fileName, bytes)
                _uiState.update { it.copy(isUploadingAttachment = false, attachmentPath = path) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUploadingAttachment = false, error = "Failed to attach receipt: ${e.message}") }
            }
        }
    }

    fun removeAttachment() {
        val path = _uiState.value.attachmentPath ?: return
        viewModelScope.launch {
            try {
                documentRepository.deleteDocument(path)
            } catch (_: Exception) {
                // Non-fatal: proceed to clear the reference locally either way.
            }
            _uiState.update { it.copy(attachmentPath = null) }
        }
    }

    fun submit() {
        val state = _uiState.value

        val validationError = ExpenseValidator.validationError(
            expenseDate = state.expenseDate,
            category = state.category,
            title = state.title,
            amountText = state.amount,
            paymentMethod = state.paymentMethod
        )
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }
        val rupees = state.amount.toDouble()

        // The only point a Double touches money in this whole flow: converting the
        // user's typed decimal string into an integer paise amount, immediately rounded
        // to a Long. Every calculation afterward (storage, sync, financial totals) works
        // on that Long - see Expense.amountPaise / task section 2.
        val amountPaise = (rupees * 100).roundToLong()

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val user = auth.currentSessionOrNull()?.user?.email ?: "Unknown"
                // Persist as ISO (yyyy-MM-dd), not the picker's display format - a plain
                // string comparison in SQL only sorts/range-filters correctly on ISO dates
                // (see ExpenseDao.getFilteredExpensesPage / getExpensesInDateRange).
                val isoDate = PatientUtils.parseDate(state.expenseDate)
                    ?.let { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(it) }
                    ?: state.expenseDate
                val expense = Expense(
                    id = state.expenseId,
                    expenseDate = isoDate,
                    category = state.category!!,
                    title = state.title.trim(),
                    description = state.description.trim().ifBlank { null },
                    amountPaise = amountPaise,
                    paymentMethod = state.paymentMethod!!,
                    referenceNumber = state.referenceNumber.trim().ifBlank { null },
                    attachmentPath = state.attachmentPath
                )
                if (state.isEditMode) {
                    expenseRepository.updateExpense(expense, user)
                } else {
                    expenseRepository.addExpense(expense, user)
                }
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Failed to save expense.") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
