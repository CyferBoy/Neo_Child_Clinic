package com.neochildclinic.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AgeUtilsAgeMilestoneTest {
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    private fun calendar(date: String): Calendar = Calendar.getInstance().apply {
        time = sdf.parse(date)!!
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test
    fun nextMilestoneChoosesOnlyEarliestUpcoming() {
        val result = AgeUtils.getNextAgeMilestone(
            "2025-09-25",
            calendar("2026-08-25"),
            calendar("2026-10-25")
        )
        assertEquals("12 Months", result?.label)
    }

    @Test
    fun twelveMonthsSevenDaysMovesToThirteenMonths() {
        val result = AgeUtils.getNextAgeMilestone(
            "2025-08-18",
            calendar("2026-08-25"),
            calendar("2026-10-25")
        )
        assertEquals("13 Months", result?.label)
    }

    @Test
    fun sixTenAndFourteenWeekMilestonesAreCalendarDayBased() {
        assertEquals("6 Weeks", AgeUtils.getNextAgeMilestone("2026-08-01", calendar("2026-08-25"), calendar("2026-10-25"))?.label)
        assertEquals("10 Weeks", AgeUtils.getNextAgeMilestone("2026-06-20", calendar("2026-08-25"), calendar("2026-10-25"))?.label)
        assertEquals("14 Weeks", AgeUtils.getNextAgeMilestone("2026-05-20", calendar("2026-08-25"), calendar("2026-10-25"))?.label)
    }

    @Test
    fun milestoneAtTwoMonthBoundaryIsIncluded() {
        val result = AgeUtils.getNextAgeMilestone(
            "2025-10-25",
            calendar("2026-08-25"),
            calendar("2026-10-25")
        )
        assertEquals("12 Months", result?.label)
    }

    @Test
    fun milestoneAfterTwoMonthWindowIsExcluded() {
        val result = AgeUtils.getNextAgeMilestone(
            "2025-10-26",
            calendar("2026-08-25"),
            calendar("2026-10-25")
        )
        assertNull(result)
    }

    @Test
    fun sixteenMonthMilestoneUsesCombinedLabel() {
        val result = AgeUtils.getNextAgeMilestone(
            "2025-04-18",
            calendar("2026-08-25"),
            calendar("2026-10-25")
        )
        assertEquals("16–17 Months", result?.label)
    }

    @Test
    fun invalidDobIsIgnored() {
        assertNull(AgeUtils.getNextAgeMilestone("not-a-date", calendar("2026-08-25"), calendar("2026-10-25")))
    }

    @Test
    fun leapDayDobMilestoneClampsToFebruary28InANonLeapYear() {
        // 2024-02-29 + 12 months is 2025-02-28 in java.time; the milestone must be found
        // (a hand-rolled "set year + 1" would throw or roll over into March).
        val result = AgeUtils.getNextAgeMilestone(
            "2024-02-29",
            calendar("2025-02-27"),
            calendar("2025-03-02")
        )
        assertEquals("12 Months", result?.label)
    }
}
