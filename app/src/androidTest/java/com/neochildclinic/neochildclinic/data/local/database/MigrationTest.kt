package com.neochildclinic.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate18To19() {
        // Create earliest version of the database if needed, or version 18
        var db = helper.createDatabase(TEST_DB, 18).apply {
            // Prepare data for version 18
            // 1. Insert patient with missing clinic ID
            execSQL("INSERT INTO patients (id, name, phone, dob, gender) VALUES ('uuid-1', 'Patient One', '123', '2020-01-01', 'M')")
            
            // 2. Insert patients with duplicate clinic ID
            execSQL("INSERT INTO patients (id, patientClinicId, name, phone, dob, gender) VALUES ('uuid-2', 'ABC001', 'Patient Two', '456', '2020-02-02', 'F')")
            execSQL("INSERT INTO patients (id, patientClinicId, name, phone, dob, gender) VALUES ('uuid-3', 'ABC001', 'Patient Three', '789', '2020-03-03', 'M')")
            
            // 3. Insert legacy reminders
            // ... insert into due_reminders etc. if version 18 had them ...
            
            close()
        }

        // Re-open the database with version 19 and provide MIGRATION_18_19
        db = helper.runMigrationsAndValidate(TEST_DB, 19, true, AppDatabase.MIGRATION_18_19)

        // Verify data repairs
        val cursor1 = db.query("SELECT patientClinicId FROM patients WHERE id = 'uuid-1'")
        cursor1.moveToFirst()
        assert(cursor1.getString(0) == "uuid-1") // Repaired from NULL/empty to ID
        cursor1.close()

        val cursor2 = db.query("SELECT patientClinicId FROM patients WHERE id = 'uuid-2'")
        cursor2.moveToFirst()
        val id2 = cursor2.getString(0)
        cursor2.close()

        val cursor3 = db.query("SELECT patientClinicId FROM patients WHERE id = 'uuid-3'")
        cursor3.moveToFirst()
        val id3 = cursor3.getString(0)
        cursor3.close()

        assert(id2 == "ABC001")
        assert(id3.startsWith("ABC001-DUP-"))
        
        // Verify indexes exist
        val indexCursor = db.query("PRAGMA index_list(patients)")
        var foundUnique = false
        while(indexCursor.moveToNext()) {
            if (indexCursor.getString(1) == "index_patients_patientClinicId" && indexCursor.getInt(2) == 1) {
                foundUnique = true
            }
        }
        indexCursor.close()
        assert(foundUnique)
    }
}
