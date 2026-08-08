package com.neochildclinic.data.local.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun vaccinationDao(): VaccinationDao
    abstract fun reminderDao(): ReminderDao
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
                val isNewDatabase = !dbFile.exists()

                val passphrase = try {
                    SecurityUtils.getDatabasePassphrase(context, shouldGenerate = isNewDatabase)
                } catch (e: Exception) {
                    Log.e(TAG, "Passphrase unavailable. Preventing database initialization to protect data.", e)
                    throw IllegalStateException("Security keys could not be loaded. Please ensure your device is unlocked.", e)
                }

                val factory = SupportOpenHelperFactory(passphrase)
                
                if (!isNewDatabase) {
                    Log.d(TAG, "Opening encrypted database...")
                    try {
                        net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                            dbFile.absolutePath, 
                            passphrase, 
                            null, 
                            net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,
                            null
                        ).close()
                        Log.d(TAG, "Database opened successfully.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Database open failed. Key mismatch or corruption suspected.", e)
                        throw IllegalStateException("Unable to access the encrypted database. Your security keys could not be loaded.", e)
                    }
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .openHelperFactory(factory)
                .setJournalMode(JournalMode.TRUNCATE)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
