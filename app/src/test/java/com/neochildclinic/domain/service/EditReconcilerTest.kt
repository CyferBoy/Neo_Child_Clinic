package com.neochildclinic.domain.service

import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.data.local.entity.VaccinationItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance tests for item-level edit reconciliation (Fluarix Tetra + Typbar TCV /
 * Fluarix Tetra + MMR primary case). Pure logic - no Room, no Android.
 */
class EditReconcilerTest {

    private fun item(id: String, vaccine: String, vaccineId: String = "v-$vaccine", qty: Int = 1) =
        VaccinationItemEntity(
            id = id,
            vaccinationId = "visit-1",
            vaccineId = vaccineId,
            vaccineName = vaccine,
            batchId = "b-$vaccine",
            batchNumber = "LOT-$vaccine",
            quantity = qty
        )

    private fun reminder(id: String, vaccine: String, vaccineId: String? = "v-$vaccine", dueDate: String = "15/10/2026") =
        ReminderEntity(
            id = id,
            patientId = "p-1",
            originalVisitId = "visit-1",
            vaccineName = vaccine,
            dueDate = dueDate,
            status = "ACTIVE",
            type = "Booster",
            nxtVaccineId = vaccineId?.let { listOf(it) }
        )

    private fun row(reminderId: String?, vaccine: String?, vaccineId: String? = vaccine?.let { "v-$it" }, dueDate: String = "15/10/2026") =
        EditReconciler.ReminderRow(
            reminderId = reminderId,
            type = "Booster",
            vaccineName = vaccine ?: "",
            vaccineId = vaccineId,
            dueDate = dueDate,
            notes = "Scheduled during visit"
        )

    // --- vaccination_items ---

    @Test
    fun `test 1 - change one administered vaccine keeps unchanged row, replaces changed one`() {
        val plan = EditReconciler.classifyItems(
            existing = listOf(item("A", "Fluarix Tetra"), item("B", "Typbar TCV")),
            edited = listOf(item("A", "Fluarix Tetra"), item("B", "Synflorix", vaccineId = "v-Synflorix"))
        )
        assertEquals(setOf("A"), plan.keepIds)
        assertEquals(listOf("B"), plan.deleteIds)
        assertEquals(1, plan.inserts.size)
        assertEquals("Synflorix", plan.inserts[0].vaccineName)
        // The replacement must get a NEW id - the old id is gone, not reused.
        assertNotEquals("B", plan.inserts[0].id)
        assertFalse(plan.inserts.any { it.id == "A" })
        assertTrue(plan.anyChange)
    }

    @Test
    fun `test 4 - remove an item deletes only that row`() {
        val plan = EditReconciler.classifyItems(
            existing = listOf(item("A", "Fluarix Tetra"), item("B", "Typbar TCV")),
            edited = listOf(item("A", "Fluarix Tetra"))
        )
        assertEquals(setOf("A"), plan.keepIds)
        assertEquals(listOf("B"), plan.deleteIds)
        assertTrue(plan.inserts.isEmpty())
    }

    @Test
    fun `test 5 - add an item creates only the new row`() {
        val plan = EditReconciler.classifyItems(
            existing = listOf(item("A", "Fluarix Tetra")),
            edited = listOf(item("A", "Fluarix Tetra"), item("", "Synflorix", vaccineId = "v-Synflorix"))
        )
        assertEquals(setOf("A"), plan.keepIds)
        assertTrue(plan.deleteIds.isEmpty())
        assertEquals(1, plan.inserts.size)
        assertEquals("Synflorix", plan.inserts[0].vaccineName)
    }

    @Test
    fun `test 6 - no changes produces an empty plan`() {
        val plan = EditReconciler.classifyItems(
            existing = listOf(item("A", "Fluarix Tetra"), item("B", "Typbar TCV")),
            edited = listOf(item("A", "Fluarix Tetra"), item("B", "Typbar TCV"))
        )
        assertFalse(plan.anyChange)
        assertTrue(plan.deleteIds.isEmpty())
        assertTrue(plan.inserts.isEmpty())
        assertEquals(setOf("A", "B"), plan.keepIds)
    }

