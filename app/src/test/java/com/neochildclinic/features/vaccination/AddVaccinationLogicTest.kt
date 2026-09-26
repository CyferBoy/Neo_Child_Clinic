package com.neochildclinic.features.vaccination

import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.VaccinationItem
import com.neochildclinic.domain.model.VaccineBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddVaccinationLogicTest {

    private fun batch(batchId: String, exp: String, remaining: Int) = VaccineBatch(
        batchId = batchId,
        vaccineId = "vaccine-1",
        batchNumber = "B-$batchId",
        purchaseDate = "2025-01-01",
        expiryDate = exp,
        remainingQuantity = remaining,
        purchaseCost = 10.0,
        sellingPrice = 20.0
    )

    private fun vaccine(id: String, brand: String, vararg batches: VaccineBatch) =
        InventoryItem(id = id, brandName = brand, stock = batches.sumOf { it.remainingQuantity }, type = "Booster", company = "C", batches = batches.toList())

    // --- bestAvailableBatch ---

    @Test
    fun `bestAvailableBatch picks the soonest non-expired, in-stock batch`() {
        val v = vaccine("v1", "DPT",
            batch("b1", "2025-12-01", 0),   // out of stock
            batch("b2", "2026-12-01", 10),  // valid, later
            batch("b3", "2026-06-01", 5)    // valid, earliest
        )
        assertEquals("b3", bestAvailableBatch(v, "2026-05-01")?.batchId)
    }

    @Test
    fun `bestAvailableBatch returns null when every batch is expired before the given date`() {
        val v = vaccine("v1", "DPT", batch("b1", "2025-06-01", 5))
        assertNull(bestAvailableBatch(v, "2026-05-01"))
    }

    // --- revalidateRowsForDate ---

    @Test
    fun `revalidateRowsForDate swaps a batch that expires before the new date for the next valid one`() {
        val v = vaccine("v1", "DPT",
            batch("old", "2026-05-01", 10),
            batch("new", "2026-09-01", 10)
        )
        val rows = listOf(VaccineSelectionState(selectedVaccine = v, selectedBatch = v.batches[0]))
        val result = revalidateRowsForDate(rows, "2026-06-15")
        assertEquals("new", result.single().selectedBatch?.batchId)
    }

    @Test
    fun `revalidateRowsForDate clears the batch when no valid replacement exists`() {
        val v = vaccine("v1", "DPT", batch("old", "2026-05-01", 10))
        val result = revalidateRowsForDate(listOf(VaccineSelectionState(selectedVaccine = v, selectedBatch = v.batches[0])), "2026-06-15")
        assertNull(result.single().selectedBatch)
    }

    @Test
    fun `revalidateRowsForDate keeps a batch that is still valid on the new date`() {
        val v = vaccine("v1", "DPT", batch("ok", "2026-09-01", 10))
        val result = revalidateRowsForDate(listOf(VaccineSelectionState(selectedVaccine = v, selectedBatch = v.batches[0])), "2026-06-15")
        assertEquals("ok", result.single().selectedBatch?.batchId)
    }

    // --- buildNextVaccinationGroups ---

    @Test
    fun `next groups group ACTIVE enabled reminders by due date and expand multi-vaccine reminders`() {
        val reminder = ReminderEntity(
            id = "r1", patientId = "p1", originalVisitId = "v1", vaccineName = "MMR",
            dueDate = "2026-10-15", status = "ACTIVE", type = "Primary", nxtVaccineId = listOf("m1", "m2")
        )
        val groups = buildNextVaccinationGroups(listOf(reminder), listOf(vaccine("m1", "MMR"), vaccine("m2", "MMR")))
        assertEquals(1, groups.size)
        assertEquals("2026-10-15", groups[0].dueDate)
        assertEquals(2, groups[0].items.size)
        assertEquals("r1", groups[0].items[0].reminderId)
        assertEquals("m1", groups[0].items[0].vaccine?.id)
        assertEquals("m2", groups[0].items[1].vaccine?.id)
    }

    @Test
    fun `next groups skip non-active and disabled reminders`() {
        val inactive = ReminderEntity(id = "r1", patientId = "p1", originalVisitId = "v1", vaccineName = "X", dueDate = "2026-10-15", status = "COMPLETED", reminderEnabled = true)
        val disabled = ReminderEntity(id = "r2", patientId = "p1", originalVisitId = "v1", vaccineName = "X", dueDate = "2026-10-15", status = "ACTIVE", reminderEnabled = false)
        assertTrue(buildNextVaccinationGroups(listOf(inactive, disabled), emptyList()).isEmpty())
    }

    // --- buildVaccineSelectionRows ---

    @Test
    fun `selection rows keep the persisted item identity and fall back to placeholder vaccine and direct batch lookup`() = kotlinx.coroutines.runBlocking {
        val item = VaccinationItem(id = "item-1", vaccinationId = "v1", vaccineId = "gone", vaccineName = "DPT", batchId = "batch-kept", batchNumber = "B7")
        val kept = batch("batch-kept", "2026-09-01", 3)
        val rows = buildVaccineSelectionRows(listOf(item), emptyList()) { b -> if (b == "batch-kept") kept else null }
        assertEquals("item-1", rows.single().id)
        assertEquals("DPT", rows.single().selectedVaccine?.brandName)
        assertEquals("batch-kept", rows.single().selectedBatch?.batchId)
    }

    // --- validateNextGroups ---

    @Test
    fun `validateNextGroups returns null when every group has a type and due date`() {
        val groups = listOf(NextVaccinationGroup(dueDate = "2026-10-15", items = listOf(NextVaccinationItem(type = "Booster"))))
        assertNull(validateNextGroups(groups))
    }

    @Test
    fun `validateNextGroups flags blank types with typeError`() {
        val groups = listOf(
            NextVaccinationGroup(dueDate = "2026-10-15", items = listOf(NextVaccinationItem(type = "Booster"), NextVaccinationItem(type = ""))),
            NextVaccinationGroup(dueDate = "", items = listOf(NextVaccinationItem(type = "")))
        )
        val result = validateNextGroups(groups)
        assertEquals("Each Next Vaccination entry requires a Type and Due Date.", result?.errorMessage)
        val flagged = result!!.groupsWithErrors.flatMap { it.items }.map { it.typeError }
        assertEquals(listOf(false, true, true), flagged)
    }
}