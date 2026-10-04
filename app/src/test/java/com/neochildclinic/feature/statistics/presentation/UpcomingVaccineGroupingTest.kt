package com.neochildclinic.feature.statistics.presentation

import com.neochildclinic.domain.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Statistics -> Vaccination -> Upcoming grouping.
 *
 * The Upcoming card counts and the drill-down detail list must agree (§10 of the spec), and
 * both must be driven by the same rules. These cover the Kotlin half of that (grouping by
 * type, per-brand counting, catalog-id identity). The SQL half - status/reminderEnabled/
 * category/is_deleted and the delimited nxt_vaccine_id match - lives in
 * UpcomingVaccinationDaoTest (androidTest), since it needs a real SQLite.
 *
 * NOTE: reminders arrive here already filtered by DueReminderDao.getUpcomingVaccinations(),
 * so these fixtures only set the fields grouping reads.
 */
class UpcomingVaccineGroupingTest {

    private val vaccineA = "vac-a"
    private val vaccineB = "vac-b"
    private val validIds = setOf(vaccineA, vaccineB)

    private fun reminder(
        id: String,
        type: String = "DTaP",
        names: List<String> = listOf("Covishield"),
        ids: List<String>? = listOf(vaccineA),
        patientId: String = "p-$id"
    ) = Reminder(
        id = id,
        patientId = patientId,
        originalVisitId = "v-$id",
        vaccineName = names.joinToString(","),
        dueDate = "2026-01-01",
        status = "ACTIVE",
        reminderEnabled = true,
        category = "VACCINATION",
        type = type,
        nxtVaccineId = ids
    )

    @Test
    fun groupsByTypeAndCountsReminders() {
        val stats = calculateUpcomingVaccineNeeds(
            listOf(
                reminder("r1"),
                reminder("r2"),
                reminder("r3", type = "Booster", names = listOf("Covaxin"), ids = listOf(vaccineB))
            ),
            validIds
        )

        assertEquals(2, stats.size)
        assertEquals("DTaP", stats[0].type)
        assertEquals(2, stats[0].count)
        assertEquals("Booster", stats[1].type)
        assertEquals(1, stats[1].count)
    }

    @Test
    fun typeTapReturnsOnlyThatType() {
        val stats = calculateUpcomingVaccineNeeds(
            listOf(
                reminder("r1", type = "DTaP"),
                reminder("r2", type = "DTaP"),
                reminder("r3", type = "MR")
            ),
            validIds
        )

        val dtap = stats.first { it.type == "DTaP" }
        val mr = stats.first { it.type == "MR" }
        assertEquals(2, dtap.count)
        assertEquals(1, mr.count)
    }

    @Test
    fun multipleBrandsUnderSameTypeAreSeparate() {
        val stats = calculateUpcomingVaccineNeeds(
            listOf(
                reminder("r1", names = listOf("Covishield"), ids = listOf(vaccineA)),
                reminder("r2", names = listOf("Covaxin"), ids = listOf(vaccineB)),
                reminder("r3", names = listOf("Covishield"), ids = listOf(vaccineA))
            ),
            validIds
        )

        val brands = stats.single().brands
        assertEquals(2, brands.size)
        // Sorted desc by count.
        assertEquals(vaccineA, brands[0].vaccineId)
        assertEquals("Covishield", brands[0].name)
        assertEquals(2, brands[0].count)
        assertEquals(vaccineB, brands[1].vaccineId)
        assertEquals(1, brands[1].count)
    }

    @Test
    fun multiplePatientsForSameBrandAreCountedSeparately() {
        val stats = calculateUpcomingVaccineNeeds(
            listOf(
                reminder("r1", patientId = "p1"),
                reminder("r2", patientId = "p2"),
                reminder("r3", patientId = "p3")
            ),
            validIds
        )

        assertEquals(3, stats.single().brands.single().count)
    }

    @Test
    fun oneReminderListingTwoVaccinesCountsUnderBothBrands() {
        val stats = calculateUpcomingVaccineNeeds(
            listOf(reminder("r1", names = listOf("Covishield", "Covaxin"), ids = listOf(vaccineA, vaccineB))),
            validIds
        )

        val brands = stats.single().brands
        assertEquals(2, brands.size)
        assertTrue(brands.all { it.count == 1 })
    }

