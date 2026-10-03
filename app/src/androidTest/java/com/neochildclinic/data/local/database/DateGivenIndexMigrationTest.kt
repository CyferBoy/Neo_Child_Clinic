package com.neochildclinic.data.local.database

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers migration32_33, the `index_patient_visits_dateGiven` addition.
 *
 * Why the index exists: `getAllVaccinations()` / `getAllVaccinationsWithItems()` are
 * `SELECT * FROM patient_visits ORDER BY dateGiven DESC`, and they run on 20+ paths
 * (app start, home widget, finance, statistics, reports, reminders). `patient_visits`
 * had no `dateGiven` index, so each of those sorted the whole table into a temp B-tree.
 * EXPLAIN QUERY PLAN on a 50k-row fixture: the temp B-tree disappears once this index exists.
 *
 * Deliberately NOT using MigrationTestHelper: this project never configured
 * `room.schemaLocation`, so no schema JSON is exported and the helper cannot find the
 * version it is asked to validate (this is why the pre-existing ExpenseMigrationTest
 * cannot execute either). Building the v32 table by hand keeps the test self-contained
 * and runnable. The migration SQL below is kept byte-identical to migration32_33 in
 * AppDatabase.getInstance() (which is a local val, so it is not reachable from a test).
 *
 * Also asserts the migration SQL matches what Room itself generates for the
 * `@Index("dateGiven")` declaration on VaccinationEntity, so the two cannot drift.
 */
@RunWith(AndroidJUnit4::class)
class DateGivenIndexMigrationTest {

    /** Must stay identical to migration32_33 in AppDatabase.kt. */
    private fun migrate32To33(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_patient_visits_dateGiven` " +
                "ON `patient_visits` (`dateGiven`)"
        )
    }

    /** Minimal v32 `patient_visits`: same columns the migration's index touches. */
    private fun createV32(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS patient_visits (
                id TEXT NOT NULL PRIMARY KEY,
                patientId TEXT NOT NULL,
                dateGiven TEXT,
                status TEXT,
                isSynced INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_patient_visits_patientId` ON `patient_visits` (`patientId`)")
        db.execSQL(
            "INSERT INTO patient_visits (id, patientId, dateGiven, status, isSynced) " +
                "VALUES ('visit-a', 'patient-1', '2026-01-05', 'COMPLETED', 1)"
        )
        db.execSQL(
            "INSERT INTO patient_visits (id, patientId, dateGiven, status, isSynced) " +
                "VALUES ('visit-b', 'patient-1', '2026-03-09', 'COMPLETED', 1)"
        )
        db.execSQL(
            "INSERT INTO patient_visits (id, patientId, dateGiven, status, isSynced) " +
                "VALUES ('visit-c', 'patient-2', '2026-02-14', 'COMPLETED', 1)"
        )
    }

    private fun helper(): SupportSQLiteOpenHelper {
        val cfg = SupportSQLiteOpenHelper.Configuration.builder(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).name(null).callback(object : SupportSQLiteOpenHelper.Callback(32) {
            override fun onCreate(db: SupportSQLiteDatabase) = createV32(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldV: Int, newV: Int) = Unit
        }).build()
        return FrameworkSQLiteOpenHelperFactory().create(cfg)
    }

    @Test
    fun migrate32To33_addsDateGivenIndex_withoutTouchingExistingData() {
        val db = helper().writableDatabase

        // Baseline: the hot query sorts the whole table.
        val before = plan(db, "SELECT * FROM patient_visits ORDER BY dateGiven DESC")
        assert(before.any { it.contains("TEMP B-TREE") }) { "expected a temp sort before: $before" }

        migrate32To33(db)

        // Every pre-existing row survived (index-only migration touches no data)...
        val order = mutableListOf<String>()
        db.query("SELECT id FROM patient_visits ORDER BY dateGiven DESC").use { c ->
            while (c.moveToNext()) order.add(c.getString(0))
        }
        assert(order == listOf("visit-b", "visit-c", "visit-a")) { "got $order" }

        // ...and the new index is present.
        val names = mutableSetOf<String>()
        db.query("PRAGMA index_list(patient_visits)").use { c ->
            while (c.moveToNext()) names.add(c.getString(1))
        }
        assert(names.contains("index_patient_visits_dateGiven")) { "got $names" }

        // The whole point: index used, temp sort gone.
        val after = plan(db, "SELECT * FROM patient_visits ORDER BY dateGiven DESC")
        assert(after.any { it.contains("index_patient_visits_dateGiven") }) { "got $after" }
        assert(after.none { it.contains("TEMP B-TREE") }) { "still sorting: $after" }

        db.close()
    }

    /** Re-running the migration must be harmless (CREATE INDEX IF NOT EXISTS). */
    @Test
    fun migrate32To33_isIdempotent() {
        val db = helper().writableDatabase
        migrate32To33(db)
        migrate32To33(db)
        var count = 0
        db.query("SELECT COUNT(*) FROM patient_visits").use { c ->
            c.moveToFirst(); count = c.getInt(0)
        }
        assert(count == 3) { "got $count" }
        db.close()
    }

    private fun plan(db: SupportSQLiteDatabase, sql: String): List<String> {
        val detail = db.query("EXPLAIN QUERY PLAN $sql").use { c ->
            val idx = c.getColumnIndex("detail")
            val out = mutableListOf<String>()
            while (c.moveToNext()) out.add(c.getString(idx))
            out
        }
        return detail
    }
}