package com.neochildclinic.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.BorrowEntity
import com.neochildclinic.data.local.entity.BorrowReturnEntity
import com.neochildclinic.data.local.entity.ConsultationEntity
import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.data.local.entity.VaccinationItemEntity
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.data.local.entity.VaccineEntity
import com.neochildclinic.data.local.entity.VisitEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A parent row written with @Insert(REPLACE) is a real SQLite INSERT OR REPLACE - DELETE +
 * INSERT - so every ForeignKey.CASCADE on that row fires and its local children are deleted
 * until a later pull re-imports the synced ones. That is the write path of every pull loop
 * (PatientRepositoryImpl, VaccinationRepositoryImpl, InventoryRepositoryImpl,
 * BorrowRepositoryImpl), of the edit paths (patient update, visit edit, consultation edit),
 * of the clinic-ID migration worker and of the conflict downloads in SyncUploader.
 *
 * The @Upsert methods cover those sites; these tests pin that they update in place without
 * touching children, and that a clinicId collision can never delete another patient.
 */
@RunWith(AndroidJUnit4::class)
class PullUpsertPreservesChildrenTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun patient(id: String, clinicId: String? = null) = PatientEntity(
        id = id,
        name = "Patient $id",
        phone = "9000000$id",
        dob = "2020-01-01",
        gender = "MALE",
        patientClinicId = clinicId
    )

    private fun vaccine(id: String) = VaccineEntity(
        id = id,
        type = "VACCINE",
        brandName = id,
        companyName = "Test Co"
    )

    private fun batch(id: String, vaccineId: String) = VaccineBatchEntity(
        batchId = id,
        vaccineId = vaccineId,
        batchNumber = "LOT-$id",
        manufacturer = "Test Co",
        purchaseDate = "2026-01-01",
        expiryDate = "2027-01-01",
        supplier = "Test Co",
        purchaseQuantity = 100,
        remainingQuantity = 100,
        purchaseCost = 1.0,
        sellingPrice = 2.0
    )

    private fun visit(id: String, patientId: String) = VisitEntity(
        id = id,
        patientId = patientId,
        dateGiven = "2026-04-01",
        totalPaid = 500.0,
        receiptNumber = "R-$id"
    )

    private fun item(id: String, visitId: String, vaccineId: String, batchId: String) = VaccinationItemEntity(
        id = id,
        vaccinationId = visitId,
        vaccineId = vaccineId,
        vaccineName = "VAX-A",
        batchId = batchId
    )

    private fun consultation(id: String, patientId: String, visitId: String) = ConsultationEntity(
        id = id,
        visitId = visitId,
        patientId = patientId,
        date = "2026-04-01",
        amount = 500.0
    )

    private fun reminder(id: String, patientId: String, visitId: String) = ReminderEntity(
        id = id,
        patientId = patientId,
        originalVisitId = visitId,
        vaccineName = "VAX-A",
        dueDate = "2026-06-01",
        status = "ACTIVE"
    )

    private fun borrow(id: String, vaccineId: String, batchId: String) = BorrowEntity(
        id = id,
        doctorName = "Dr Test",
        vaccineId = vaccineId,
        batchId = batchId,
        borrowedDate = "2026-04-01"
    )

    private fun borrowReturn(id: String, borrowId: String, batchId: String) = BorrowReturnEntity(
        id = id,
        borrowRecordId = borrowId,
        batchId = batchId,
        quantity = 1,
        returnedDate = "2026-04-02"
    )

    private suspend fun seedClinicalGraph() {
        db.vaccineDao().insertVaccine(vaccine("vac-a"))
        db.vaccineDao().insertBatch(batch("batch-a", "vac-a"))
        db.patientDao().insertPatient(patient("p1", clinicId = "NEO-1"))
        db.vaccinationDao().insertVaccination(visit("v1", "p1"))
        db.vaccinationItemDao().insertItems(listOf(item("i1", "v1", "vac-a", "batch-a")))
        db.consultationDao().insertConsultation(consultation("c1", "p1", "v1"))
        db.dueReminderDao().insertReminder(reminder("r1", "p1", "v1"))
    }

    @Test
    fun upsertPatient_keepsVisitsItemsConsultationsAndReminders() = runBlocking {
        seedClinicalGraph()

        // PatientRepositoryImpl.refreshPatients() and addPatient(isUpdate = true).
        db.patientDao().upsertPatient(
            patient("p1", clinicId = "NEO-1").copy(name = "Renamed", isSynced = true)
        )

        assertEquals("Renamed", db.patientDao().getPatientById("p1")?.name)
        assertNotNull("visit was cascaded away", db.vaccinationDao().getVaccinationById("v1"))
        assertEquals(1, db.vaccinationItemDao().getItemsForVaccination("v1").first().size)
        assertNotNull("consultation was cascaded away", db.consultationDao().getConsultationById("c1"))
        assertNotNull("reminder was cascaded away", db.dueReminderDao().getReminderById("r1"))
    }

    @Test
    fun upsertVisit_keepsItemsAndConsultations() = runBlocking {
        seedClinicalGraph()

        // The visit edit (VaccinationRepositoryImpl) and the consultation edit
        // (ConsultationRepositoryImpl) both rewrite the linked visit row.
        db.vaccinationDao().upsertVaccination(visit("v1", "p1").copy(totalPaid = 900.0, isSynced = true))

        assertEquals(900.0, db.vaccinationDao().getVaccinationById("v1")?.totalPaid)
        assertEquals(1, db.vaccinationItemDao().getItemsForVaccination("v1").first().size)
        assertNotNull("consultation was cascaded away", db.consultationDao().getConsultationById("c1"))
    }

    @Test
    fun upsertVaccineAndBatch_keepItemsAndBorrows() = runBlocking {
        seedClinicalGraph()
        db.borrowDao().insertRecord(borrow("b1", "vac-a", "batch-a"))
        db.borrowReturnDao().insert(borrowReturn("br1", "b1", "batch-a"))

        db.vaccineDao().upsertVaccine(vaccine("vac-a").copy(brandName = "Renamed"))
        db.vaccineDao().upsertBatch(batch("batch-a", "vac-a").copy(remainingQuantity = 42))

        assertEquals("Renamed", db.vaccineDao().getVaccineById("vac-a")?.brandName)
        assertEquals(42, db.vaccineDao().getBatchById("batch-a")?.remainingQuantity)
        assertEquals(1, db.vaccinationItemDao().getItemsForVaccination("v1").first().size)
        assertNotNull("borrow record was cascaded away", db.borrowDao().getRecordById("b1"))
        assertNotNull("borrow return was cascaded away", db.borrowReturnDao().getById("br1"))
    }

    @Test
    fun upsertBorrowRecord_keepsItsReturns() = runBlocking {
        seedClinicalGraph()
        db.borrowDao().insertRecord(borrow("b1", "vac-a", "batch-a"))
        db.borrowReturnDao().insert(borrowReturn("br1", "b1", "batch-a"))

        db.borrowDao().upsertRecord(borrow("b1", "vac-a", "batch-a").copy(notes = "updated"))

        assertNotNull("borrow return was cascaded away", db.borrowReturnDao().getById("br1"))
    }

    // Pins the hazard the upserts exist for: REPLACE deletes the parent row and CASCADE does
    // the rest. If this stops deleting, the pull/edit call sites can be simplified again.
    @Test
    fun legacyReplace_stillDeletesChildren() = runBlocking {
        seedClinicalGraph()

        db.patientDao().insertPatient(patient("p1", clinicId = "NEO-1").copy(name = "Replaced"))

        assertNull("REPLACE deleted the patient and cascaded its visit", db.vaccinationDao().getVaccinationById("v1"))
        assertEquals(0, db.vaccinationItemDao().getItemsForVaccination("v1").first().size)
    }

    // What REPLACE did here was delete the patient that already held the clinicId. The pull
    // resolves collisions before writing, so this is a safety net rather than a live path.
    @Test
    fun upsertPatient_clinicIdCollision_neverDeletesTheOtherPatient() = runBlocking {
        db.patientDao().insertPatient(patient("p1", clinicId = "NEO-1"))
        db.patientDao().insertPatient(patient("p2", clinicId = "NEO-2"))

        // The write may be skipped or may surface the unique-index error - both are fine.
        runCatching { db.patientDao().upsertPatient(patient("p2", clinicId = "NEO-1")) }

        assertNotNull("the other patient was deleted by the collision", db.patientDao().getPatientById("p1"))
        assertEquals("NEO-1", db.patientDao().getPatientById("p1")?.patientClinicId)
    }
}
