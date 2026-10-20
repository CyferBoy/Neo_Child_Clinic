package com.neochildclinic.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.ReminderSyncRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO ground truth for the RemotePrune wiring (Phase 2 Batch 1, NCC-023 / DA-01): the
 * id/synced projections and delete calls every repository refresh now runs must behave as
 * the pruner assumes. Three properties:
 *  1. synced-flag projections read the RIGHT local column per entity (the schema mixes
 *     `isSynced` and `is_synced` local column names across entities),
 *  2. deleting a prune-missing patientById means Room FKs cascade its child rows away,
 *  3. RemotePrune's REMINDERS identity variant holds against real projection data
 *     (local PK != remote PK; remote identity tracked in serverId).
 *
 * Needs a real SQLite engine -> androidTest, CI `android-instrumented` job only.
 */
@RunWith(AndroidJUnit4::class)
class RemotePruneDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var patientDao: PatientDao
    private lateinit var vaccinationDao: VaccinationDao
    private lateinit var dueReminderDao: DueReminderDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        patientDao = db.patientDao()
        vaccinationDao = db.vaccinationDao()
        dueReminderDao = db.dueReminderDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun patient(id: String, name: String, synced: Boolean) = PatientEntity(
        id = id,
        name = name,
        phone = "",
        dob = "",
        gender = "",
        isSynced = synced
    )

    private fun visit(id: String, patientId: String, synced: Boolean) = com.neochildclinic.data.local.entity.VisitEntity(
        id = id,
        patientId = patientId,
        dateGiven = "",
        isSynced = synced
    )

    @Test
    fun patientProjectionsUseIsSyncedLocalColumn() = runBlocking {
        patientDao.upsertPatient(patient("keep", "Kept", synced = true))
        patientDao.upsertPatient(patient("drift", "Drift", synced = true))
        patientDao.upsertPatient(patient("dirty", "Dirty", synced = false))

        assertEquals(setOf("keep", "drift", "dirty"), patientDao.getPatientIds().toSet())
        assertEquals(setOf("keep", "drift"), patientDao.getSyncedPatientIds().toSet())
    }

    @Test
    fun deletionCascadesVisitChildrenBecauseAllFksAreCascade() = runBlocking {
        patientDao.upsertPatient(patient("gone", "Gone", synced = true))
        vaccinationDao.upsertVaccination(visit("v-1", "gone", synced = true))

        patientDao.deletePatient("gone")

        assertNull(patientDao.getPatientById("gone"))
        assertNull(vaccinationDao.getVaccinationById("v-1"))
    }

    @Test
    fun deleteVaccinationOfMissingIdIsANoOp() = runBlocking {
        vaccinationDao.deleteVaccination("never-existed")
        assertTrue(vaccinationDao.getVisitIds().isEmpty())
    }

    private fun reminder(localId: String, serverId: String, synced: Boolean) = runBlocking {
        // ReminderEntity requires the full payload; the fields the prune logic reads are
        // id / serverId / isSynced, everything else is house-default placeholder.
        com.neochildclinic.data.local.entity.ReminderEntity(
            id = localId,
            patientId = "p-1",
            originalVisitId = "v-1",
            vaccineName = "BCG",
            type = "VACCINATION",
            dueDate = "2030-01-01",
            status = "ACTIVE",
            reminderEnabled = true,
            isSynced = synced,
            serverId = serverId
        )
    }

    @Test
    fun reminderProjectionMapsServerIdIdentity() = runBlocking {
        dueReminderDao.insertReminder(reminder("loc-1", "srv-1", synced = true))
        dueReminderDao.insertReminder(reminder("loc-2", "srv-2", synced = false))

        val refs = dueReminderDao.getReminderSyncRefs().associateBy { it.localId }
        assertEquals(
            ReminderSyncRef(localId = "loc-1", serverId = "srv-1", synced = true),
            refs["loc-1"]
        )
        assertEquals(
            ReminderSyncRef(localId = "loc-2", serverId = "srv-2", synced = false),
            refs["loc-2"]
        )
    }
}
