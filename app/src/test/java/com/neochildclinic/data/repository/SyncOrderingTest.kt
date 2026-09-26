package com.neochildclinic.data.repository

import com.neochildclinic.data.local.entity.SyncQueueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Characterization tests for the FK-safe ordering heuristic extracted from
// processNextItems (audit Finding 16 / roadmap item 6). These pin the behavior the
// extensive comments in that function describe: groups ordered by (min entity priority,
// min createdAt), DELETEs negating priority so children unlink/delete before parents, and
// within a shared transactionGroupId exact enqueue order preserved by (createdAt, queueId).
class SyncOrderingTest {

    // Mirror of getEntityPriority()'s table at extraction time; see roadmap item 7
    // (registry) which will own this single source of truth.
    private val priority: (String) -> Int = { name ->
        when (name) {
            "PATIENT", "VACCINE" -> 1
            "VACCINATION", "VISIT", "BATCH", "DOCTOR_WEEKLY_SLOT" -> 2
            "VACCINATION_ITEM", "CONSULTATION", "CONSULTATION_TODO", "VACCINATION_TODO", "WASTE", "BORROW", "DOCTOR_SLOT_EXCEPTION" -> 3
            "BORROW_RETURN", "INVENTORY_TRANSACTION", "FINANCE", "EXPENSE" -> 4
            "REMINDERS", "PATIENT_NOTE", "AUDIT_LOG", "PERSONAL_REMINDER" -> 5
            else -> 100
        }
    }

    private fun item(
        name: String,
        op: String,
        createdAt: String,
        queueId: Long,
        groupId: String? = null
    ) = SyncQueueEntity(
        queueId = queueId,
        entityName = name,
        entityId = "id-$name-$queueId",
        operation = op,
        transactionGroupId = groupId,
        createdAt = createdAt
    )

    private fun namesInOrder(groups: Map<String, List<SyncQueueEntity>>): List<String> =
        groups.values.flatten().map { it.entityId }

    @Test
    fun `CREATE groups order parent before child by min priority`() {
        val groups = orderPendingIntoGroups(
            listOf(
                item("VACCINATION", "CREATE", "2026-01-01T10:00:00Z", 2),
                item("PATIENT", "CREATE", "2026-01-01T10:00:00Z", 1)
            ),
            priority
        )
        assertEquals("PATIENT", groups.values.first()[0].entityName)
        assertEquals(listOf("id-PATIENT-1", "id-VACCINATION-2"), namesInOrder(groups))
    }

    @Test
    fun `DELETE groups order child before parent by negated priority`() {
        val groups = orderPendingIntoGroups(
            listOf(
                item("PATIENT", "DELETE", "2026-01-01T10:00:00Z", 1),
                item("VACCINATION", "DELETE", "2026-01-01T10:00:00Z", 2)
            ),
            priority
        )
        assertEquals("VACCINATION", groups.values.first()[0].entityName)
        assertEquals(listOf("id-VACCINATION-2", "id-PATIENT-1"), namesInOrder(groups))
    }

    @Test
    fun `mixed parent-DELETE and child-UPDATE sorts child-unlink before parent-delete`() {
        // The production incident the comments describe: FINANCE's UPDATE (+4) must not
        // sort after VACCINATION's DELETE (-2), which would delete patient_visits while
        // finance_transactions.visit_id still points at it.
        val groups = orderPendingIntoGroups(
            listOf(
                item("FINANCE", "UPDATE", "2026-01-01T10:00:00Z", 4),
                item("VACCINATION", "DELETE", "2026-01-01T10:05:00Z", 2)
            ),
            priority
        )
        assertEquals(listOf("id-VACCINATION-2", "id-FINANCE-4"), namesInOrder(groups))
    }

    @Test
    fun `same priority ties order groups by min createdAt`() {
        val groups = orderPendingIntoGroups(
            listOf(
                item("PATIENT", "CREATE", "2026-01-01T11:00:00Z", 3),
                item("PATIENT", "CREATE", "2026-01-01T10:00:00Z", 2)
            ),
            priority
        )
        assertEquals("id-PATIENT-2", groups.values.first()[0].entityId)
    }

    @Test
    fun `shared transactionGroupId preserves enqueue order not priority order`() {
        // deleteVaccination() queues unlink/child before parent under one group id.
        val groups = orderPendingIntoGroups(
            listOf(
                item("VACCINATION", "DELETE", "2026-01-01T10:00:00Z", 1, "tx-1"),
                item("FINANCE", "DELETE", "2026-01-01T10:01:00Z", 2, "tx-1")
            ),
            priority
        )
        assertTrue(groups.size == 1)
        assertEquals(
            listOf("id-VACCINATION-1", "id-FINANCE-2"),
            groups.values.first().map { it.entityId }
        )
    }

    @Test
    fun `items without group id become singleton groups sorted by priority`() {
        val groups = orderPendingIntoGroups(
            listOf(
                item("EXPENSE", "CREATE", "2026-01-01T10:00:00Z", 5),
                item("PATIENT", "CREATE", "2026-01-01T10:00:00Z", 1)
            ),
            priority
        )
        assertEquals(2, groups.size)
        assertEquals(listOf("id-PATIENT-1", "id-EXPENSE-5"), namesInOrder(groups))
    }
}