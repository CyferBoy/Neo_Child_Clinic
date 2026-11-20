package com.neochildclinic.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two writes behind "Add Patient" from Today's Patients, and the UHID uniqueness
 * the pull loop relies on. Needs a real SQLite engine -> androidTest.
 */
@RunWith(AndroidJUnit4::class)
class PatientLinkAndUhidDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun linksAConsultationTodoAndMarksItDirty() = runBlocking {
        db.patientTodoDao().insertConsultation(consultationTodo("c-1"))

        db.patientTodoDao().linkConsultationToPatient("c-1", "p-1", "2026-11-19T10:00:00")

        val linked = db.patientTodoDao().getConsultationTodoById("c-1")!!
        assertEquals("p-1", linked.patientId)
        assertEquals(false, linked.isSynced)
        assertEquals("2026-11-19T10:00:00", linked.updatedAt)
        assertEquals(null, db.patientTodoDao().getVaccinationTodoById("c-1")?.patientId)
    }

    @Test
    fun linksAVaccinationTodoAndMarksItDirty() = runBlocking {
        db.patientTodoDao().insertVaccination(vaccinationTodo("v-1"))

        db.patientTodoDao().linkVaccinationToPatient("v-1", "p-2", "2026-11-19T11:00:00")

        val linked = db.patientTodoDao().getVaccinationTodoById("v-1")!!
        assertEquals("p-2", linked.patientId)
        assertEquals(false, linked.isSynced)
        assertEquals("2026-11-19T11:00:00", linked.updatedAt)
    }

    @Test
    fun duplicateUhidIsRejected() = runBlocking {
        db.patientDao().upsertPatient(patient("p-1", "26-27/001"))
        try {
            db.patientDao().upsertPatient(patient("p-2", "26-27/001"))
            fail("a second patient with the same UHID must be rejected")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            assertTrue(e.message.orEmpty().isNotBlank())
        }
    }

    private fun consultationTodo(id: String) = ConsultationTodoEntity(
        id = id, name = "Asha Rao", mobile = "9876543210", address = "", todoDate = "2026-11-19"
    )

    private fun vaccinationTodo(id: String) = VaccinationTodoEntity(
        id = id, name = "Asha Rao", mobile = "9876543210", vaccineNames = "MMR",
        address = "", todoDate = "2026-11-19"
    )

    private fun patient(id: String, uhid: String) = PatientEntity(
        id = id, uhid = uhid, name = "Asha Rao", phone = "9876543210", dob = "2020-01-01",
        gender = "Female", isSynced = false
    )
}
