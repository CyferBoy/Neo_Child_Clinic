package com.neochildclinic.core.sync

import com.neochildclinic.feature.sync.data.SYNC_ENTITY_REGISTRY
import com.neochildclinic.feature.sync.domain.SyncEntityCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the whole class of bug where code enqueues a sync item for an entity the uploader does
 * not know: SyncUploader throws "Unknown entity", the error is classified permanent, and every
 * occurrence fails forever with no way for the user to clear it.
 *
 * REMINDER_AUDIT was such a case - MergePatientsUseCase enqueued it on every patient merge
 * while SYNC_ENTITY_REGISTRY never had it, so each merge left a FAILED row on the Sync screen
 * and the app raised "Some changes could not be synced" after five of them.
 *
 * Direct assertions on production data ([SyncEntityCatalog] vs [SYNC_ENTITY_REGISTRY]) replace
 * the old regex scan of source files, which depended on the working directory and could pass
 * vacuously when no sources were found.
 */
class SyncEntityRegistryTest {

    @Test
    fun `every entity name the app can enqueue exists in the sync registry`() {
        val enqueued = SyncEntityCatalog.ENQUEUED_ENTITY_NAMES
        val missing = enqueued - SYNC_ENTITY_REGISTRY.keys
        assertTrue(
            "these enqueueable entities have no uploader descriptor and would fail permanently:\n" +
                missing.sorted().joinToString("\n") { "  $it" },
            missing.isEmpty()
        )
    }

    @Test
    fun `the registry has no stale entity names`() {
        val stale = SYNC_ENTITY_REGISTRY.keys - SyncEntityCatalog.ENQUEUED_ENTITY_NAMES
        assertTrue(
            "registry entries with no enqueue site are dead configuration:\n" +
                stale.sorted().joinToString("\n") { "  $it" },
            stale.isEmpty()
        )
    }

    @Test
    fun `the entity catalog is not empty`() {
        // Guards against an empty catalog making the two tests above vacuously pass.
        assertTrue("expected the production catalog to list enqueueable entities", SyncEntityCatalog.ENQUEUED_ENTITY_NAMES.isNotEmpty())
    }

    @Test
    fun `every registered descriptor points at a real table with a positive priority`() {
        SYNC_ENTITY_REGISTRY.forEach { (entity, descriptor) ->
            assertTrue("$entity has a blank table name", descriptor.tableName.isNotBlank())
            assertTrue("$entity has a non-positive priority", descriptor.priority > 0)
            assertTrue("$entity has a blank local PK column", descriptor.pkColumn.isNotBlank())
        }
    }
}
