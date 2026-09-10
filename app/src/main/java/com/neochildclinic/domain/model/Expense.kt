package com.neochildclinic.domain.model

/**
 * Predefined expense categories (task section 3). Stored on ExpenseEntity/expenses.category
 * as the enum name (String), matching how the rest of the app stores enums (e.g.
 * BatchStatus, InventoryTransactionType) - never as a hardcoded raw string sprinkled
 * through the UI.
 */
enum class ExpenseCategory(val label: String) {
    RENT("Rent"),
    ELECTRICITY("Electricity"),
    WATER("Water"),
    INTERNET("Internet"),
    STAFF_SALARY("Staff Salary"),
    MEDICAL_SUPPLIES("Medical Supplies"),
    VACCINE_PURCHASE("Vaccine Purchase"),
    EQUIPMENT("Equipment"),
    MAINTENANCE("Maintenance"),
    CLEANING("Cleaning"),
    STATIONERY("Stationery"),
    TRANSPORTATION("Transportation"),
    MARKETING("Marketing"),
    SOFTWARE_SUBSCRIPTION("Software/Subscription"),
    OTHER("Other");

    companion object {
        fun fromLabelOrName(value: String): ExpenseCategory =
            entries.find { it.label.equals(value, true) || it.name.equals(value, true) } ?: OTHER
    }
}

/**
 * The app's existing FinanceEntity.paymentMethod is a free-form string ("CASH", "ONLINE",
 * "MIXED", "FREE") with no dedicated enum anywhere in domain/model. Expenses need a real
 * enum per task section 9, so this is introduced here rather than retrofitting the
 * existing finance_transactions string field (which stays untouched, per task section 20).
 */
enum class ExpensePaymentMethod(val label: String) {
    CASH("Cash"),
    ONLINE_UPI("Online/UPI"),
    BANK("Bank");

    companion object {
        fun fromLabelOrName(value: String): ExpensePaymentMethod =
            entries.find { it.label.equals(value, true) || it.name.equals(value, true) } ?: CASH
    }
}

/**
 * Domain-level Expense, mirrored 1:1 to ExpenseEntity/the Supabase `expenses` table.
 * amountPaise is the money value in integer paise (1 rupee = 100 paise) - never a
 * Double/Float - per task section 2 ("do not use floating-point arithmetic for
 * monetary calculations"). UI layers convert to/from a rupee-denominated String only at
 * the input/display boundary.
 */
data class Expense(
    val id: String = java.util.UUID.randomUUID().toString(),
    val expenseDate: String,
    val category: ExpenseCategory,
    val title: String,
    val description: String? = null,
    val amountPaise: Long,
    val paymentMethod: ExpensePaymentMethod,
    val referenceNumber: String? = null,
    val attachmentPath: String? = null,
    val isDeleted: Boolean = false,
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isSynced: Boolean = true
) {
    val amountRupees: Double get() = amountPaise / 100.0
}