    @Test
    fun sameVaccineListedTwiceInOneReminderCountsOnce() {
        // distinctBy on the vaccine id keeps the detail row count equal to the card count.
        val stats = calculateUpcomingVaccineNeeds(
            listOf(reminder("r1", names = listOf("Covishield", "Covishield"), ids = listOf(vaccineA, vaccineA))),
            validIds
        )

        assertEquals(1, stats.single().brands.size)
        assertEquals(1, stats.single().brands.single().count)
    }

    @Test
    fun brandKeyedByCatalogIdNotDisplayName() {
        // Two catalog entries sharing a display name must stay separate cards - this is why
        // the drill-down is keyed by id rather than by the free-text brand name.
        val stats = calculateUpcomingVaccineNeeds(
            listOf(
                reminder("r1", names = listOf("Covishield"), ids = listOf(vaccineA)),
                reminder("r2", names = listOf("Covishield"), ids = listOf(vaccineB))
            ),
            validIds
        )

        val brands = stats.single().brands
        assertEquals(2, brands.size)
        assertEquals(setOf(vaccineA, vaccineB), brands.map { it.vaccineId }.toSet())
    }

    @Test
    fun vaccineMissingFromCatalogIsExcludedFromBrands() {
        val stats = calculateUpcomingVaccineNeeds(
            listOf(
                reminder("r1", names = listOf("Covishield"), ids = listOf(vaccineA)),
                reminder("r2", names = listOf("Retired Vaccine"), ids = listOf("vac-deleted"))
            ),
            validIds
        )

        val brands = stats.single().brands
        assertEquals(1, brands.size)
        assertEquals(vaccineA, brands.single().vaccineId)
        // The type card still counts every reminder of the type.
        assertEquals(2, stats.single().count)
    }

    @Test
    fun legacyReminderWithNoIdsIsCountedOnTypeButNotOfferedAsBrand() {
        // Nothing to drill into without an id, so it cannot appear as a brand card - which
        // keeps brand totals consistent with what the by-vaccine-id query can return.
        val stats = calculateUpcomingVaccineNeeds(
            listOf(reminder("r1", names = listOf("Covishield"), ids = null)),
            validIds
        )

        assertEquals(1, stats.single().count)
        assertTrue(stats.single().brands.isEmpty())
    }

    @Test
    fun blankTypeGroupsUnderOtherMatchingTheQueryArgument() {
        val stats = calculateUpcomingVaccineNeeds(
            listOf(reminder("r1", type = "  "), reminder("r2", type = "")),
            validIds
        )

        assertEquals("Other", stats.single().type)
        assertEquals(2, stats.single().count)
    }

    @Test
    fun blankTypeIsTrimmedBeforeGrouping() {
        val stats = calculateUpcomingVaccineNeeds(
            listOf(reminder("r1", type = " DTaP "), reminder("r2", type = "DTaP")),
            validIds
        )

        assertEquals(1, stats.size)
        assertEquals("DTaP", stats.single().type)
        assertEquals(2, stats.single().count)
    }

    @Test
    fun emptyInputProducesNoStats() {
        assertTrue(calculateUpcomingVaccineNeeds(emptyList(), validIds).isEmpty())
    }

    @Test
    fun statsSortedByCountDescending() {
        val stats = calculateUpcomingVaccineNeeds(
            listOf(
                reminder("r1", type = "MR"),
                reminder("r2", type = "DTaP"),
                reminder("r3", type = "DTaP"),
                reminder("r4", type = "DTaP")
            ),
            validIds
        )

        assertEquals(listOf("DTaP", "MR"), stats.map { it.type })
        assertEquals(listOf(3, 1), stats.map { it.count })
    }

    @Test
    fun brandCountEqualsRowsTheByVaccineIdQueryWouldReturn() {
        // Count/detail parity: summing brand counts equals the number of matching rows.
        val reminders = listOf(
            reminder("r1", names = listOf("Covishield"), ids = listOf(vaccineA)),
            reminder("r2", names = listOf("Covaxin"), ids = listOf(vaccineB)),
            reminder("r3", names = listOf("Covishield", "Covaxin"), ids = listOf(vaccineA, vaccineB))
        )
        val brands = calculateUpcomingVaccineNeeds(reminders, validIds).single().brands

        // r1 -> A, r2 -> B, r3 -> A and B => A:2, B:2
        assertEquals(2, brands.first { it.vaccineId == vaccineA }.count)
        assertEquals(2, brands.first { it.vaccineId == vaccineB }.count)
    }
}