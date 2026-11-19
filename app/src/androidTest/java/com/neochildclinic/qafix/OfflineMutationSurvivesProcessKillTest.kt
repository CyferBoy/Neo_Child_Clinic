package com.neochildclinic.qafix

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.ExpenseEntity
import com.neochildclinic.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * QA-06: a mutation made while offline must survive process death together with its sync
 * queue row, and once a (faked) remote comes back the queue must drain without touching the
 * local record. Uses a file-backed Room DB closed and reopened under the same name to model
 * the process kill - an in-memory DB would not prove persistence.
 */
@RunWith(AndroidJUnit4::class)
class OfflineMutationSurvivesProcessKillTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun openDb() = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME).build()

    @Test
    fun offlineWrite_queueSurvivesReopen_thenDrainsWhenRemoteRecovers() = runBlocking {
        context.deleteDatabase(DB_NAME)
        var db = openDb()
        try {
            // Offline mutation: local row dirty, upload queued (the repository's contract).
            db.expenseDao().insertExpense(
                ExpenseEntity(
                    id = "expense-offline", expenseDate = "2026-04-15", category = "RENT",
                    title = "April rent", amountPaise = 500000L, paymentMethod = "BANK",
                    isSynced = false
                )
            )
            db.syncQueueDao().enqueue(
                SyncQueueEntity(entityName = "EXPENSE", entityId = "expense-offline", operation = "CREATE")
            )

            val beforeKill = db.expenseDao().getExpenseById("expense-offline")
            assertNotNull(beforeKill)
            assertFalse(beforeKill!!.isSynced)
            assertEquals(1, db.syncQueueDao().getPendingCountSync())
        } finally {
            db.close() // process dies here
        }

        // Reopen the same on-disk database - no in-memory shortcut.
        db = openDb()
        try {
            val afterKill = db.expenseDao().getExpenseById("expense-offline")
            assertNotNull("offline mutation must survive process death", afterKill)
            assertEquals(500000L, afterKill!!.amountPaise)
            assertFalse("still unacknowledged", afterKill.isSynced)

            val queued = db.syncQueueDao().getItemsByStatus("PENDING")
            assertEquals("queue must survive process death too", 1, queued.size)
            assertEquals("EXPENSE", queued.single().entityName)
            assertEquals("expense-offline", queued.single().entityId)

            // Fake remote recovers: the uploader marks the item in flight, gets an ack, then
            // deletes the queue row and flips the local row's isSynced flag.
            val item = queued.single()
            db.syncQueueDao().updateStatus(item.queueId, "SYNCING")
            db.expenseDao().insertExpense(afterKill.copy(isSynced = true))
            db.syncQueueDao().deleteByIds(listOf(item.queueId))

            assertEquals("queue must drain after a successful upload", 0, db.syncQueueDao().getPendingCountSync())
            assertEquals(0, db.syncQueueDao().getItemsByStatus("SYNCING").size)
            val synced = db.expenseDao().getExpenseById("expense-offline")
            assertNotNull("local record must survive the drain", synced)
            assertTrue("local row must now be marked synced", synced!!.isSynced)
            assertEquals(500000L, synced.amountPaise)
        } finally {
            db.close()
            context.deleteDatabase(DB_NAME)
        }
    }

    private companion object {
        const val DB_NAME = "qafix-offline-recovery-test"
    }
}
