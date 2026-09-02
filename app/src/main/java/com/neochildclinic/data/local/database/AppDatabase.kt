package com.neochildclinic.data.local.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.neochildclinic.data.local.dao.*
import com.neochildclinic.data.local.entity.*
import com.neochildclinic.core.utils.SecurityUtils
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        PatientEntity::class, 
        VisitEntity::class, 
        ReminderEntity::class, 
        VaccineEntity::class,
        VaccineBatchEntity::class,
        InventoryTransactionEntity::class,
        InventoryDeductionEntity::class,
        SyncQueueEntity::class,
        WasteEntity::class,
        WidgetDueEntity::class,
        AuditLogEntity::class,
        PatientNotesEntity::class,
        ProfileEntity::class,
        FinanceEntity::class,
        BorrowEntity::class,
        ConsultationEntity::class,
        VaccinationItemEntity::class,
        ConsultationTodoEntity::class,
        VaccinationTodoEntity::class,
        PersonalReminderEntity::class,
        BorrowReturnEntity::class
    ], 
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun vaccinationDao(): VaccinationDao
    abstract fun dueReminderDao(): DueReminderDao
    abstract fun vaccineDao(): VaccineDao
    abstract fun reminderAuditDao(): ReminderAuditDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun wasteDao(): WasteDao
    abstract fun widgetDueDao(): WidgetDueDao
    abstract fun auditLogDao(): AuditLogDao
    
    // New DAOs
    abstract fun financeDao(): FinanceDao
    abstract fun profileDao(): ProfileDao
    abstract fun borrowDao(): BorrowDao
    abstract fun patientNotesDao(): PatientNotesDao
    abstract fun inventoryDeductionDao(): InventoryDeductionDao
    abstract fun consultationDao(): ConsultationDao
    abstract fun vaccinationItemDao(): VaccinationItemDao
    abstract fun patientTodoDao(): PatientTodoDao
    abstract fun personalReminderDao(): PersonalReminderDao
    abstract fun borrowReturnDao(): BorrowReturnDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "neochild_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.getDatabasePath(DB_NAME)
                
                val passphrase = try {
                    SecurityUtils.getDatabasePassphrase(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Hardware Keystore is unavailable. This usually means the device is locked after a reboot.", e)
                    throw IllegalStateException("Security keys could not be loaded. Please ensure your device is unlocked.", e)
                }

                // Verify if we can open the database with this passphrase
                if (dbFile.exists()) {
                    try {
                        Log.d(TAG, "Verifying database encryption...")
                        net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                            dbFile.absolutePath, 
                            passphrase, 
                            null, 
                            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,
                            null
                        ).close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Existing database is unreadable (likely due to device format/restore). Wiping local database to recover...", e)
                        context.deleteDatabase(DB_NAME)
                    }
                }

                val factory = SupportOpenHelperFactory(passphrase)

                val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        val tables = listOf(
                            "patients", "patient_visits", "consultations", "vaccines",
                            "vaccine_batches", "borrow_records", "waste_records", "reminders",
                            "finance_transactions", "patient_notes", "inventory_deductions",
                            "inventory_transactions", "profiles"
                        )
                        tables.forEach { table ->
                            database.execSQL("ALTER TABLE `$table` ADD COLUMN `created_by` TEXT")
                            database.execSQL("ALTER TABLE `$table` ADD COLUMN `updated_by` TEXT")
                        }
                    }
                }
                
                val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("""CREATE TABLE IF NOT EXISTS consultation_todos (id TEXT NOT NULL PRIMARY KEY, patient_id TEXT, name TEXT NOT NULL, mobile TEXT NOT NULL, address TEXT NOT NULL, todo_date TEXT NOT NULL, status TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, is_synced INTEGER NOT NULL, created_by TEXT, updated_by TEXT)""")
                        database.execSQL("""CREATE INDEX IF NOT EXISTS index_consultation_todos_todo_date ON consultation_todos(todo_date)""")
                        database.execSQL("""CREATE INDEX IF NOT EXISTS index_consultation_todos_status ON consultation_todos(status)""")
                        database.execSQL("""CREATE INDEX IF NOT EXISTS index_consultation_todos_patient_id ON consultation_todos(patient_id)""")
                        database.execSQL("""CREATE TABLE IF NOT EXISTS vaccination_todos (id TEXT NOT NULL PRIMARY KEY, patient_id TEXT, name TEXT NOT NULL, mobile TEXT NOT NULL, vaccine_names TEXT NOT NULL, address TEXT NOT NULL, todo_date TEXT NOT NULL, status TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, is_synced INTEGER NOT NULL, created_by TEXT, updated_by TEXT)""")
                        database.execSQL("""CREATE INDEX IF NOT EXISTS index_vaccination_todos_todo_date ON vaccination_todos(todo_date)""")
                        database.execSQL("""CREATE INDEX IF NOT EXISTS index_vaccination_todos_status ON vaccination_todos(status)""")
                        database.execSQL("""CREATE INDEX IF NOT EXISTS index_vaccination_todos_patient_id ON vaccination_todos(patient_id)""")
                    }
                }

                val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL(
                            """CREATE TABLE IF NOT EXISTS personal_vaccine_reminders (
                                id TEXT NOT NULL PRIMARY KEY,
                                patient_id TEXT NOT NULL,
                                vaccine_id TEXT,
                                vaccine_label TEXT,
                                note TEXT,
                                advance_received INTEGER NOT NULL,
                                advance_amount REAL,
                                advance_date TEXT,
                                reminder_date TEXT NOT NULL,
                                status TEXT NOT NULL,
                                created_at TEXT NOT NULL,
                                updated_at TEXT NOT NULL,
                                completed_at TEXT,
                                cancelled_at TEXT,
                                is_synced INTEGER NOT NULL,
                                created_by TEXT,
                                updated_by TEXT,
                                FOREIGN KEY(patient_id) REFERENCES patients(id) ON DELETE CASCADE,
                                FOREIGN KEY(vaccine_id) REFERENCES vaccines(id) ON DELETE SET NULL
                            )"""
                        )
                        database.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_patient_id ON personal_vaccine_reminders(patient_id)")
                        database.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_vaccine_id ON personal_vaccine_reminders(vaccine_id)")
                        database.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_status ON personal_vaccine_reminders(status)")
                        database.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_reminder_date ON personal_vaccine_reminders(reminder_date)")
                    }
                }

                val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL(
                            """CREATE TABLE IF NOT EXISTS borrow_returns (
                                id TEXT NOT NULL PRIMARY KEY,
                                borrow_record_id TEXT NOT NULL,
                                batch_id TEXT NOT NULL,
                                quantity INTEGER NOT NULL,
                                returned_date TEXT NOT NULL,
                                notes TEXT,
                                created_at TEXT NOT NULL,
                                is_synced INTEGER NOT NULL,
                                created_by TEXT,
                                updated_by TEXT,
                                FOREIGN KEY(borrow_record_id) REFERENCES borrow_records(id) ON DELETE CASCADE,
                                FOREIGN KEY(batch_id) REFERENCES vaccine_batches(batchId) ON DELETE CASCADE
                            )"""
                        )
                        database.execSQL("CREATE INDEX IF NOT EXISTS index_borrow_returns_borrow_record_id ON borrow_returns(borrow_record_id)")
                        database.execSQL("CREATE INDEX IF NOT EXISTS index_borrow_returns_batch_id ON borrow_returns(batch_id)")
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .openHelperFactory(factory)
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
