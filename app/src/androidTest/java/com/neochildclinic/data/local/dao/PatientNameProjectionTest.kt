package com.neochildclinic.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.PatientEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WidgetWorker's name lookup (DA-06 fix, widget batch): patient names must come from a
 * projection over exactly the due list's patient ids, not from the whole patients table.
 * Two properties matter: (1) rows for unknown ids are simply absent, (2) only id+name
 * return.
 *
 * Needs a real SQLite engine -> androidTest, CI `android-instrumented` job only.
 */
@RunWith(AndroidJUnit4::class)
class PatientNameProjectionTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PatientDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.patientDao()
        runBlocking {
            dao.upsertPatient(seededPatient("p-1", "Alpha One"))
            dao.upsertPatient(seededPatient("p-2", "Beta Two"))
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun seededPatient(id: String, name: String) = PatientEntity(
        id = id,
        name = name,
        phone = "",
        dob = "",
        gender = "",
        isSynced = true
    )

    @Test
    fun returnsNamesForExistingIdsAndSkipsUnknown() = runBlocking {
        val refs = dao.getNamesByIds(listOf("p-1", "missing-id"))
        assertEquals(1, refs.size)
        val ref = refs.single()
        assertEquals("p-1", ref.id)
        assertEquals("Alpha One", ref.name)
    }

    @Test
    fun emptyIdListReturnsNothing() = runBlocking {
        assertTrue(dao.getNamesByIds(emptyList()).isEmpty())
    }

    @Test
    fun duplicateIdsCollapseToDistinctRows() = runBlocking {
        val refs = dao.getNamesByIds(listOf("p-1", "p-1", "p-2"))
        assertEquals(2, refs.size)
        assertEquals(setOf("p-1", "p-2"), refs.map { it.id }.toSet())
    }
}
