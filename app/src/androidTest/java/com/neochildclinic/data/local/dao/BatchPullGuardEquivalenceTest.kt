package com.neochildclinic.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.PatientEntity
import com.neochildclinic.data.local.entity.SyncQueueEntity
import com.neochildclinic.domain.model.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pull loops answer "is this remote row superseded by a queued local edit?" with one
 * bulk query per entity name instead of one SELECT EXISTS per remote row, and the patient
 * pull replaced its per-row clinic-id lookup with one full read of the patients table.
 *
 * These tests pin that the bulk forms answer exactly the same question as the per-row
 * forms they replaced. A different predicate, a missing entity name or a broader/narrower
 * rows-write guard would silently roll back queued local edits on the next pull.
 */
@RunWith(AndroidJUnit4::class)
class BatchPullGuardEquivalenceTest {

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

    @Test
    fun bulkUnsyncedIdsMatchPerIdIsUnsynced() = runBlocking {
        val queue = db.syncQueueDao()
        // One row per status a pull can encounter, plus the same entityId under a
        // different entityName (pruning the name out of the bulk query must not pass).
        queue.enqueue(SyncQueueEntity(entityName = "PATIENT", entityId = "p-pending", operation = "UPDATE", status = SyncStatus.PENDING.name))
        queue.enqueue(SyncQueueEntity(entityName = "PATIENT", entityId = "p-syncing", operation = "UPDATE", status = SyncStatus.SYNCING.name))
        queue.enqueue(SyncQueueEntity(entityName = "PATIENT", entityId = "p-failed", operation = "UPDATE", status = SyncStatus.FAILED.name))
        queue.enqueue(SyncQueueEntity(entityName = "PATIENT", entityId = "p-synced", operation = "UPDATE", status = SyncStatus.SYNCED.name))
        queue.enqueue(SyncQueueEntity(entityName = "VACCINATION", entityId = "p-pending", operation = "UPDATE"))

        val bulk = queue.unsyncedIdSet("PATIENT")
        assertEquals(setOf("p-pending", "p-syncing", "p-failed"), bulk)

        for (id in listOf("p-pending", "p-syncing", "p-failed", "p-synced", "p-absent")) {
            assertEquals("bulk guard disagrees with isUnsynced($id)", queue.isUnsynced("PATIENT", id), id in bulk)
        }
        assertEquals(emptySet<String>(), queue.unsyncedIdSet("NO_SUCH_ENTITY"))
    }

    @Test
    fun patientSnapshotSupportsTheBatchedLookups() = runBlocking {
        val dao = db.patientDao()
        dao.upsertPatient(patient("p1", clinicId = "NEO-0001"))
        dao.upsertPatient(patient("p2", clinicId = null))
        dao.upsertPatient(patient("p3", clinicId = "TEMP-p3"))

        // The pull builds localById and the clinic-id owner map from this one read.
        val snapshot = dao.getAllPatientsSnapshot()
        assertEquals(setOf("p1", "p2", "p3"), snapshot.map { it.id }.toSet())

        val localById = snapshot.associateBy { it.id }
        for (id in listOf("p1", "p2", "p3")) {
            assertEquals(dao.getPatientById(id), localById[id])
        }

        val ownerByClinicId = snapshot
            .mapNotNull { p -> p.patientClinicId?.takeIf { it.isNotBlank() }?.let { it to p.id } }
            .toMap()
        assertEquals(dao.getPatientByClinicId("NEO-0001")?.id, ownerByClinicId["NEO-0001"])
        assertEquals(dao.getPatientByClinicId("TEMP-p3")?.id, ownerByClinicId["TEMP-p3"])
        assertNull(dao.getPatientByClinicId("NEO-9999"))
        assertNull(ownerByClinicId["NEO-9999"])
    }

    private fun patient(id: String, clinicId: String?) = PatientEntity(
        id = id,
        name = "Patient $id",
        phone = "9000000$id",
        dob = "2020-01-01",
        gender = "MALE",
        patientClinicId = clinicId
    )
}
