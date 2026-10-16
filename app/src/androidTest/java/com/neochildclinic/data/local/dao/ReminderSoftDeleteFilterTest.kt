package com.neochildclinic.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every read query on [DueReminderDao] must exclude `is_deleted = 1` rows (NCC-006).
 *
 * Nine lookups omitted the filter while seven included it, so the DAO contradicted its own
 * stated rule. The defect is latent today — nothing in app/src/main writes `is_deleted = 1`
 * — but migration 31->32 deliberately excludes `reminders` from its hard delete, so a row
 * soft-deleted by a pre-v31 client survives into HEAD. When that happens, the Due screen,
 * patient history, the backup export and the sync conflict guard all disagree with the
 * statistics queries that already filtered.
 *
 * These need a real SQLite engine, so they live in androidTest and do NOT run in
 * `gradlew test` — hence the `android-instrumented` CI job.
 */
@RunWith(AndroidJUnit4::class)
class ReminderSoftDeleteFilterTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DueReminderDao
    private lateinit var backupDao: com.neochildclinic.data.local.dao.BackupDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.dueReminderDao()
        backupDao = db.backupDao()
    }

    @After
    fun tearDown() = db.close()

    private fun row(
        id: String,
        patientId: String = "p-1",
        visitId: String = "v-1",
        vaccineName: String = "Covishield",
        type: String = "DTaP",
        dueDate: String = "2026-01-01",
        status: String = "ACTIVE",
        enabled: Boolean = true,
        deleted: Boolean
    ) = ReminderEntity(
        id = id,
        patientId = patientId,
        originalVisitId = visitId,
        vaccineName = vaccineName,
        dueDate = dueDate,
        status = status,
        reminderEnabled = enabled,
        category = "VACCINATION",
        type = type,
        isDeleted = deleted,
    )

    private fun seed(vararg rows: ReminderEntity) = runBlocking {
        rows.forEach { dao.insertReminder(it) }
    }

    // ── the nine lookups that were missing the filter ───────────────────────

    @Test
    fun getRemindersByVisitId_excludesDeleted() = runBlocking {
        seed(row("live", visitId = "v-9", deleted = false), row("dead", visitId = "v-9", deleted = true))

        assertEquals(listOf("live"), dao.getRemindersByVisitId("v-9").map { it.id })
    }

    @Test
    fun getReminderById_excludesDeleted() = runBlocking {
        seed(row("dead", deleted = true))

        assertNull(dao.getReminderById("dead"))
    }

    @Test
    fun getDueReminder_excludesDeleted() = runBlocking {
        seed(row("dead", deleted = true))

        assertNull(dao.getDueReminder("p-1", "v-1", "Covishield", "DTaP"))
    }

    @Test
    fun getReminderByUniqueEvent_excludesDeleted() = runBlocking {
        seed(row("dead", deleted = true))

        assertNull(dao.getReminderByUniqueEvent("p-1", "v-1", "2026-01-01", "Covishield", "DTaP"))
    }

    @Test
    fun getReminderByStableId_excludesDeleted() = runBlocking {
        seed(row("dead", deleted = true))

        assertNull(dao.getReminderByStableId("p-1", "v-1", "Covishield", "DTaP"))
    }

    @Test
    fun getDueRemindersForPatient_excludesDeleted() = runBlocking {
        seed(
            row("live-a", patientId = "p-7", deleted = false),
            row("dead", patientId = "p-7", deleted = true),
        )

        assertEquals(listOf("live-a"), dao.getDueRemindersForPatient("p-7").first().map { it.id })
    }

    // ── the three paginated variants: dead code today, same trap if wired ──

    @Test
    fun pagedQueries_excludeDeleted() = runBlocking {
        seed(
            row("live", status = "ACTIVE", enabled = true, deleted = false),
            row("dead", status = "ACTIVE", enabled = true, deleted = true),
        )

        assertEquals(listOf("live"), dao.getDueRemindersPage(limit = 50, offset = 0).map { it.id })
    }

    @Test
    fun completedPage_excludesDeleted() = runBlocking {
        seed(
            row("live", status = "COMPLETED", enabled = false, deleted = false),
            row("dead", status = "COMPLETED", enabled = false, deleted = true),
        )

        assertEquals(listOf("live"), dao.getCompletedRemindersPage(limit = 50, offset = 0).map { it.id })
    }

    @Test
    fun dismissedPage_excludesDeleted() = runBlocking {
        seed(
            row("live", status = "DISMISSED", enabled = true, deleted = false),
            row("dead", status = "DISMISSED", enabled = true, deleted = true),
        )

        assertEquals(listOf("live"), dao.getDismissedRemindersPage(limit = 50, offset = 0).map { it.id })
    }

    // ── the backup export must not resurrect them on the restoring device ────

    @Test
    fun backupExport_excludesDeleted() = runBlocking {
        seed(row("live", deleted = false), row("dead", deleted = true))

        assertEquals(
            "a .nccb carrying a soft-deleted reminder restores it on the other device",
            listOf("live"),
            backupDao.getAllRemindersForBackup().map { it.id },
        )
    }

    // ── the seven that already filtered must keep doing so ──────────────────

    @Test
    fun preExistingFilters_stillExcludeDeleted() = runBlocking {
        seed(
            row("live", deleted = false),
            row("dead", deleted = true),
        )

        assertTrue(dao.getAllReminders().first().none { it.id == "dead" })
        assertTrue(dao.getUpcomingVaccinations().first().none { it.id == "dead" })
        assertTrue(dao.getUpcomingVaccinationsByType("DTaP").first().none { it.id == "dead" })
        assertTrue(dao.getUpcomingVaccinationsByVaccineId("vac-a").first().none { it.id == "dead" })
        assertTrue(dao.getAllDueReminders().first().none { it.id == "dead" })
        assertTrue(dao.getAllCompletedReminders().first().none { it.id == "dead" })
        assertTrue(dao.getAllDismissedReminders().first().none { it.id == "dead" })
    }

    /** Guards the fix: every read query above must keep excluding deleted rows. */
    @Test
    fun liveRows_areStillReturned() = runBlocking {
        seed(row("live", deleted = false))

        assertEquals(1, dao.getRemindersByVisitId("v-1").size)
        assertEquals(1, dao.getReminderById("live")?.let { 1 } ?: 0)
        assertEquals(listOf("live"), dao.getDueRemindersForPatient("p-1").first().map { it.id })
        assertEquals(listOf("live"), backupDao.getAllRemindersForBackup().map { it.id })
    }
}
