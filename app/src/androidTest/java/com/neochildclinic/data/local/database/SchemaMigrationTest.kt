package com.neochildclinic.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Real migration coverage for the two hops whose schemas ARE exported (33.json / 34.json /
 * 35.json). MigrationTestHelper creates the starting DB from the shipped JSON, runs the
 * production migration from AppDatabase.MIGRATIONS, and validates the result against the
 * next JSON - so a hand-edited copy of the SQL cannot keep this test green.
 *
 * The 32->33 hop has no 32.json and is covered by DateGivenIndexMigrationTest instead.
 * The 22->23 hop used to have an @Ignore'd test whose 22.json/23.json were never exported;
 * that test was deleted rather than left permanently skipped.
 *
 * Uses JUnit assertions, not Kotlin's `assert` (a no-op under instrumentation, no -ea).
 */
@RunWith(AndroidJUnit4::class)
class SchemaMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    /** A row written by an older app version must survive every migration untouched. */
    private fun insertSentinelTodo(db: SupportSQLiteDatabase) {
        db.execSQL(
            """INSERT INTO consultation_todos
                (id, name, mobile, address, todo_date, status, created_at, updated_at, is_synced)
                VALUES ('sentinel-todo', 'Legacy sentinel', '555', 'Clinic', '2026-01-01',
                        'PENDING', '2026-01-01T00:00:00', '2026-01-01T00:00:00', 0)"""
        )
    }

    private fun indexNames(db: SupportSQLiteDatabase): Set<String> {
        val names = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
            while (c.moveToNext()) names.add(c.getString(0))
        }
        return names
    }

    @Test
    @Throws(IOException::class)
    fun migrate33To34_preservesSentinelAndBackfillsVisitTypeWithEmptyString() {
        helper.createDatabase(DB_33_TO_34, 33).apply {
            insertSentinelTodo(this)
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_33_TO_34, 34, true,
            AppDatabase.MIGRATIONS.first { it.startVersion == 33 }
        )

        migrated.query("SELECT name, visit_type FROM consultation_todos WHERE id = 'sentinel-todo'").use { c ->
            assertTrue("sentinel row must survive 33->34", c.moveToFirst())
            assertEquals("Legacy sentinel", c.getString(0))
            assertEquals("legacy rows must read as unset, not NULL", "", c.getString(1))
        }
        migrated.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate34To35_preservesSentinelAndCreatesAll27Indexes() {
        val before: Set<String>
        helper.createDatabase(DB_34_TO_35, 34).apply {
            insertSentinelTodo(this)
            before = indexNames(this)
            close()
        }
        assertTrue("fixture must start without the v35 indices", ADDED_IN_35.none { it in before })

        val migrated = helper.runMigrationsAndValidate(
            DB_34_TO_35, 35, true,
            AppDatabase.MIGRATIONS.first { it.startVersion == 34 }
        )

        migrated.query("SELECT name FROM consultation_todos WHERE id = 'sentinel-todo'").use { c ->
            assertTrue("sentinel row must survive 34->35", c.moveToFirst())
            assertEquals("Legacy sentinel", c.getString(0))
        }

        val after = indexNames(migrated)
        val missing = ADDED_IN_35 - after
        assertTrue("migration34_35 did not create: ${missing.sorted()}", missing.isEmpty())
        assertEquals("expected exactly the 27 shipped indices to be added", 27, (after - before).size)
        migrated.close()
    }

    private companion object {
        const val DB_33_TO_34 = "schema-migration-33-34"
        const val DB_34_TO_35 = "schema-migration-34-35"

        /** The 27 indices added by migration34_35, mirroring the diff of 34.json -> 35.json. */
        val ADDED_IN_35 = setOf(
            "index_patient_visits_doctorId",
            "index_patient_visits_availabilitySlotId",
            "index_patient_visits_createdAt",
            "index_consultations_doctorId",
            "index_consultations_availabilitySlotId",
            "index_consultations_createdAt",
            "index_consultation_todos_doctor_id",
            "index_consultation_todos_availability_slot_id",
            "index_consultation_todos_created_at",
            "index_vaccination_todos_doctor_id",
            "index_vaccination_todos_availability_slot_id",
            "index_vaccination_todos_created_at",
            "index_inventory_transactions_patientId",
            "index_inventory_transactions_status",
            "index_inventory_transactions_timestamp",
            "index_waste_records_batchId",
            "index_sync_queue_entityName_entityId",
            "index_sync_queue_createdAt",
            "index_profiles_employeeId",
            "index_profiles_role",
            "index_reminders_priority",
            "index_reminders_createdAt",
            "index_vaccine_batches_status",
            "index_inventory_deductions_batchId",
            "index_inventory_deductions_vaccineId",
            "index_personal_vaccine_reminders_created_at",
            "index_expenses_createdAt"
        )
    }
}
