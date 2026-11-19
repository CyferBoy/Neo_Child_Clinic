package com.neochildclinic.transaction

import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.data.local.dao.SyncQueueDao
import com.neochildclinic.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * DB-02/SY-01/DB-03/CL-06 invariant: the pending-queue snapshot that guards a pull must be
 * read inside the same transaction as the pull. A row enqueued during that transaction is
 * visible to a guard read inside it; a snapshot taken before [TransactionRunner.run] is stale
 * and would let the pull overwrite the local edit. Mirrors SyncQueueDao.unsyncedIdSet's doc.
 */
class QueuedEditGuardVisibilityTest {

    private class InlineTransactionRunner : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    @Test
    fun `queued edit is visible to guard read inside the transaction`() = runBlocking {
        val pending = mutableSetOf<String>()
        val syncQueueDao = Proxy.newProxyInstance(
            SyncQueueDao::class.java.classLoader,
            arrayOf(SyncQueueDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "unsyncedIdSet" -> pending.toSet()
                "enqueue" -> {
                    pending.add((args!![0] as SyncQueueEntity).entityId)
                    null
                }
                else -> null
            }
        } as SyncQueueDao

        // The old pattern: snapshot taken before the transaction - it misses the edit.
        val staleGuard = syncQueueDao.unsyncedIdSet("PATIENT")

        // The fixed pattern: enqueue and guard read share one transaction.
        val guardInsideTransaction = InlineTransactionRunner().run {
            syncQueueDao.enqueue(
                SyncQueueEntity(entityName = "PATIENT", entityId = "patient-1", operation = "UPDATE")
            )
            syncQueueDao.unsyncedIdSet("PATIENT")
        }

        assertTrue("patient-1" in guardInsideTransaction)
        assertFalse("patient-1" in staleGuard)
    }
}
