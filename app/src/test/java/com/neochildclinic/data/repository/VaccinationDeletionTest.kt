package com.neochildclinic.data.repository

import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class VaccinationDeletionTest {

    private fun ded(batch: String?, qty: Int, status: String) = InventoryDeductionEntity(
        vaccinationId = "visit",
        vaccineId = "vac",
        vaccineName = "BCG",
        batchId = batch,
        quantity = qty,
        status = status,
        errorMessage = null,
        resolvedAt = 0L
    )

    @Test
    fun `reversal sums real item quantities per batch`() {
        // Two items from the same batch (qty 1 + qty 2) must restore 3, not "1 per occurrence".
        val rows = listOf(
            ded("b1", 1, "COMPLETED"),
            ded("b1", 2, "COMPLETED"),
            ded("b2", 4, "COMPLETED")
        )
        assertEquals(mapOf("b1" to 3, "b2" to 4), completedQuantityByBatch(rows))
    }

    @Test
    fun `failed or unresolved deductions are not reversed`() {
        val rows = listOf(
            ded("b1", 3, "COMPLETED"),
            ded("b1", 5, "FAILED"),
            ded(null, 9, "COMPLETED")
        )
        assertEquals(mapOf("b1" to 3), completedQuantityByBatch(rows))
    }

    @Test
    fun `no completed deductions means nothing is restored`() {
        assertEquals(emptyMap<String, Int>(), completedQuantityByBatch(emptyList()))
        assertEquals(emptyMap<String, Int>(), completedQuantityByBatch(listOf(ded("b1", 2, "FAILED"))))
    }
}