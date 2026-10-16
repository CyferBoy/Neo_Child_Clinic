package com.neochildclinic.feature.inventory.data

import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.model.VaccineBatch

// Applies a stock deduction to a batch, routing the quantity into the matching
// used/wasted/borrowed bucket alongside remainingQuantity so those counters always
// stay consistent with why stock left the batch.
internal fun VaccineBatchEntity.deducted(quantity: Int, transactionType: InventoryTransactionType, userName: String): VaccineBatchEntity {
    // Stock movement is a trust boundary: every repository entry point that moves doses
    // funnels through here. A non-positive quantity used to pass the "enough stock?" check
    // and then either write a bogus no-op transaction (0) or, worse, ADD doses back to the
    // shelf and drive usedQuantity negative (-n) - inventing stock that never existed.
    require(quantity > 0) { "Deduction quantity must be greater than zero, was $quantity" }
    val base = copy(
        remainingQuantity = remainingQuantity - quantity,
        updatedBy = userName,
        updatedAt = PatientUtils.getCurrentIsoTimestamp()
    )
    return when (transactionType) {
        InventoryTransactionType.VACCINATION -> base.copy(usedQuantity = usedQuantity + quantity)
        InventoryTransactionType.BORROWED -> base.copy(borrowedQuantity = borrowedQuantity + quantity)
        InventoryTransactionType.EXPIRED,
        InventoryTransactionType.DAMAGED,
        InventoryTransactionType.COLD_CHAIN_FAILURE,
        InventoryTransactionType.CONTAMINATED,
        InventoryTransactionType.OTHER -> base.copy(wastedQuantity = wastedQuantity + quantity)
        else -> base
    }
}

internal fun buildStockTransaction(
    vaccineId: String,
    batchId: String,
    transactionType: InventoryTransactionType,
    quantity: Int,
    previousQuantity: Int,
    currentQuantity: Int,
    user: String,
    notes: String?,
    patientId: String? = null,
    visitId: String? = null
): InventoryTransactionEntity {
    val now = PatientUtils.getCurrentIsoTimestamp()
    return InventoryTransactionEntity(
        vaccineId = vaccineId,
        batchId = batchId,
        patientId = patientId,
        visitId = visitId,
        transactionType = transactionType.name,
        quantity = quantity,
        previousQuantity = previousQuantity,
        currentQuantity = currentQuantity,
        user = user,
        notes = notes,
        timestamp = now,
        createdBy = user,
        updatedBy = user
    )
}
// Sort keys for the inventory list. All three compare PARSED dates, never the stored
// string: Constants.DATE_FORMAT is "d MMM yyyy" and is not zero-padded, so a textual
// comparison puts "16 Oct 2026" before "9 Oct 2026" and sorts a whole year wrong
// (NCC-017). Pure so they are unit-testable without Room - see InventorySortKeysTest.
//
// Long.MIN_VALUE / Long.MAX_VALUE mean "unknown" and sort to the end in both directions,
// which is what the previous "" and "9999-12-31" sentinels were trying to express.

internal fun List<VaccineBatch>.batchExpiryMillis(): Long =
    firstOrNull { !it.expiryDate.isNullOrBlank() }
        ?.let { PatientUtils.parseDate(it.expiryDate)?.time }
        ?: Long.MAX_VALUE

internal fun List<VaccineBatch>.latestPurchaseMillis(): Long =
    maxOfOrNull { PatientUtils.parseDate(it.purchaseDate)?.time ?: Long.MIN_VALUE }
        ?: Long.MIN_VALUE

internal fun List<VaccineBatch>.earliestPurchaseMillis(): Long =
    minOfOrNull { PatientUtils.parseDate(it.purchaseDate)?.time ?: Long.MAX_VALUE }
        ?: Long.MAX_VALUE

/** The batch whose purchase date parses latest; used for the MRP/net-rate fallback. */
internal fun List<VaccineBatch>.newestBatch(): VaccineBatch? =
    maxByOrNull { PatientUtils.parseDate(it.purchaseDate)?.time ?: Long.MIN_VALUE }
