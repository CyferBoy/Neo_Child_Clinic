package com.neochildclinic.feature.patient.data

import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import org.junit.Assert.assertEquals
import org.junit.Test

// Pull-merge decision for Today's Patient todos (report item F3): the previous refresh did
// one DAO lookup per remote row and REPLACE-wrote EVERY remote row, so even an unchanged
// pull rewrote the whole todos table (each write invalidates every flow watching it) and did
// a 2N round-trip of local reads. The decision must keep the per-row guard semantics:
//   - the queue guard wins (a row with an unpushed local edit - even a pending delete - is
//     never overwritten by the pull),
//   - a dirty (isSynced = false) local row is the device's version until it syncs,
//   - a synced row identical to the remote one is NOT rewritten (REPLACE writes the same
//     bytes; skipping it changes no end state, only the invalidation churn),
//   - a synced row that differs is overwritten with the remote copy marked synced.
class TodoUpsertsForRefreshTest {

    private fun consultation(
        id: String,
        isSynced: Boolean,
        status: String = "PENDING"
    ) = ConsultationTodoEntity(id = id, name = "Patient", mobile = "1", address = "a",
        todoDate = "2026-10-20", status = status, isSynced = isSynced)

    private fun merge(
        remote: List<ConsultationTodoEntity>,
        local: List<ConsultationTodoEntity>,
        unsynced: Set<String>
    ) = todoUpsertsForRefresh(
        remote = remote,
        localById = local.associateBy { it.id },
        unsyncedIdSet = unsynced,
        id = { it.id },
        isSynced = { it.isSynced },
        markedSynced = { it.copy(isSynced = true) }
    )

    @Test
    fun `a remote row with no local copy is inserted`() {
        val remote = listOf(consultation("r1", isSynced = true))
        val upserts = merge(remote, local = emptyList(), unsynced = emptySet())
        assertEquals(listOf(remote[0].copy(isSynced = true)), upserts)
    }

    @Test
    fun `a queue-unsynced row is never overwritten - including a pending delete`() {
        val remote = listOf(consultation("r1", isSynced = true))
        val upserts = merge(remote, local = emptyList(), unsynced = setOf("r1"))
        assertEquals(emptyList<ConsultationTodoEntity>(), upserts)
    }

    @Test
    fun `a dirty local row is the device's version and is not overwritten`() {
        val remote = listOf(consultation("r1", isSynced = true, status = "PENDING"))
        val local = listOf(consultation("r1", isSynced = false, status = "COMPLETED"))
        val upserts = merge(remote, local, unsynced = emptySet())
        assertEquals(emptyList<ConsultationTodoEntity>(), upserts)
    }

    @Test
    fun `a synced row identical to remote is not rewritten at all`() {
        val remote = listOf(consultation("r1", isSynced = true))
        val local = listOf(consultation("r1", isSynced = true))
        val upserts = merge(remote, local, unsynced = emptySet())
        // Skipping changes no end state: the values on disk would be byte-identical.
        assertEquals(emptyList<ConsultationTodoEntity>(), upserts)
    }

    @Test
    fun `a synced row differing from remote is overwritten with the remote copy`() {
        val remote = listOf(consultation("r1", isSynced = true, status = "COMPLETED"))
        val local = listOf(consultation("r1", isSynced = true, status = "PENDING"))
        val upserts = merge(remote, local, unsynced = emptySet())
        assertEquals(remote, upserts)
    }
}
