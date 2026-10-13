package com.neochildclinic.core.sync

import com.neochildclinic.data.local.entity.SyncQueueEntity
import org.junit.Assert.assertEquals
import org.junit.Test

// Characterization tests for the retry-tail slicing extracted from processGroup (audit
// finding 17b): a group failure must only classify the item that actually threw.
// Before the split, classifyAndRecordError burned the retry budget of the whole group -
// including items that were never attempted (after the failing index) and items whose
// queue rows were already deleted after a successful upload (before it), so five
// untried rows would end up FAILED carrying a sibling item's error text.
class SyncGroupFailureTest {

    private fun item(queueId: Long) = SyncQueueEntity(
        queueId = queueId,
        entityName = "PATIENT",
        entityId = "id-$queueId",
        operation = "UPDATE"
    )

    private val group = (1L..4L).map { item(it) }

    @Test
    fun `failure mid-group classifies only the item that threw`() {
        // Items 1-2 uploaded (queue rows deleted); item 3 threw; item 4 never attempted.
        // Only item 3 may have its retry budget spent - 4 must stay untouched and be
        // released back to PENDING by processGroup, not classified.
        assertEquals(listOf(group[2]), errorClassificationTail(group, 2))
    }

    @Test
    fun `failure before any upload classifies the whole group`() {
        assertEquals(group, errorClassificationTail(group, -1))
    }

    @Test
    fun `an out-of-range failure index is empty, not out of bounds`() {
        assertEquals(emptyList<SyncQueueEntity>(), errorClassificationTail(group, group.size))
        assertEquals(emptyList<SyncQueueEntity>(), errorClassificationTail(group, 99))
    }
}
