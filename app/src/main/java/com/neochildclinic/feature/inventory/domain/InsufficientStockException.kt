package com.neochildclinic.feature.inventory.domain

/**
 * Thrown when a stock movement cannot proceed because the batch does not hold enough doses.
 *
 * A subtype of [StockDeductionBlockedException]; see that type for the full contract. In
 * short: callers that can absorb it record a FAILED inventory_deductions row and still
 * save the visit, and anything that fails *after* the batch row has been written must
 * propagate instead so the transaction rolls back.
 *
 * It also extends IllegalStateException so pre-existing catch sites keep working.
 */
class InsufficientStockException(message: String) : StockDeductionBlockedException(message)
