package com.neochildclinic.feature.borrowed.domain

import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.domain.model.VaccineBatch
import com.neochildclinic.feature.inventory.domain.InventoryUtils

/**
 * Picks the single batch a physical borrow must come out of.
 *
 * A borrow record stores exactly one batchId ([com.neochildclinic.data.local.entity.BorrowEntity]),
 * and the return path credits only that batch. Deduction therefore has to happen in that same
 * batch: the old code deducted earliest-expiry-first across batches while recording whichever
 * batch the dialog happened to pre-select, so returning a borrow that spanned two batches
 * credited all of it to one and permanently invented doses on the shelf.
 *
 * Rule: honour the batch the caller picked when it can supply the whole quantity unexpired,
 * otherwise take the earliest-expiring batch that can. When no single batch can cover the
 * quantity the caller must record separate borrows - spreading one borrow over several batches
 * cannot be returned correctly.
 */
fun selectBorrowBatch(
    batches: List<VaccineBatch>,
    quantity: Int,
    preferredBatchId: String? = null
): VaccineBatch? {
    if (quantity <= 0) return null

    val usable = batches.filter {
        it.remainingQuantity >= quantity && !InventoryUtils.isExpired(it.expiryDate)
    }
    if (usable.isEmpty()) return null

    val preferred = preferredBatchId?.let { id -> usable.firstOrNull { it.batchId == id } }
    if (preferred != null) return preferred

    // Soonest expiry first, so the dose on loan is the one closest to expiry. An unparseable
    // expiry date sorts last instead of first so it is only used when nothing better exists.
    return usable.minByOrNull { batch ->
        PatientUtils.parseDate(batch.expiryDate)?.time ?: Long.MAX_VALUE
    }
}