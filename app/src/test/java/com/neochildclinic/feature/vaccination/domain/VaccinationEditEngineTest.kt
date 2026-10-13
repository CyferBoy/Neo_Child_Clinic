package com.neochildclinic.feature.vaccination.domain

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
}
