package com.neochildclinic.features.dashboard

import com.neochildclinic.domain.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaySlotFilterTest {

    private fun range(startHour: Int, endHour: Int) = TimeRange(startHour * 60, endHour * 60)
    private fun keys(vararg ranges: TimeRange) = TodaySlotFilter.segments(ranges.toList(), emptyList())

    // --- Core rule: 0/1 slots hidden, 2+ slots shown, fully dynamic ---

    @Test
    fun `zero slots hides the control`() {
        val segments = keys()
        assertTrue(segments.isEmpty())
        assertNull(TodaySlotFilter.effectiveKey(segments, null))
    }

    @Test
    fun `one slot hides the control even if a selection is pending`() {
        val segments = keys(range(9, 11))
        assertEquals(1, segments.size)
        assertNull(TodaySlotFilter.effectiveKey(segments, segments.first().key))
        assertNull(TodaySlotFilter.effectiveKey(segments, null))
    }

    @Test
    fun `two slots show the control with labels from the data`() {
        val segments = keys(range(9, 11), range(11, 13))
        assertEquals(2, segments.size)
        assertEquals("9 AM \u2013 11 AM", segments[0].label)
        assertEquals("11 AM \u2013 1 PM", segments[1].label)
        assertTrue(TodaySlotFilter.effectiveKey(segments, null) != null)
    }

    @Test
    fun `three slots from spec example produce three segments`() {
        val segments = keys(range(9, 11), range(11, 13), range(15, 17))
        assertEquals(listOf("9 AM \u2013 11 AM", "11 AM \u2013 1 PM", "3 PM \u2013 5 PM"), segments.map { it.label })
    }

    @Test
    fun `four slots from spec example produce four segments`() {
        val segments = keys(range(9, 12), range(14, 16), range(16, 18), range(18, 20))
        assertEquals(4, segments.size)
        assertEquals(listOf("9 AM \u2013 12 PM", "2 PM \u2013 4 PM", "4 PM \u2013 6 PM", "6 PM \u2013 8 PM"), segments.map { it.label })
    }

    // --- Segment derivation: dedupe, sort, booked-only ranges ---

    @Test
    fun `duplicate ranges across doctors collapse to one segment`() {
        val segments = TodaySlotFilter.segments(listOf(range(9, 11), range(9, 11)), listOf(range(9, 11)))
        assertEquals(1, segments.size)
    }

    @Test
    fun `segments are sorted by start time`() {
        val segments = TodaySlotFilter.segments(listOf(range(15, 17), range(9, 11)), listOf(range(11, 13)))
        assertEquals(listOf(9 * 60, 11 * 60, 15 * 60), segments.map { it.key.takeWhile { c -> c != '-' }.toInt() })
    }

    @Test
    fun `exception-blocked booked range still gets a segment so the patient stays reachable`() {
        val segments = TodaySlotFilter.segments(emptyList(), listOf(range(9, 11)))
        assertEquals(1, segments.size)
        val segments2 = TodaySlotFilter.segments(listOf(range(14, 16)), listOf(range(9, 11)))
        assertEquals(2, segments2.size)
    }

    // --- Selection state ---

    @Test
    fun `auto-selects the first segment when nothing was selected`() {
        val segments = keys(range(9, 11), range(11, 13))
        assertEquals(segments.first().key, TodaySlotFilter.effectiveKey(segments, null))
    }

    @Test
    fun `keeps a still-valid selection across a date change`() {
        val monday = keys(range(9, 11), range(11, 13))
        val keep = monday[1].key
        val tuesday = keys(range(11, 13), range(15, 17))
        assertEquals(keep, TodaySlotFilter.effectiveKey(tuesday, keep))
    }

    @Test
    fun `falls back to first segment when the old selection vanished on the new date`() {
        val monday = keys(range(9, 11), range(11, 13))
        val tuesday = keys(range(15, 17), range(18, 20))
        assertEquals(tuesday.first().key, TodaySlotFilter.effectiveKey(tuesday, monday.first().key))
    }

    // --- Patient list filtering ---

    private val nineEleven = range(9, 11)
    private val ranges = mapOf("slot-a" to nineEleven)
    private val nineElevenKey = TodaySlotFilter.key(nineEleven)

    @Test
    fun `no filter while the control is hidden`() {
        assertTrue(TodaySlotFilter.passes(null, ranges, "slot-a"))
        assertTrue(TodaySlotFilter.passes(null, ranges, null))
    }

    @Test
    fun `patient with a matching slot passes and a different slot is filtered out`() {
        assertTrue(TodaySlotFilter.passes(nineElevenKey, ranges, "slot-a"))
        val other = mapOf("slot-b" to range(15, 17))
        assertFalse(TodaySlotFilter.passes(nineElevenKey, other, "slot-b"))
    }

    @Test
    fun `patient without a slot or with an unresolvable slot is always shown`() {
        assertTrue(TodaySlotFilter.passes(nineElevenKey, ranges, null))
        assertTrue(TodaySlotFilter.passes(nineElevenKey, ranges, ""))
        assertTrue(TodaySlotFilter.passes(nineElevenKey, ranges, "missing-id"))
    }

    @Test
    fun `same time range under a different slot id still passes`() {
        assertTrue(TodaySlotFilter.passes(nineElevenKey, mapOf("other-doctors-slot" to nineEleven), "other-doctors-slot"))
    }
}
