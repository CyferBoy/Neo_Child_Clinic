package com.neochildclinic.core.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the whole class of bug where code enqueues a sync item for an entity the uploader does
 * not know: SyncUploader throws "Unknown entity", the error is classified permanent, and every
 * occurrence fails forever with no way for the user to clear it.
 *
 * REMINDER_AUDIT was such a case - MergePatientsUseCase enqueued it on every patient merge
 * while SYNC_ENTITY_REGISTRY never had it, so each merge left a FAILED row on the Sync screen
 * and the app raised "Some changes could not be synced" after five of them.
 *
 * Reads the sources, so it catches the next one the day it is written.
 */
class SyncEntityRegistryTest {

    private val sourceDirs = listOf(File("src/main/java"), File("app/src/main/java"))

    private fun sourceRoot(): File = sourceDirs.firstOrNull { it.isDirectory }
        ?: error("Cannot find the app sources from ${File("").absolutePath}")

    private fun entityNamesInRegistry(): Set<String> =
        SYNC_ENTITY_REGISTRY.keys

    private fun enqueuedNames(): Map<String, List<String>> {
        val root = sourceRoot()
        val calls = mutableMapOf<String, MutableList<String>>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                Regex("""enqueue\(\s*(?:entityName\s*=\s*)?"([A-Z][A-Z_0-9]*)"""")
                    .findAll(text)
                    .forEach { match ->
                        calls.getOrPut(match.groupValues[1]) { mutableListOf() }
                            .add("${file.name}:${text.take(match.range.first).count { it == '\n' } + 1}")
                    }
            }
        return calls
    }

    @Test
    fun `every entity name passed to enqueue exists in the sync registry`() {
        val registered = entityNamesInRegistry()
        val offenders = enqueuedNames().filterKeys { it !in registered }
        assertTrue(
            "enqueue() is called with entities the uploader does not know, so those items " +
                "fail permanently and cannot be retried by the user:\n" +
                offenders.entries.joinToString("\n") { (entity, sites) -> "  $entity -> $sites" },
            offenders.isEmpty()
        )
    }

    @Test
    fun `the registry test actually scanned the sources`() {
        // Guards against a silently-empty scan making the test above vacuously pass.
        val enqueued = enqueuedNames()
        assertTrue("expected to find enqueue() call sites in ${sourceRoot()}", enqueued.isNotEmpty())
    }
}