    @Test
    fun `quantity change replaces the row - not matched by vaccine name`() {
        val plan = EditReconciler.classifyItems(
            existing = listOf(item("A", "Fluarix Tetra", qty = 1)),
            edited = listOf(item("A", "Fluarix Tetra", qty = 2))
        )
        assertEquals(listOf("A"), plan.deleteIds)
        assertEquals(1, plan.inserts.size)
        assertEquals(2, plan.inserts[0].quantity)
        assertNotEquals("A", plan.inserts[0].id)
    }

    // --- reminders ---

    @Test
    fun `test 2 - change one reminder keeps unchanged row, replaces changed one`() {
        val plan = EditReconciler.classifyReminders(
            existing = listOf(reminder("A", "Fluarix Tetra"), reminder("B", "MMR")),
            desired = listOf(row("A", "Fluarix Tetra"), row("B", "MCV")),
            excludedIds = emptySet()
        )
        assertEquals(listOf("B"), plan.delete.map { it.id })
        assertEquals(1, plan.create.size)
        assertEquals("MCV", plan.create[0].vaccineName)
        // The kept Fluarix reminder is not queued for recreation in any way.
        assertTrue(plan.create.none { it.reminderId == "A" })
        assertEquals(1, plan.delete.size)
    }

    @Test
    fun `test 3 - both items and reminders change together`() {
        val items = EditReconciler.classifyItems(
            existing = listOf(item("A", "Fluarix Tetra"), item("B", "Typbar TCV")),
            edited = listOf(item("A", "Fluarix Tetra"), item("B", "Synflorix", vaccineId = "v-Synflorix"))
        )
        val reminders = EditReconciler.classifyReminders(
            existing = listOf(reminder("A", "Fluarix Tetra"), reminder("B", "MMR")),
            desired = listOf(row("A", "Fluarix Tetra"), row("B", "MCV")),
            excludedIds = emptySet()
        )
        assertEquals(setOf("A"), items.keepIds)
        assertEquals(listOf("B"), items.deleteIds)
        assertEquals("Synflorix", items.inserts.single().vaccineName)
        assertEquals(listOf("B"), reminders.delete.map { it.id })
        assertEquals("MCV", reminders.create.single().vaccineName)
    }

    @Test
    fun `test 6 - unchanged reminders produce an empty plan`() {
        val plan = EditReconciler.classifyReminders(
            existing = listOf(reminder("A", "Fluarix Tetra"), reminder("B", "MMR")),
            desired = listOf(row("A", "Fluarix Tetra"), row("B", "MMR")),
            excludedIds = emptySet()
        )
        assertTrue(plan.delete.isEmpty())
        assertTrue(plan.create.isEmpty())
    }

    @Test
    fun `removed reminder is deleted, cancelled-excluded reminder survives`() {
        val plan = EditReconciler.classifyReminders(
            existing = listOf(reminder("A", "Fluarix Tetra"), reminder("B", "MMR"), reminder("C", "OPV")),
            desired = listOf(row("A", "Fluarix Tetra")),
            excludedIds = setOf("C")
        )
        assertEquals(listOf("B"), plan.delete.map { it.id })
        assertTrue(plan.create.isEmpty())
    }

    @Test
    fun `added reminder with no prior row is created`() {
        val plan = EditReconciler.classifyReminders(
            existing = listOf(reminder("A", "Fluarix Tetra")),
            desired = listOf(row("A", "Fluarix Tetra"), row(null, "MCV")),
            excludedIds = emptySet()
        )
        assertTrue(plan.delete.isEmpty())
        assertEquals(1, plan.create.size)
        assertEquals("MCV", plan.create[0].vaccineName)
        assertEquals(null, plan.create[0].reminderId)
    }

    @Test
    fun `due date change counts as changed`() {
        val plan = EditReconciler.classifyReminders(
            existing = listOf(reminder("A", "MMR", dueDate = "15/10/2026")),
            desired = listOf(row("A", "MMR", dueDate = "15/11/2026")),
            excludedIds = emptySet()
        )
        assertEquals(listOf("A"), plan.delete.map { it.id })
        assertEquals(1, plan.create.size)
    }
}
