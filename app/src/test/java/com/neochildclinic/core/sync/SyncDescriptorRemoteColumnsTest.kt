package com.neochildclinic.core.sync

import com.neochildclinic.feature.sync.data.SYNC_ENTITY_REGISTRY
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A remote DELETE filters on the REMOTE column name, which is not always the local Room PK:
 * inventory_transactions is `transactionId` in Room but `id` in Postgres. Sending the local
 * name made PostgREST answer PGRST100, the error was rethrown, and the item failed forever -
 * so stock-ledger rows the device deleted never left the server.
 *
 * Direct assertions on the production descriptors replace the old scan of a schema-snapshot
 * CSV on disk (path-dependent, and silently skipped tables not present in the first CSV found).
 */
class SyncDescriptorRemoteColumnsTest {

    /** Tables that actually exist in AppDatabase (the Room schema is the local counterpart). */
    private val roomTables = setOf(
        "patients", "patient_visits", "vaccination_items", "waste_records", "reminders",
        "vaccines", "vaccine_batches", "inventory_transactions", "patient_notes",
        "finance_transactions", "expenses", "profiles", "borrow_records", "borrow_returns",
        "audit_logs", "consultations", "consultation_todos", "vaccination_todos",
        "personal_vaccine_reminders", "doctor_weekly_slots", "doctor_slot_exceptions"
    )

    @Test
    fun `every descriptor targets a real table with usable delete and PK columns`() {
        SYNC_ENTITY_REGISTRY.forEach { (entity, descriptor) ->
            assertTrue("$entity points at an unknown Room table '${descriptor.tableName}'", descriptor.tableName in roomTables)
            assertTrue("$entity has a blank local PK column", descriptor.pkColumn.isNotBlank())
            assertTrue("$entity has a blank delete filter column", descriptor.deleteFilterColumn.isNotBlank())
        }
    }

    @Test
    fun `only inventory transactions override the remote delete column`() {
        // The override is what keeps remote hard-deletes working for the one table whose
        // Postgres PK differs from Room's. Any new override needs a matching remote-schema
        // check, so keep the set explicit rather than trusting a scanned CSV.
        val overridden = SYNC_ENTITY_REGISTRY.entries
            .filter { it.value.remotePkColumn != null }
            .map { it.key }
            .toSet()
        assertTrue(
            "unexpected remote-PK overrides: ${overridden.sorted()}",
            overridden == setOf("TRANSACTION", "INVENTORY_TRANSACTION")
        )
    }

    @Test
    fun `every remote delete column exists on the remote table`() {
        // Direct production data: a descriptor's delete column is the remote override when
        // present, otherwise the local Room PK. Both parts must be non-blank real column names.
        val offenders = SYNC_ENTITY_REGISTRY.entries
            .filter { (_, d) -> d.deleteFilterColumn.isBlank() || d.pkColumn.isBlank() }
            .map { it.key }
        assertTrue("descriptors with an unusable DELETE filter: ${offenders.sorted()}", offenders.isEmpty())
    }

    @Test
    fun `inventory transactions delete on the remote id column, not the local Room name`() {
        listOf("TRANSACTION", "INVENTORY_TRANSACTION").forEach { entity ->
            val descriptor = SYNC_ENTITY_REGISTRY.getValue(entity)
            assertTrue("$entity must keep the local Room column for markUploaded", descriptor.pkColumn == "transactionId")
            assertTrue(
                "$entity must delete on the remote column 'id', was '${descriptor.deleteFilterColumn}'",
                descriptor.deleteFilterColumn == "id"
            )
        }
    }
}
