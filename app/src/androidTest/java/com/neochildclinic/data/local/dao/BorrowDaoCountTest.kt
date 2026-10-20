package com.neochildclinic.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.BorrowEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dashboard's borrowed tile must count active borrow records in SQL and keep the exact
 * `isReturned = 0` predicate the list query uses (report item F2). Like the other DAO
 * tests, these need a real SQLite engine, so they run in the `android-instrumented` CI job.
 */
@RunWith(AndroidJUnit4::class)
class BorrowDaoCountTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BorrowDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.borrowDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun borrow(id: String, isReturned: Boolean) = BorrowEntity(
        id = id, doctorName = "d", vaccineId = "v", batchId = "b", borrowedDate = "2026-01-01",
        isReturned = isReturned
    )

    @Test
    fun `count matches the active list the old tile materialized`() = runBlocking {
        dao.upsertRecord(borrow("a1", isReturned = false))
        dao.upsertRecord(borrow("a2", isReturned = false))
        dao.upsertRecord(borrow("r1", isReturned = true))

        assertEquals(2, dao.getActiveBorrowCount().first())
        assertEquals(dao.getActiveBorrows().first().size, dao.getActiveBorrowCount().first())
    }

    @Test
    fun `empty table counts zero`() = runBlocking {
        assertEquals(0, dao.getActiveBorrowCount().first())
    }
}
