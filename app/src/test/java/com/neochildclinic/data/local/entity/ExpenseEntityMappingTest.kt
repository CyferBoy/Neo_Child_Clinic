package com.neochildclinic.data.local.entity

import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.ExpenseCategory
import com.neochildclinic.domain.model.ExpensePaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExpenseEntityMappingTest {

    @Test
    fun `domain to entity to domain round-trip preserves every field exactly`() {
        val original = Expense(
            id = "exp-1",
            expenseDate = "2026-04-15",
            category = ExpenseCategory.MEDICAL_SUPPLIES,
            title = "Syringes",
            description = "Box of 500",
            amountPaise = 123456L,
            paymentMethod = ExpensePaymentMethod.ONLINE_UPI,
            referenceNumber = "UTR-9988",
            attachmentPath = "expenses/exp-1/receipt.jpg",
            createdBy = "nurse@example.com",
            updatedBy = "nurse@example.com"
        )

        val roundTripped = original.toEntity(isSynced = true).toDomain()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.expenseDate, roundTripped.expenseDate)
        assertEquals(original.category, roundTripped.category)
        assertEquals(original.title, roundTripped.title)
        assertEquals(original.description, roundTripped.description)
        // Exact Long equality - not comparing Doubles with a delta, since amountPaise is
        // never a floating-point type at any point in this round-trip (task section 2).
        assertEquals(original.amountPaise, roundTripped.amountPaise)
        assertEquals(original.paymentMethod, roundTripped.paymentMethod)
        assertEquals(original.referenceNumber, roundTripped.referenceNumber)
        assertEquals(original.attachmentPath, roundTripped.attachmentPath)
    }

    @Test
    fun `amountRupees divides paise by 100 exactly for whole-rupee amounts`() {
        val expense = Expense(
            expenseDate = "2026-04-15",
            category = ExpenseCategory.RENT,
            title = "Rent",
            amountPaise = 5000000L, // 50,000.00 rupees
            paymentMethod = ExpensePaymentMethod.BANK
        )
        assertEquals(50000.0, expense.amountRupees, 0.0)
    }

    @Test
    fun `unrecognized category string falls back to OTHER rather than crashing`() {
        val entity = ExpenseEntity(
            id = "e1",
            expenseDate = "2026-04-01",
            category = "SOME_FUTURE_CATEGORY_NOT_YET_KNOWN",
            title = "t",
            amountPaise = 100L,
            paymentMethod = "CASH"
        )
        assertEquals(ExpenseCategory.OTHER, entity.toDomain().category)
    }

    @Test
    fun `new expense defaults to unsynced until the sync queue confirms upload`() {
        val expense = Expense(
            expenseDate = "2026-04-01",
            category = ExpenseCategory.OTHER,
            title = "t",
            amountPaise = 100L,
            paymentMethod = ExpensePaymentMethod.CASH
        )
        val entity = expense.toEntity() // isSynced defaults to false
        assertFalse(entity.isSynced)
    }
}
