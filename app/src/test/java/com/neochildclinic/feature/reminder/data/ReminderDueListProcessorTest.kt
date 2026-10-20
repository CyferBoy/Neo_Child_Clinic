package com.neochildclinic.feature.reminder.data

import com.neochildclinic.core.common.DateClassifier
import com.neochildclinic.domain.model.NextVaccinationSummary
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import org.junit.Assert.assertEquals
import org.junit.Test

// The dashboard's due-today tile moved from getDueList() (which filters to ACTIVE rows and
// enriches with patients it does not need for counting) to a dedicated count flow. This test
// pins the counting rule the tile must keep:
//   - ACTIVE rows only: terminal (COMPLETED/DISMISSED) rows also carry dueDate strings and
//     must never be counted as due TODAY,
//   - only rows whose nextDueDate classifies as Today.
class ReminderDueListProcessorTest {

    private fun due(today: Boolean, status: ReminderStatus): Vaccination {
        val date = if (today) DateClassifier.getTodayStart().let {
            com.neochildclinic.core.common.PatientUtils.formatDate(
                java.util.Date(it.timeInMillis)
            )
        } else "1 Dec 2099"
        return Vaccination(
            id = "v", patientId = "p", visitType = "VACCINATION", status = status
        ).copy(
            nextVaccinations = listOf(
                NextVaccinationSummary(reminderId = "r", type = "VACCINATION",
                    vaccineNames = listOf("BCG"), dueDate = date)
            )
        )
    }

    private val todayString: String
        get() = due(today = true, status = ReminderStatus.ACTIVE).nextDueDate

    @Test
    fun `an active reminder due today counts`() {
        val n = ReminderDueListProcessor.dueTodayCount(listOf(due(true, ReminderStatus.ACTIVE)))
        assertEquals(1, n)
    }

    @Test
    fun `terminal rows never count - even when their stored due date is today`() {
        val n = ReminderDueListProcessor.dueTodayCount(listOf(
            due(true, ReminderStatus.ACTIVE),
            due(true, ReminderStatus.COMPLETED),
            due(true, ReminderStatus.DISMISSED)
        ))
        assertEquals(1, n)
    }

    @Test
    fun `future and overdue rows do not count`() {
        val n = ReminderDueListProcessor.dueTodayCount(listOf(
            due(false, ReminderStatus.ACTIVE)
        ))
        assertEquals(0, n)
    }

    @Test
    fun `the fixture format actually classifies as Today`() {
        // Guards the fixture builder itself: the "d MMM yyyy" string built for "today" must
        // classify as 今天 Today - if it ever classifies otherwise, the pin above is vacuous.
        val todayCal = DateClassifier.getTodayStart()
        val category = DateClassifier.classify(todayString, todayCal)
        assertEquals("nextDueDate was: $todayString",
            com.neochildclinic.core.common.DateCategory.Today, category)
    }
}
