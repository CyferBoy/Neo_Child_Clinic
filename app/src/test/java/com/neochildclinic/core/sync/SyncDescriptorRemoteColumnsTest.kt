package com.neochildclinic.core.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A remote DELETE filters on the REMOTE column name, which is not always the local Room PK:
 * inventory_transactions is `transactionId` in Room but `id` in Postgres. Sending the local
 * name made PostgREST answer PGRST100, the error was rethrown, and the item failed forever -
 * so stock-ledger rows the device deleted never left the server.
 *
 * The schema snapshot CSV committed alongside the migrations is the source of truth for the
 * remote column names, so assert every descriptor's remote column is really a column of that
 * table. This catches a new mismatch the day a descriptor is added.
 */
class SyncDescriptorRemoteColumnsTest {

    private val snapshotCandidates = listOf(
        File("src/main/java"),
        File("app/src/main/java")
    ).map { File(it, "../../../supabase/migrations") } +
        listOf(File("supabase/migrations"), File("../supabase/migrations"), File("../../supabase/migrations"))

    private fun snapshot(): File {
        snapshotCandidates.firstOrNull { it.isDirectory }
            ?.listFiles { f -> f.name.endsWith(".csv") }
            ?.firstOrNull()
            ?.let { return it }
        error("Could not find the schema snapshot CSV from ${File("").absolutePath}")
    }

    private fun remoteColumnsByTable(): Map<String, Set<String>> {
        val file = snapshot()
        val out = mutableMapOf<String, MutableSet<String>>()
        file.useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
                val cols = line.split(",")
                if (cols.size >= 3) {
                    out.getOrPut(cols[0].trim().removeSurrounding("\"")) { mutableSetOf() }
                        .add(cols[2].trim().removeSurrounding("\""))
                }
            }
        }
        return out
    }

    @Test
    fun `every remote delete column exists on the remote table`() {
        val remote = remoteColumnsByTable()
        assertTrue("snapshot parsed no tables", remote.isNotEmpty())

        val offenders = SYNC_ENTITY_REGISTRY.entries
            .mapNotNull { (entity, descriptor) ->
                val columns = remote[descriptor.tableName]
                    ?: return@mapNotNull null // table not in the snapshot; nothing to check
                if (descriptor.deleteFilterColumn in columns) null
                else "$entity (${descriptor.tableName}) deletes on '${descriptor.deleteFilterColumn}', " +
                    "which is not a column of that table (columns: ${columns.sorted().joinToString(", ")})"
            }
        assertTrue(
            "remote DELETE filters must use the Postgres column name:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
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