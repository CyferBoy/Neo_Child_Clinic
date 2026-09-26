package com.neochildclinic.features.dashboard

import com.neochildclinic.domain.model.TimeRange

/** One dynamic segment of the Today's Patient slot filter. */
data class SlotSegment(val key: String, val label: String)

/**
 * Pure rules for the dynamic slot control - covered by TodaySlotFilterTest.
 * effectiveKey == null means "no filtering" (0 or 1 available slots).
 */
object TodaySlotFilter {
    fun key(range: TimeRange): String = "${range.startMinute}-${range.endMinute}"

    /** Distinct time ranges (availability + booked), sorted by start time, as segments. */
    fun segments(availability: List<TimeRange>, booked: List<TimeRange>): List<SlotSegment> =
        (availability + booked)
            .distinctBy { it.startMinute to it.endMinute }
            .sortedBy { it.startMinute }
            .map { SlotSegment(key(it), it.label()) }

    /** Auto-select the first segment when 2+ exist; keep a still-valid prior selection. */
    fun effectiveKey(segments: List<SlotSegment>, selected: String?): String? = when {
        segments.size < 2 -> null
        selected != null && segments.any { it.key == selected } -> selected
        else -> segments.first().key
    }

    fun passes(effectiveKey: String?, ranges: Map<String, TimeRange>, slotId: String?): Boolean {
        val want = effectiveKey ?: return true
        if (slotId.isNullOrBlank()) return true
        val range = ranges[slotId] ?: return true
        return key(range) == want
    }
}