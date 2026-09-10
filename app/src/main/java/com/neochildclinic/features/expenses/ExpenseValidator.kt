package com.neochildclinic.features.expenses

import com.neochildclinic.domain.model.ExpenseCategory
import com.neochildclinic.domain.model.ExpensePaymentMethod

/**
 * Pure validation logic for the Add Expense form (task section 4: date/category/title/
 * amount required, amount > 0, payment method required, reference number optional).
 * Extracted out of AddExpenseViewModel.submit() so it's testable without a Hilt/DI
 * test harness - see ExpenseValidatorTest.
 */
object ExpenseValidator {
    fun validationError(
        expenseDate: String,
        category: ExpenseCategory?,
        title: String,
        amountText: String,
        paymentMethod: ExpensePaymentMethod?
    ): String? {
        if (expenseDate.isBlank()) return "Expense date is required."
        if (category == null) return "Category is required."
        if (title.isBlank()) return "Title is required."
        val amount = amountText.toDoubleOrNull()
        if (amountText.isBlank() || amount == null) return "Amount is required."
        if (amount <= 0.0) return "Amount must be greater than zero."
        if (paymentMethod == null) return "Payment method is required."
        return null
    }
}
