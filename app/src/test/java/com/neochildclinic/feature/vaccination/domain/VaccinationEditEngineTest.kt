package com.neochildclinic.feature.vaccination.domain

import com.neochildclinic.domain.model.InventoryDeduction
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.VaccinationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the per-batch quantity diff that decides what VaccinationEditEngine
 * deducts/reverses on a visit edit. Pure logic - no Room, no Android.
 */
class VaccinationEditEngineTest {

    private fun item(batch: String, qty: Int) = VaccinationItem(
        id = "i-$batch",
        vaccinationId = "visit-1",
        vaccineId = "v-1",
        vaccineName = "Fluarix Tetra",
        batchId = batch,
        quantity = qty
    )

    private fun visit(vararg items: VaccinationItem) =
        Vaccination(id = "visit-1", patientId = "p-1", items = items.toList())

    @Test
    fun `no change produces an empty diff`() {
        val old = visit(item("b1", 2), item("b2", 1))
        val new = visit(item("b1", 2), item("b2", 1))

        assertTrue(inventoryDiff(old, new).isEmpty())
    }

    @Test
    fun `reduced quantity produces a negative delta`() {
        val old = visit(item("b1", 2))
        val new = visit(item("b1", 1))

        assertEquals(mapOf("b1" to -1), inventoryDiff(old, new))
    }

    @Test
    fun `same quantity split across rows still diffs to zero`() {
        val old = visit(item("b1", 1), item("b1", 1))
        val new = visit(item("b1", 2))

        assertTrue(inventoryDiff(old, new).isEmpty())
    }

    private fun deduction(
        qty: Int,
        status: String,
        vaccineId: String = "v-1",
        batchId: String? = "b1"
    ) = InventoryDeduction(
        vaccinationId = "visit-1",
        vaccineId = vaccineId,
        vaccineName = "Fluarix Tetra",
        batchId = batchId,
        quantity = qty,
        status = status,
        errorMessage = null
    )

    /**
     * Severity 1, Known Bugs.md 1.1. Repro: b1 is empty, visit records b1 x 1 -> FAILED
     * row. Staff restocks 5. Edit bumps the visit to b1 x 2. The +1 deduction now
     * succeeds, so a real dose left the shelf - but the rebuilt row inherited the old
     * FAILED verdict, and deleteVaccination reverses only COMPLETED rows. The dose was
     * gone and the ledger lied.
     */
    @Test
    fun `deduction that succeeds after a restock is COMPLETED not the inherited FAILED`() {
        val previous = listOf(deduction(qty = 1, status = "FAILED"))

        assertEquals(
            "COMPLETED",
            deductionStatus(
                shortfall = null,
                deductedThisEdit = true,
                exact = previous.firstOrNull { it.quantity == 2 },
                previousForVaccine = previous
            )
        )
    }

    @Test
    fun `a shortfall this edit still wins over a successful-looking deduction`() {
        assertEquals(
            "FAILED",
            deductionStatus(
                shortfall = "Insufficient stock",
                deductedThisEdit = true,
                exact = null,
                previousForVaccine = emptyList()
            )
        )
    }

    @Test
    fun `untouched batch with no completed history stays FAILED`() {
        val previous = listOf(deduction(qty = 1, status = "FAILED"))

        assertEquals(
            "FAILED",
            deductionStatus(
                shortfall = null,
                deductedThisEdit = false,
                exact = null,
                previousForVaccine = previous
            )
        )
    }

    @Test
    fun `untouched batch with an exact completed row keeps COMPLETED`() {
        val previous = listOf(deduction(qty = 2, status = "COMPLETED"))

        assertEquals(
            "COMPLETED",
            deductionStatus(
                shortfall = null,
                deductedThisEdit = false,
                exact = previous.first(),
                previousForVaccine = previous
            )
        )
    }

    @Test
    fun `brand new batch with no history is COMPLETED`() {
        assertEquals(
            "COMPLETED",
            deductionStatus(
                shortfall = null,
                deductedThisEdit = false,
                exact = null,
                previousForVaccine = emptyList()
            )
        )
    }
}
