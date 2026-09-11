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
        BorrowReturnEntity::class,
        ExpenseEntity::class,
        DoctorWeeklySlotEntity::class,
        DoctorSlotExceptionEntity::class,
    ], 
    version = 25,
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
    abstract fun expenseDao(): ExpenseDao
    abstract fun doctorAvailabilityDao(): DoctorAvailabilityDao

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

                val migration17_18 = object : androidx.room.migration.Migration(17, 18) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        val tables = listOf(
                            "patients", "patient_visits", "consultations", "vaccines",
                            "vaccine_batches", "borrow_records", "waste_records", "reminders",
                            "finance_transactions", "patient_notes", "inventory_deductions",
                            "inventory_transactions", "profiles"
                        )
                        tables.forEach { table ->
                            db.execSQL("ALTER TABLE `$table` ADD COLUMN `created_by` TEXT")
                            db.execSQL("ALTER TABLE `$table` ADD COLUMN `updated_by` TEXT")
                        }
                    }
                }
                
                val migration18_19 = object : androidx.room.migration.Migration(18, 19) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE IF NOT EXISTS consultation_todos (id TEXT NOT NULL PRIMARY KEY, patient_id TEXT, name TEXT NOT NULL, mobile TEXT NOT NULL, address TEXT NOT NULL, todo_date TEXT NOT NULL, status TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, is_synced INTEGER NOT NULL, created_by TEXT, updated_by TEXT)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS index_consultation_todos_todo_date ON consultation_todos(todo_date)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS index_consultation_todos_status ON consultation_todos(status)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS index_consultation_todos_patient_id ON consultation_todos(patient_id)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS vaccination_todos (id TEXT NOT NULL PRIMARY KEY, patient_id TEXT, name TEXT NOT NULL, mobile TEXT NOT NULL, vaccine_names TEXT NOT NULL, address TEXT NOT NULL, todo_date TEXT NOT NULL, status TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, is_synced INTEGER NOT NULL, created_by TEXT, updated_by TEXT)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS index_vaccination_todos_todo_date ON vaccination_todos(todo_date)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS index_vaccination_todos_status ON vaccination_todos(status)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS index_vaccination_todos_patient_id ON vaccination_todos(patient_id)""")
                    }
                }

                val migration19_20 = object : androidx.room.migration.Migration(19, 20) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
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
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_patient_id ON personal_vaccine_reminders(patient_id)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_vaccine_id ON personal_vaccine_reminders(vaccine_id)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_status ON personal_vaccine_reminders(status)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_reminder_date ON personal_vaccine_reminders(reminder_date)")
                    }
                }

                val migration20_21 = object : androidx.room.migration.Migration(20, 21) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
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
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_borrow_returns_borrow_record_id ON borrow_returns(borrow_record_id)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_borrow_returns_batch_id ON borrow_returns(batch_id)")
                    }
                }

                val migration21_22 = object : androidx.room.migration.Migration(21, 22) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE personal_vaccine_reminders RENAME TO personal_vaccine_reminders_old")
                        db.execSQL("""CREATE TABLE personal_vaccine_reminders (id TEXT NOT NULL PRIMARY KEY, patient_id TEXT, patient_name TEXT NOT NULL, patient_phone TEXT NOT NULL, vaccine_id TEXT, vaccine_label TEXT, note TEXT, advance_received INTEGER NOT NULL, advance_amount REAL, advance_date TEXT, reminder_date TEXT, status TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, completed_at TEXT, cancelled_at TEXT, is_synced INTEGER NOT NULL, created_by TEXT, updated_by TEXT, FOREIGN KEY(patient_id) REFERENCES patients(id) ON DELETE CASCADE, FOREIGN KEY(vaccine_id) REFERENCES vaccines(id) ON DELETE SET NULL)""")
                        db.execSQL("""INSERT INTO personal_vaccine_reminders (id, patient_id, patient_name, patient_phone, vaccine_id, vaccine_label, note, advance_received, advance_amount, advance_date, reminder_date, status, created_at, updated_at, completed_at, cancelled_at, is_synced, created_by, updated_by) SELECT r.id, r.patient_id, COALESCE(p.name, ''), COALESCE(p.phone, ''), r.vaccine_id, r.vaccine_label, r.note, r.advance_received, r.advance_amount, r.advance_date, r.reminder_date, r.status, r.created_at, r.updated_at, r.completed_at, r.cancelled_at, r.is_synced, r.created_by, r.updated_by FROM personal_vaccine_reminders_old r LEFT JOIN patients p ON p.id = r.patient_id""")
                        db.execSQL("DROP TABLE personal_vaccine_reminders_old")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_patient_id ON personal_vaccine_reminders(patient_id)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_vaccine_id ON personal_vaccine_reminders(vaccine_id)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_status ON personal_vaccine_reminders(status)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_vaccine_reminders_reminder_date ON personal_vaccine_reminders(reminder_date)")
                    }
                }

                val migration22_23 = object : androidx.room.migration.Migration(22, 23) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
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

                // Doctor assignment + availability slots (Today's Patient / Add
                // Consultation / Add Vaccination doctor+slot picker). All new columns are
                // nullable and all new tables are additive-only - existing
                // consultations/patient_visits/consultation_todos/vaccination_todos rows
                // remain valid with availabilitySlotId = NULL (req. 20: no forced
                // migration of historical records onto a slot they were never booked
                // against).
                val migration24_25 = object : androidx.room.migration.Migration(24, 25) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE consultations ADD COLUMN availabilitySlotId TEXT")
                        db.execSQL("ALTER TABLE patient_visits ADD COLUMN availabilitySlotId TEXT")
                        db.execSQL("ALTER TABLE consultation_todos ADD COLUMN doctor_id TEXT")
                        db.execSQL("ALTER TABLE consultation_todos ADD COLUMN doctor_name TEXT")
                        db.execSQL("ALTER TABLE consultation_todos ADD COLUMN availability_slot_id TEXT")
                        db.execSQL("ALTER TABLE vaccination_todos ADD COLUMN doctor_id TEXT")
                        db.execSQL("ALTER TABLE vaccination_todos ADD COLUMN doctor_name TEXT")
                        db.execSQL("ALTER TABLE vaccination_todos ADD COLUMN availability_slot_id TEXT")

                        db.execSQL(
                            """CREATE TABLE IF NOT EXISTS doctor_weekly_slots (
                                id TEXT NOT NULL PRIMARY KEY,
                                doctorId TEXT NOT NULL,
                                dayOfWeek INTEGER NOT NULL,
                                startMinute INTEGER NOT NULL,
                                endMinute INTEGER NOT NULL,
                                is_active INTEGER NOT NULL DEFAULT 1,
                                created_at TEXT NOT NULL DEFAULT '',
                                updated_at TEXT NOT NULL DEFAULT '',
                                is_synced INTEGER NOT NULL DEFAULT 0,
                                created_by TEXT,
                                updated_by TEXT
                            )"""
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_weekly_slots_doctorId ON doctor_weekly_slots(doctorId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_weekly_slots_dayOfWeek ON doctor_weekly_slots(dayOfWeek)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_weekly_slots_isActive ON doctor_weekly_slots(is_active)")

                        db.execSQL(
                            """CREATE TABLE IF NOT EXISTS doctor_slot_exceptions (
                                id TEXT NOT NULL PRIMARY KEY,
                                doctorId TEXT NOT NULL,
                                exceptionDate TEXT NOT NULL,
                                exceptionType TEXT NOT NULL,
                                weeklySlotId TEXT,
                                reason TEXT,
                                is_deleted INTEGER NOT NULL DEFAULT 0,
                                created_at TEXT NOT NULL DEFAULT '',
                                updated_at TEXT NOT NULL DEFAULT '',
                                is_synced INTEGER NOT NULL DEFAULT 0,
                                created_by TEXT,
                                updated_by TEXT
                            )"""
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_slot_exceptions_doctorId ON doctor_slot_exceptions(doctorId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_slot_exceptions_exceptionDate ON doctor_slot_exceptions(exceptionDate)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_slot_exceptions_weeklySlotId ON doctor_slot_exceptions(weeklySlotId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_doctor_slot_exceptions_isDeleted ON doctor_slot_exceptions(is_deleted)")
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .openHelperFactory(factory)
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(migration17_18, migration18_19, migration19_20, migration20_21, migration21_22, migration22_23, migration24_25)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
