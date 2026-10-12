package com.neochildclinic.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards against BUG-001: a cleanup commit once deleted migration objects that looked
 * like dead code, and every device pinned at the dropped versions crashed on open
 * (Room throws "A migration from X to Y was required but not found").
 */
class AppDatabaseMigrationTest {

    @Test
    fun `migration chain is contiguous from 17 to DB_VERSION`() {
        val sorted = AppDatabase.MIGRATIONS.sortedBy { it.startVersion }

        assertEquals(
            "oldest supported device version must be 17 (V0.5.1 shipped floor 17)",
            17,
            sorted.first().startVersion
        )

        sorted.zipWithNext().forEach { (a, b) ->
            assertEquals(
                "gap: migration ${a.startVersion}->${a.endVersion} is not followed by ${b.startVersion}",
                a.endVersion,
                b.startVersion
            )
        }

        assertEquals(
            "chain must reach the @Database version (DB_VERSION)",
            AppDatabase.DB_VERSION,
            sorted.last().endVersion
        )

        assertEquals(
            "duplicate start versions would mean two migrations compete for one hop",
            sorted.size,
            sorted.map { it.startVersion }.toSet().size
        )
    }
}
