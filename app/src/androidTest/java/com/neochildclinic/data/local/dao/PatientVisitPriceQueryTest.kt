package com.neochildclinic.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.VaccinationItemEntity
import com.neochildclinic.data.local.entity.VisitEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the performance pass that replaced PatientListViewModel's
 * `vaccinationRepository.allVaccinations.filter { it.totalPaid <= 0.0 }` projection
 * (which pulled every visit and a query per visit) with
 * `SELECT DISTINCT patientId FROM patient_visits WHERE totalPaid <= 0.0`, and that
 * replaced the per-visit item-flow combine() with a Room @Relation batch read.
 *
 * The badge rules are behavioural, so they are pinned here: any-vs-all semantics,
 * DISTINCT, and the fact that a patient with no visits is never flagged.
 */
@RunWith(AndroidJUnit4::class)
class PatientVisitPriceQueryTest {
    private lateinit var db: AppDatabase
    private lateinit var patientDao: PatientDao
    private lateinit var vaccinationDao: VaccinationDao
    private lateinit var itemDao: VaccinationItemDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        patientDao = db.patientDao()
        vaccinationDao = db.vaccinationDao()
        itemDao = db.vaccinationItemDao()
    }

    @After
    fun tearDown() = db.close()

    private fun patient(id: String) = PatientEntity(
        id = id,
        name = "Patient $id",
        phone = "90000000$id",
        dob = "2020-01-01",
        gender = "MALE"
    )

    private fun visit(id: String, patientId: String, totalPaid: Double, dateGiven: String = "2026-04-01") =
        VisitEntity(
            id = id,
            patientId = patientId,
            dateGiven = dateGiven,
            totalPaid = totalPaid,
            receiptNumber = "R-$id"
        )

    private fun item(id: String, visitId: String, name: String) = VaccinationItemEntity(
        id = id,
        vaccinationId = visitId,
        vaccineId = "vac-$name",
        vaccineName = name,
        batchId = "batch-$id"
    )

    @Test
    fun missingPrice_flagsPatient_whenAnyVisitIsUnpaid() = runBlocking {
        // p-unpaid-one: single visit at 0.0
        // p-unpaid-two: a paid visit AND a later unpaid one - must still flag ("any", not "all")
        patientDao.insertPatient(patient("p1"))
        patientDao.insertPatient(patient("p2"))
        vaccinationDao.insertVaccination(visit("v1", "p1", 0.0))
        vaccinationDao.insertVaccination(visit("v2", "p2", 500.0, "2026-04-01"))
        vaccinationDao.insertVaccination(visit("v3", "p2", 0.0, "2026-04-02"))

        assertEquals(
            setOf("p1", "p2"),
            patientDao.getPatientIdsWithMissingPrice().first().toSet()
        )
    }

    @Test
    fun missingPrice_doesNotFlagFullyPaidPatients() = runBlocking {
        patientDao.insertPatient(patient("p1"))
        vaccinationDao.insertVaccination(visit("v1", "p1", 1.0))

        assertEquals(emptyList<String>(), patientDao.getPatientIdsWithMissingPrice().first())
    }

    @Test
    fun missingPrice_doesNotFlagPatientWithNoVisits() = runBlocking {
        // A patient who has never been vaccinated must not appear as "missing price".
        patientDao.insertPatient(patient("never-visited"))

        assertEquals(emptyList<String>(), patientDao.getPatientIdsWithMissingPrice().first())
    }

    @Test
    fun missingPrice_flagsOnZeroExactly_butNotOnSmallPositive() = runBlocking {
        patientDao.insertPatient(patient("zero"))
        patientDao.insertPatient(patient("positive"))
        vaccinationDao.insertVaccination(visit("v1", "zero", 0.0))
        vaccinationDao.insertVaccination(visit("v2", "positive", 0.01))

        assertEquals(setOf("zero"), patientDao.getPatientIdsWithMissingPrice().first().toSet())
    }

    @Test
    fun missingPrice_returnsEachPatientOnce_evenWithSeveralUnpaidVisits() = runBlocking {
        // Guards the DISTINCT: without it this patient appears three times, and the
        // ViewModel's toSet() would be doing work the query should have done.
        patientDao.insertPatient(patient("p1"))
        vaccinationDao.insertVaccination(visit("v1", "p1", 0.0, "2026-04-01"))
        vaccinationDao.insertVaccination(visit("v2", "p1", 0.0, "2026-04-02"))
        vaccinationDao.insertVaccination(visit("v3", "p1", -50.0, "2026-04-03"))

        val ids = patientDao.getPatientIdsWithMissingPrice().first()
        assertEquals(listOf("p1"), ids)
    }

    @Test
    fun missingPrice_emitsAgain_whenAVisitPriceChanges() = runBlocking {
        patientDao.insertPatient(patient("p1"))
        vaccinationDao.insertVaccination(visit("v1", "p1", 0.0))
        assertEquals(setOf("p1"), patientDao.getPatientIdsWithMissingPrice().first().toSet())

        vaccinationDao.insertVaccination(visit("v1", "p1", 750.0))

        assertEquals(emptyList<String>(), patientDao.getPatientIdsWithMissingPrice().first())
    }

    @Test
    fun batchItemsRead_attachesEachItemToItsOwnVisit() = runBlocking {
        // The N+1 being replaced returned one flow per visit; a relation bug that crossed
        // items between visits would corrupt doses, so assert the exact grouping.
        patientDao.insertPatient(patient("p1"))
        vaccinationDao.insertVaccination(visit("v1", "p1", 100.0, "2026-04-01"))
        vaccinationDao.insertVaccination(visit("v2", "p1", 200.0, "2026-04-02"))
        itemDao.insertItems(
            listOf(
                item("i1", "v1", "VAX-A"),
                item("i2", "v1", "VAX-B"),
                item("i3", "v2", "VAX-C")
            )
        )

        val rows = vaccinationDao.getAllVaccinationsWithItems().first()

        assertEquals(2, rows.size)
        assertEquals(listOf("VAX-A", "VAX-B"), rows.first { it.visit.id == "v1" }.items.map { it.vaccineName })
        assertEquals(listOf("VAX-C"), rows.first { it.visit.id == "v2" }.items.map { it.vaccineName })
    }

    @Test
    fun batchItemsRead_forSinglePatient_returnsOnlyThatPatientsVisits() = runBlocking {
        patientDao.insertPatient(patient("p1"))
        patientDao.insertPatient(patient("p2"))
        vaccinationDao.insertVaccination(visit("v1", "p1", 100.0, "2026-04-01"))
        vaccinationDao.insertVaccination(visit("v2", "p1", 100.0, "2026-04-02"))
        vaccinationDao.insertVaccination(visit("v3", "p2", 100.0, "2026-04-03"))
        itemDao.insertItems(listOf(item("i1", "v1", "VAX-A"), item("i2", "v3", "VAX-Z")))

        val rows = vaccinationDao.getVaccinationsForPatientWithItems("p1").first()

        assertEquals(setOf("v1", "v2"), rows.map { it.visit.id }.toSet())
        assertEquals(1, rows.single { it.visit.id == "v1" }.items.size)
        assertEquals(emptyList<String>(), rows.single { it.visit.id == "v2" }.items.map { it.vaccineName })
    }

    @Test
    fun batchItemsRead_ordersByDateGivenDescending_likeTheOldQuery() = runBlocking {
        patientDao.insertPatient(patient("p1"))
        vaccinationDao.insertVaccination(visit("old", "p1", 1.0, "2026-01-01"))
        vaccinationDao.insertVaccination(visit("new", "p1", 1.0, "2026-12-31"))
        vaccinationDao.insertVaccination(visit("mid", "p1", 1.0, "2026-06-15"))

        assertEquals(
            listOf("new", "mid", "old"),
            vaccinationDao.getAllVaccinationsWithItems().first().map { it.visit.id }
        )
    }
}