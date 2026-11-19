package com.neochildclinic.archfix

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Compile-time boundary guard for the repository-contract untangling: `core` and `domain`
 * are shared layers and must not import feature, app, data or Android packages.
 * Any deliberate exception must be documented here and added to the allow-list below.
 */
class ImportBoundaryTest {

    // Documented exceptions, keyed by "<layer file>:<import prefix>". None are kept today.
    private val allowed = emptySet<String>()

    @Test
    fun coreDoesNotImportFeatureOrApp() {
        assertNoForbiddenImports(
            layer = "core",
            forbidden = listOf("com.neochildclinic.feature.", "com.neochildclinic.app.")
        )
    }

    @Test
    fun domainDoesNotImportFeatureDataOrAndroid() {
        assertNoForbiddenImports(
            layer = "domain",
            forbidden = listOf(
                "com.neochildclinic.feature.",
                "com.neochildclinic.data.",
                "android.",
                "androidx."
            )
        )
    }

    private fun assertNoForbiddenImports(layer: String, forbidden: List<String>) {
        val root = mainSourceRoot()
        val violations = mutableListOf<String>()
        File(root, layer).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, raw ->
                        val line = raw.trim()
                        if (!line.startsWith("import ")) return@forEachIndexed
                        val imported = line.substringAfter("import ").substringBefore(' ').trim()
                        val hit = forbidden.firstOrNull { imported.startsWith(it) } ?: return@forEachIndexed
                        val key = "${file.relativeTo(root)}:$imported"
                        if (key !in allowed) {
                            violations += "${file.relativeTo(root)}:${index + 1} imports $imported"
                        }
                    }
                }
            }
        assertTrue(
            "$layer must not import ${forbidden.joinToString()}; violations:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    private fun mainSourceRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(dir, "app/src/main/java/com/neochildclinic")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile ?: return dir
        }
        error("Could not locate app/src/main/java/com/neochildclinic from ${System.getProperty("user.dir")}")
    }
}
