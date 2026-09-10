package com.neochildclinic.features.expenses

import com.neochildclinic.domain.model.ExpenseCategory
import com.neochildclinic.domain.model.ExpensePaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseValidatorTest {

    private fun valid() = ExpenseValidator.validationError(
        expenseDate = "15 Apr 2026",
        category = ExpenseCategory.RENT,
        title = "April Rent",
        amountText = "5000",
        paymentMethod = ExpensePaymentMethod.BANK
    )

    @Test
    fun `all fields valid returns no error`() {
        assertNull(valid())
    }

    @Test
    fun `blank date is rejected`() {
        val error = ExpenseValidator.validationError("", ExpenseCategory.RENT, "Rent", "100", ExpensePaymentMethod.CASH)
        assertEquals("Expense date is required.", error)
    }

    @Test
    fun `missing category is rejected`() {
        val error = ExpenseValidator.validationError("15 Apr 2026", null, "Rent", "100", ExpensePaymentMethod.CASH)
        assertEquals("Category is required.", error)
    }

    @Test
    fun `blank title is rejected`() {
        val error = ExpenseValidator.validationError("15 Apr 2026", ExpenseCategory.RENT, "", "100", ExpensePaymentMethod.CASH)
        assertEquals("Title is required.", error)
    }

    @Test
    fun `blank amount is rejected`() {
        val error = ExpenseValidator.validationError("15 Apr 2026", ExpenseCategory.RENT, "Rent", "", ExpensePaymentMethod.CASH)
        assertEquals("Amount is required.", error)
    }

    @Test
    fun `non-numeric amount is rejected`() {
        val error = ExpenseValidator.validationError("15 Apr 2026", ExpenseCategory.RENT, "Rent", "abc", ExpensePaymentMethod.CASH)
        assertEquals("Amount is required.", error)
    }

    @Test
    fun `zero amount is rejected`() {
        val error = ExpenseValidator.validationError("15 Apr 2026", ExpenseCategory.RENT, "Rent", "0", ExpensePaymentMethod.CASH)
        assertEquals("Amount must be greater than zero.", error)
    }

    @Test
    fun `negative amount is rejected`() {
        val error = ExpenseValidator.validationError("15 Apr 2026", ExpenseCategory.RENT, "Rent", "-50", ExpensePaymentMethod.CASH)
        assertEquals("Amount must be greater than zero.", error)
    }

    @Test
    fun `missing payment method is rejected`() {
        val error = ExpenseValidator.validationError("15 Apr 2026", ExpenseCategory.RENT, "Rent", "100", null)
        assertEquals("Payment method is required.", error)
    }

    @Test
    fun `reference number is not required for validity`() {
        // ExpenseValidator has no reference-number parameter at all - confirming task
        // section 4's "Reference number is optional" by construction: validity never
        // depends on it.
        assertNull(valid())
    }
}
