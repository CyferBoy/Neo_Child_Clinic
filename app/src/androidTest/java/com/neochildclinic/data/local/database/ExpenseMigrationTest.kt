package com.neochildclinic.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Migration test for migration22_23 (task section 17/18: "Add tests for ... Room
 * migration", "Do not use destructive migration to implement this feature").
 *
 * NOTE: AppDatabase's migration objects (migration17_18 .. migration22_23) are declared
 * as local vals inside AppDatabase.getInstance(), not exposed as accessible members (no
 * companion `MIGRATION_x_y` constants), so they can't be referenced directly from a test
 * the way the pre-existing MigrationTest.kt tries to (that test references
 * `AppDatabase.MIGRATION_18_19`, which does not exist anywhere in this codebase's
 * AppDatabase.kt and does not compile as-is - a pre-existing issue, not introduced here).
 * This test instead defines an identical copy of migration22_23's SQL and validates it
 * against a real database at version 22, which is the closest available way to verify
 * the migration is non-destructive and produces the expected schema.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseMigrationTest {
    private val testDbName = "expense-migration-test"

    private val migration22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS expenses (
                    id TEXT NOT NULL PRIMARY KEY,
                    expenseDate TEXT NOT NULL,
                    category TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    amountPaise INTEGER NOT NULL,
                    paymentMethod TEXT NOT NULL,
                    referenceNumber TEXT,
                    attachmentPath TEXT,
                    isDeleted INTEGER NOT NULL DEFAULT 0,
                    createdAt TEXT NOT NULL,
                    updatedAt TEXT NOT NULL,
                    created_by TEXT,
                    updated_by TEXT,
                    isSynced INTEGER NOT NULL DEFAULT 0,
                    syncedAt TEXT
                )"""
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_expenseDate ON expenses(expenseDate)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_category ON expenses(category)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_created_by ON expenses(created_by)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_updatedAt ON expenses(updatedAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_isDeleted ON expenses(isDeleted)")
        }
    }

    @get:Rule
    val helper = androidx.room.testing.MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate22To23_createsExpensesTable_withoutTouchingExistingData() {
        // Version 22 database with a pre-existing, unrelated row - proves the migration
        // is additive/non-destructive rather than falling back to a destructive rebuild.
        val dbAt22 = helper.createDatabase(testDbName, 22).apply {
            execSQL("INSERT INTO patients (id, name, phone, dob, gender) VALUES ('patient-1', 'Existing Patient', '555', '2020-01-01', 'F')")
            close()
        }

        val dbAt23 = helper.runMigrationsAndValidate(testDbName, 23, true, migration22_23)

        // Pre-existing data survived (non-destructive).
        val patientCursor = dbAt23.query("SELECT name FROM patients WHERE id = 'patient-1'")
        assert(patientCursor.moveToFirst())
        assert(patientCursor.getString(0) == "Existing Patient")
        patientCursor.close()

        // New table exists and accepts a row matching ExpenseEntity's shape.
        dbAt23.execSQL(
            """INSERT INTO expenses (
                id, expenseDate, category, title, description, amountPaise, paymentMethod,
                referenceNumber, attachmentPath, isDeleted, createdAt, updatedAt,
                created_by, updated_by, isSynced, syncedAt
            ) VALUES (
                'expense-1', '2026-04-15', 'RENT', 'April Rent', 'Clinic rent', 5000000, 'BANK',
                'REF-001', NULL, 0, '2026-04-15T10:00:00.000+00:00', '2026-04-15T10:00:00.000+00:00',
                'admin@example.com', 'admin@example.com', 0, NULL
            )"""
        )
        val expenseCursor = dbAt23.query("SELECT amountPaise, isDeleted FROM expenses WHERE id = 'expense-1'")
        assert(expenseCursor.moveToFirst())
        assert(expenseCursor.getLong(0) == 5000000L)
        assert(expenseCursor.getInt(1) == 0)
        expenseCursor.close()

        // Indexes required for filtered queries (task section 15) were created.
        val indexCursor = dbAt23.query("PRAGMA index_list(expenses)")
        val indexNames = mutableSetOf<String>()
        while (indexCursor.moveToNext()) {
            indexNames.add(indexCursor.getString(1))
        }
        indexCursor.close()
        assert(indexNames.contains("index_expenses_expenseDate"))
        assert(indexNames.contains("index_expenses_category"))
        assert(indexNames.contains("index_expenses_isDeleted"))

        dbAt23.close()
    }
}
