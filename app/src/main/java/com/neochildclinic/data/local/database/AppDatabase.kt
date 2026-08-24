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
        VaccinationItemEntity::class
    ], 
    version = 18,
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
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .openHelperFactory(factory)
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_17_18)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
