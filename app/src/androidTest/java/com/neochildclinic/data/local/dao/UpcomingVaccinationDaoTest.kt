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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Statistics -> Vaccination -> Upcoming drill-down, exercised against the real SQL in
 * [DueReminderDao] against an in-memory Room database.
 *
 * The Kotlin grouping half is covered by UpcomingVaccineGroupingTest (JVM). This file exists
 * because the parts that can silently go wrong - the `is_deleted = 0` rule and the delimited
 * match against the comma-joined `nxt_vaccine_id` TEXT column - only actually execute in
 * SQLite, so mocking the DAO would test nothing.
 */
@RunWith(AndroidJUnit4::class)
class UpcomingVaccinationDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DueReminderDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.dueReminderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun reminder(
        id: String,
        type: String = "DTaP",
        names: List<String> = listOf("Covishield"),
        ids: List<String>? = listOf("vac-a"),
        status: String = "ACTIVE",
        enabled: Boolean = true,
        category: String = "VACCINATION",
        isDeleted: Boolean = false,
        dueDate: String = "2026-01-01"
    ) = ReminderEntity(
        id = id,
        patientId = "p-$id",
        originalVisitId = "v-$id",
        vaccineName = names.joinToString(","),
        dueDate = dueDate,
        status = status,
        reminderEnabled = enabled,
        category = category,
        type = type,
        nxtVaccineId = ids,
        isDeleted = isDeleted
    )

    private fun seed(vararg rows: ReminderEntity) = runBlocking {
        rows.forEach { dao.insertReminder(it) }
    }

    @Test
    fun byTypeReturnsOnlyThatType() = runBlocking {
        seed(
            reminder("r1", type = "DTaP"),
            reminder("r2", type = "DTaP"),
            reminder("r3", type = "MR")
        )

        val dtap = dao.getUpcomingVaccinationsByType("DTaP").first()
        assertEquals(2, dtap.size)
        assertTrue(dtap.all { it.type == "DTaP" })
        assertEquals(1, dao.getUpcomingVaccinationsByType("MR").first().size)
    }

    @Test
    fun byVaccineIdReturnsOnlyThatBrand() = runBlocking {
        seed(
            reminder("r1", names = listOf("Covishield"), ids = listOf("vac-a")),
            reminder("r2", names = listOf("Covaxin"), ids = listOf("vac-b")),
            reminder("r3", names = listOf("Covishield"), ids = listOf("vac-a"))
        )

        val brandA = dao.getUpcomingVaccinationsByVaccineId("vac-a").first()
        assertEquals(2, brandA.size)
        assertEquals(1, dao.getUpcomingVaccinationsByVaccineId("vac-b").first().size)
    }

    @Test
    fun brandMatchIsExactNotSubstring() = runBlocking {
        // The regression this guards: a plain LIKE '%vac-a%' would also match 'vac-ab'.
        seed(
            reminder("r1", names = listOf("Exact"), ids = listOf("vac-a")),
            reminder("r2", names = listOf("Prefix"), ids = listOf("vac-ab"))
        )

        val matches = dao.getUpcomingVaccinationsByVaccineId("vac-a").first()
        assertEquals(1, matches.size)
        assertEquals("r1", matches.single().id)
    }

    @Test
    fun brandMatchWorksForMiddleElementOfList() = runBlocking {
        seed(
            reminder("r1", names = listOf("A", "B", "C"), ids = listOf("vac-a", "vac-b", "vac-c"))
        )

        assertEquals(1, dao.getUpcomingVaccinationsByVaccineId("vac-b").first().size)
        assertEquals(1, dao.getUpcomingVaccinationsByVaccineId("vac-c").first().size)
        assertEquals(1, dao.getUpcomingVaccinationsByVaccineId("vac-a").first().size)
    }

    @Test
    fun brandMatchWorksForFirstElementOfList() = runBlocking {
        seed(reminder("r1", names = listOf("A", "B"), ids = listOf("vac-a", "vac-b")))
        assertEquals(1, dao.getUpcomingVaccinationsByVaccineId("vac-a").first().size)
    }

    @Test
    fun softDeletedRemindersAreExcluded() = runBlocking {
        seed(
            reminder("r1"),
            reminder("r2", isDeleted = true),
            reminder("r3", isDeleted = true)
        )

        val byType = dao.getUpcomingVaccinationsByType("DTaP").first()
        assertEquals(1, byType.size)
        assertEquals("r1", byType.single().id)

        val byBrand = dao.getUpcomingVaccinationsByVaccineId("vac-a").first()
        assertEquals(1, byBrand.size)

        assertEquals(1, dao.getUpcomingVaccinations().first().size)
    }

    @Test
    fun softDeletedReminderIsExcludedFromTypeButBrandQueryStillIsolates() = runBlocking {
        seed(
            reminder("r1", names = listOf("Covishield"), ids = listOf("vac-a")),
            reminder("r2", names = listOf("Covishield"), ids = listOf("vac-a"), isDeleted = true)
        )

        assertEquals(1, dao.getUpcomingVaccinationsByVaccineId("vac-a").first().size)
    }

    @Test
    fun preservesExistingUpcomingStatusRules() = runBlocking {
        seed(
            reminder("r1"),
            reminder("r2", status = "COMPLETED"),
            reminder("r3", status = "DISMISSED"),
            reminder("r4", enabled = false),
            reminder("r5", category = "OTHER")
        )

        val rows = dao.getUpcomingVaccinations().first()
        assertEquals(1, rows.size)
        assertEquals("r1", rows.single().id)
    }

    @Test
    fun excludesNonVaccinationCategory() = runBlocking {
        seed(reminder("r1", category = "OTHER"))
        assertTrue(dao.getUpcomingVaccinations().first().isEmpty())
    }

    @Test
    fun legacyRowWithNullVaccineIdsIsExcludedFromBrandQuery() = runBlocking {
        seed(reminder("r1", ids = null))

        // Cannot match any id - matches how calculateUpcomingVaccineNeeds omits these rows
        // from the brand cards, so brand counts and brand drill-down stay consistent.
        assertTrue(dao.getUpcomingVaccinationsByVaccineId("vac-a").first().isEmpty())
        // Still counted on the type card.
        assertEquals(1, dao.getUpcomingVaccinationsByType("DTaP").first().size)
    }

    @Test
    fun legacyRowWithEmptyVaccineIdsIsExcludedFromBrandQuery() = runBlocking {
        seed(reminder("r1", ids = emptyList()))
        assertTrue(dao.getUpcomingVaccinationsByVaccineId("vac-a").first().isEmpty())
    }

    @Test
    fun emptyResultForUnknownTypeOrBrand() = runBlocking {
        seed(reminder("r1"))

        assertTrue(dao.getUpcomingVaccinationsByType("DoesNotExist").first().isEmpty())
        assertTrue(dao.getUpcomingVaccinationsByVaccineId("does-not-exist").first().isEmpty())
    }

    @Test
    fun emptyTableReturnsEmpty() = runBlocking {
        assertTrue(dao.getUpcomingVaccinations().first().isEmpty())
    }

    @Test
    fun resultsOrderedByDueDate() = runBlocking {
        seed(
            reminder("r-late", dueDate = "2026-03-01"),
            reminder("r-early", dueDate = "2026-01-01"),
            reminder("r-mid", dueDate = "2026-02-01")
        )

        assertEquals(
            listOf("r-early", "r-mid", "r-late"),
            dao.getUpcomingVaccinationsByType("DTaP").first().map { it.id }
        )
    }

    @Test
    fun multiplePatientsForSameBrandEachGetARow() = runBlocking {
        seed(
            reminder("r1"),
            reminder("r2"),
            reminder("r3")
        )

        val rows = dao.getUpcomingVaccinationsByVaccineId("vac-a").first()
        assertEquals(3, rows.size)
        assertEquals(3, rows.map { it.patientId }.distinct().size)
    }

    @Test
    fun typeCardCountMatchesSumOfBrandCounts() = runBlocking {
        // §10: a card reading N must open N rows. The type query returns the reminders that
        // back the card; the brand queries partition them by vaccine id.
        seed(
            reminder("r1", names = listOf("Covishield"), ids = listOf("vac-a")),
            reminder("r2", names = listOf("Covaxin"), ids = listOf("vac-b")),
            reminder("r3", names = listOf("Covishield", "Covaxin"), ids = listOf("vac-a", "vac-b"))
        )

        val typeRows = dao.getUpcomingVaccinationsByType("DTaP").first()
        assertEquals(3, typeRows.size)
        // Each of r1,r3 lists A; r2,r3 list B => 2 + 2 = 4 brand-level rows across 3 reminders.
        assertEquals(2, dao.getUpcomingVaccinationsByVaccineId("vac-a").first().size)
        assertEquals(2, dao.getUpcomingVaccinationsByVaccineId("vac-b").first().size)
    }
}