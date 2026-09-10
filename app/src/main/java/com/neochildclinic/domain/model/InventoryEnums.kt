package com.neochildclinic.domain.model

enum class InventoryTransactionType {
    PURCHASE,
    VACCINATION,
    RETURN,
    BORROWED,
    BORROW_RETURN,
    EXPIRED,
    DAMAGED,
    COLD_CHAIN_FAILURE,
    CONTAMINATED,
    OTHER,
    MANUAL_ADJUSTMENT,
    ADJUSTMENT,
    REVERSAL
}

enum class InventoryStatus {
    PENDING,
    COMPLETED,
    PARTIAL,
    FAILED,
    SKIPPED
}

enum class BatchStatus {
    ACTIVE,
    EXPIRED,
    USED,
    DELETED
}

enum class InventoryFilter { ALL, LOW_STOCK, NEAR_EXPIRY, EXPIRED, OUT_OF_STOCK, HIDDEN, AVAILABLE }
enum class InventorySort { ALPHABETICAL, HIGHEST_STOCK, LOWEST_STOCK, EXPIRY, MANUFACTURER, NEWEST, OLDEST }

/**
 * Human-readable label for an inventory transaction, used by the Stock History screen.
 * Kept centralized here (rather than duplicated in UI files) since the same transaction
 * types are also referenced by InventoryRepositoryImpl and WasteRepositoryImpl.
 */
fun InventoryTransactionType.displayLabel(): String = when (this) {
    InventoryTransactionType.PURCHASE -> "Stock Added"
    InventoryTransactionType.VACCINATION -> "Vaccination Used"
    InventoryTransactionType.RETURN -> "Returned to Supplier"
    InventoryTransactionType.BORROWED -> "Borrowed"
    InventoryTransactionType.BORROW_RETURN -> "Borrow Returned"
    InventoryTransactionType.EXPIRED -> "Waste \u2013 Expired"
    InventoryTransactionType.DAMAGED -> "Waste \u2013 Damaged"
    InventoryTransactionType.COLD_CHAIN_FAILURE -> "Waste \u2013 Cold Chain Failure"
    InventoryTransactionType.CONTAMINATED -> "Waste \u2013 Contaminated"
    InventoryTransactionType.OTHER -> "Waste \u2013 Other"
    InventoryTransactionType.MANUAL_ADJUSTMENT -> "Manual Adjustment"
    InventoryTransactionType.ADJUSTMENT -> "Adjustment"
    InventoryTransactionType.REVERSAL -> "Reversal (Correction)"
}

/**
 * Grouping used to drive the Stock History transaction-type filter chips
 * ([All] [Added] [Used] [Borrowed] [Returned] [Waste] [Adjustment]) without
 * inventing new transaction types - each category maps onto the existing
 * InventoryTransactionType values already written by the app.
 */
enum class StockHistoryTypeFilter(val label: String) {
    ALL("All"),
    ADDED("Added"),
    USED("Used"),
    BORROWED("Borrowed"),
    RETURNED("Returned"),
    WASTE("Waste"),
    ADJUSTMENT("Adjustment"),
    REVERSAL("Reversal");

    val transactionTypes: List<InventoryTransactionType>
        get() = when (this) {
            ALL -> emptyList()
            ADDED -> listOf(InventoryTransactionType.PURCHASE)
            USED -> listOf(InventoryTransactionType.VACCINATION)
            BORROWED -> listOf(InventoryTransactionType.BORROWED)
            RETURNED -> listOf(InventoryTransactionType.BORROW_RETURN, InventoryTransactionType.RETURN)
            WASTE -> listOf(
                InventoryTransactionType.EXPIRED,
                InventoryTransactionType.DAMAGED,
                InventoryTransactionType.COLD_CHAIN_FAILURE,
                InventoryTransactionType.CONTAMINATED,
                InventoryTransactionType.OTHER
            )
            ADJUSTMENT -> listOf(
                InventoryTransactionType.MANUAL_ADJUSTMENT,
                InventoryTransactionType.ADJUSTMENT
            )
            REVERSAL -> listOf(InventoryTransactionType.REVERSAL)
        }
}
