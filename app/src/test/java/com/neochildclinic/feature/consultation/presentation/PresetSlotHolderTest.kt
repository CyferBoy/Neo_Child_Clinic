package com.neochildclinic.feature.consultation.presentation

import com.neochildclinic.domain.model.AvailableSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetSlotHolderTest {

    private fun slot(id: String) = AvailableSlot(
        weeklySlotId = id, doctorId = "doc-1", startMinute = 540, endMinute = 600
    )

    @Test
    fun `preset is selected and consumed once`() {
        val holder = PresetSlotHolder()
        holder.preset("s1")

        assertEquals("s1", holder.take(null, listOf(slot("s1")))?.weeklySlotId)
        assertNull(holder.take(null, listOf(slot("s1"))))
    }

    @Test
    fun `preset not in this doctor's slots stays pending`() {
        val holder = PresetSlotHolder()
        holder.preset("s1")

        assertNull(holder.take(null, listOf(slot("s2"))))
        assertEquals("s1", holder.take(null, listOf(slot("s1")))?.weeklySlotId)
    }

    @Test
    fun `editing consultation slot wins and consumes the preset`() {
        val holder = PresetSlotHolder()
        holder.preset("s1")

        assertEquals("edit", holder.take("edit", listOf(slot("edit"), slot("s1")))?.weeklySlotId)
        assertNull(holder.take(null, listOf(slot("s1"))))
    }

    @Test
    fun `blank preset never selects`() {
        val holder = PresetSlotHolder()
        holder.preset("   ")

        assertNull(holder.take(null, listOf(slot("s1"))))
    }
}
