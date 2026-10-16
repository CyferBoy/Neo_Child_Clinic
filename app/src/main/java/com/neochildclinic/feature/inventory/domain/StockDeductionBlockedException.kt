package com.neochildclinic.feature.inventory.domain

/**
 * Base type for "this stock deduction cannot proceed, and staff must reconcile it".
 *
 * Every precondition that can stop a dose leaving a batch throws this or a subtype, and
 * both call sites that can absorb it catch THIS type:
 *
 *  - [InsufficientStockException] - the batch does not hold enough doses.
 *  - `IllegalStateException("Batch not found")` - the batch row is gone (deleted after the
 *    screen loaded, or absent from a restored backup).
 *  - `IllegalStateException("...already expired on the vaccination date")` - the batch had
 *    expired by the visit's given date.
 *
 * The last two used to be plain IllegalStateException, so the narrow catch in
 * ClinicalVaccinationService missed them and they escaped the transaction, rolling back the
 * whole visit, its items, the finance row and the inventory status - the exact outcome the
 * recovery branch exists to prevent. They are still IllegalStateException (this extends it),
 * so existing catch sites are unaffected; they are simply caught by a wider, correct one.
 *
 * Contract: throwing this means NOTHING was written. The caller records a FAILED
 * inventory_deductions row and the visit still saves. A failure after the batch row has
 * already been updated must NOT be reported this way - it has to propagate so the
 * surrounding transaction rolls back and the ledger cannot disagree with the shelf.
 *
 * Open rather than sealed so a call site can throw it directly with its own message; the
 * messages ("Batch not found", "...already expired on the vaccination date") are what staff
 * read in the FAILED ledger row, so they are the distinguishing signal, not the type.
 */
open class StockDeductionBlockedException(message: String) : IllegalStateException(message)
