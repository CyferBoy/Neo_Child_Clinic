package com.neochildclinic.data.local.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        StaffEntity::class,
        UserEntity::class,
        FinanceEntity::class,
        BorrowEntity::class,
        ConsultationEntity::class
    ], 
    version = 28,
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
    abstract fun staffDao(): StaffDao
    abstract fun borrowDao(): BorrowDao
    abstract fun patientNotesDao(): PatientNotesDao
    abstract fun inventoryDeductionDao(): InventoryDeductionDao
    abstract fun consultationDao(): ConsultationDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "neochild_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `waste_records` (
                        `id` TEXT NOT NULL, `vaccineId` TEXT NOT NULL, `brandName` TEXT NOT NULL, 
                        `batchNumber` TEXT NOT NULL, `expiryDate` TEXT NOT NULL, `dateWasted` TEXT NOT NULL, 
                        `reason` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, 
                        `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                """)
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `widget_due_cache` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientName` TEXT NOT NULL, 
                        `vaccineName` TEXT NOT NULL, `dueDate` TEXT NOT NULL, `isOverdue` INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add batchId to waste_records
                db.execSQL("ALTER TABLE `waste_records` ADD COLUMN `batchId` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add batchNumbers and expiryDates to vaccinations
                db.execSQL("ALTER TABLE `vaccinations` ADD COLUMN `batchNumbers` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `vaccinations` ADD COLUMN `expiryDates` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create new tables
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `due_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT NOT NULL, 
                        `originalVisitId` TEXT NOT NULL, `vaccineName` TEXT NOT NULL, `dueDate` TEXT NOT NULL, 
                        `reminderDate` TEXT NOT NULL, `status` TEXT NOT NULL, `priority` TEXT NOT NULL, 
                        `reminderEnabled` INTEGER NOT NULL, `category` TEXT NOT NULL, `notes` TEXT, 
                        `lastReminderTime` INTEGER NOT NULL, `notificationSent` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, 
                        `isDeleted` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_due_reminders_patientId_originalVisitId_vaccineName` ON `due_reminders` (`patientId`, `originalVisitId`, `vaccineName`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `completed_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT NOT NULL, 
                        `originalVisitId` TEXT NOT NULL, `vaccineName` TEXT NOT NULL, `dueDate` TEXT NOT NULL, 
                        `completionDate` INTEGER NOT NULL, `completedBy` TEXT NOT NULL, `notes` TEXT, 
                        `isSynced` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_completed_reminders_patientId_originalVisitId_vaccineName` ON `completed_reminders` (`patientId`, `originalVisitId`, `vaccineName`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dismissed_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT NOT NULL, 
                        `originalVisitId` TEXT NOT NULL, `vaccineName` TEXT NOT NULL, `dueDate` TEXT NOT NULL, 
                        `dismissalDate` INTEGER NOT NULL, `dismissedBy` TEXT NOT NULL, `reason` TEXT, 
                        `isSynced` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dismissed_reminders_patientId_originalVisitId_vaccineName` ON `dismissed_reminders` (`patientId`, `originalVisitId`, `vaccineName`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `external_reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT NOT NULL, 
                        `originalVisitId` TEXT NOT NULL, `vaccineName` TEXT NOT NULL, `dueDate` TEXT NOT NULL, 
                        `externalDate` TEXT NOT NULL, `source` TEXT NOT NULL, `recordedBy` TEXT NOT NULL, 
                        `notes` TEXT, `isSynced` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_external_reminders_patientId_originalVisitId_vaccineName` ON `external_reminders` (`patientId`, `originalVisitId`, `vaccineName`)")

                // 2. Migrate data with deduplication
                db.execSQL("""
                    INSERT OR REPLACE INTO due_reminders (patientId, originalVisitId, vaccineName, dueDate, reminderDate, status, priority, reminderEnabled, category, notes, lastReminderTime, notificationSent, createdAt, updatedAt, isSynced, isDeleted)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, dueDate, status, priority, reminderEnabled, category, notes, lastReminderTime, notificationSent, createdAt, updatedAt, isSynced, 0
                    FROM reminders WHERE (completed = 0 OR completed IS NULL) AND status NOT IN ('COMPLETED', 'DISMISSED', 'EXTERNAL')
                """)

                db.execSQL("""
                    INSERT OR REPLACE INTO completed_reminders (patientId, originalVisitId, vaccineName, dueDate, completionDate, completedBy, notes, isSynced, isDeleted)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, updatedAt, 'MIGRATED', notes, isSynced, 0
                    FROM reminders WHERE status = 'COMPLETED' OR completed = 1
                """)

                db.execSQL("""
                    INSERT OR REPLACE INTO dismissed_reminders (patientId, originalVisitId, vaccineName, dueDate, dismissalDate, dismissedBy, reason, isSynced, isDeleted)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, updatedAt, 'MIGRATED', notes, isSynced, 0
                    FROM reminders WHERE status = 'DISMISSED'
                """)

                db.execSQL("""
                    INSERT OR REPLACE INTO external_reminders (patientId, originalVisitId, vaccineName, dueDate, externalDate, source, recordedBy, notes, isSynced, isDeleted)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, dueDate, IFNULL(vaccinationSource, 'UNKNOWN'), 'MIGRATED', notes, isSynced, 0
                    FROM reminders WHERE status = 'EXTERNAL'
                """)
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT, 
                        `action` TEXT NOT NULL, `details` TEXT NOT NULL, `staffMember` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, `device` TEXT, `isSynced` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_patientId` ON `audit_logs` (`patientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_timestamp` ON `audit_logs` (`timestamp`)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add vaccineIds to vaccinations table
                db.execSQL("ALTER TABLE `vaccinations` ADD COLUMN `vaccineIds` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add performedBy to due_reminders table
                db.execSQL("ALTER TABLE `due_reminders` ADD COLUMN `performedBy` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Starting Migration 18 -> 19")

                // 1. Refactor Patients (Handle NULLs and NOT NULL constraints)
                Log.d(TAG, "Migrating patients table...")
                
                // Safety: Ensure patientClinicId column exists (handles cases where it wasn't added in v18)
                try {
                    db.execSQL("ALTER TABLE patients ADD COLUMN patientClinicId TEXT")
                } catch (_: Exception) {}

                // Repair data in old table first
                // CRITICAL: We use 'TEMP-' + id to ensure UNIQUE constraint doesn't fail 
                // for multiple legacy patients before the MigrationWorker assigns real IDs.
                db.execSQL("UPDATE patients SET patientClinicId = 'TEMP-' || id WHERE patientClinicId IS NULL OR patientClinicId = '' OR patientClinicId = ' '")
                db.execSQL("UPDATE patients SET isSynced = 1 WHERE isSynced IS NULL")
                db.execSQL("UPDATE patients SET isDeleted = 0 WHERE isDeleted IS NULL")
                
                // Create new table with exact schema required by Room
                db.execSQL("""
                    CREATE TABLE `patients_new` (
                        `id` TEXT NOT NULL, 
                        `patientClinicId` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `parentName` TEXT NOT NULL, 
                        `phone` TEXT NOT NULL, 
                        `alternatePhone` TEXT NOT NULL, 
                        `dob` TEXT NOT NULL, 
                        `gender` TEXT NOT NULL, 
                        `village` TEXT NOT NULL, 
                        `address` TEXT NOT NULL, 
                        `registrationDate` TEXT NOT NULL, 
                        `notes` TEXT, 
                        `guardianRelation` TEXT, 
                        `guardianPhone` TEXT, 
                        `attachments` TEXT, 
                        `isSynced` INTEGER NOT NULL, 
                        `isDeleted` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """)

                // Transfer data using COALESCE to ensure NOT NULL compliance
                db.execSQL("""
                    INSERT INTO patients_new (
                        id, patientClinicId, name, parentName, phone, alternatePhone, 
                        dob, gender, village, address, registrationDate, 
                        isSynced, isDeleted
                    )
                    SELECT 
                        id, patientClinicId, name, COALESCE(parentName, ''), phone, COALESCE(alternatePhone, ''), 
                        dob, gender, COALESCE(village, ''), COALESCE(address, ''), COALESCE(registrationDate, ''), 
                        isSynced, isDeleted
                    FROM patients
                """)
                
                db.execSQL("DROP TABLE patients")
                db.execSQL("ALTER TABLE patients_new RENAME TO patients")
                
                // Recreate all indices for patients
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_patients_patientClinicId` ON `patients` (`patientClinicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_patients_name` ON `patients` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_patients_phone` ON `patients` (`phone`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_patients_isSynced` ON `patients` (`isSynced`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_patients_isDeleted` ON `patients` (`isDeleted`)")

                // 2. Update Vaccines
                Log.d(TAG, "Updating vaccines table...")
                db.execSQL("ALTER TABLE `vaccines` ADD COLUMN `manufacturer` TEXT")
                db.execSQL("ALTER TABLE `vaccines` ADD COLUMN `category` TEXT")
                db.execSQL("ALTER TABLE `vaccines` ADD COLUMN `doseSchedule` TEXT")
                db.execSQL("ALTER TABLE `vaccines` ADD COLUMN `storageDetails` TEXT")

                // 3. Update Vaccine Batches
                Log.d(TAG, "Updating vaccine_batches table...")
                db.execSQL("ALTER TABLE `vaccine_batches` ADD COLUMN `reservedQuantity` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `vaccine_batches` ADD COLUMN `usedQuantity` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `vaccine_batches` ADD COLUMN `wastedQuantity` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `vaccine_batches` ADD COLUMN `borrowedQuantity` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vaccine_batches_batchNumber` ON `vaccine_batches` (`batchNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vaccine_batches_vaccineId` ON `vaccine_batches` (`vaccineId`)")

                // 4. Create New Tables
                Log.d(TAG, "Creating new tables...")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reminder_states` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT NOT NULL, 
                        `originalVisitId` TEXT NOT NULL, `vaccineName` TEXT NOT NULL, `dueDate` TEXT NOT NULL, 
                        `status` TEXT NOT NULL, `priority` TEXT NOT NULL, `reminderEnabled` INTEGER NOT NULL, 
                        `category` TEXT NOT NULL, `vaccinationSource` TEXT, `notes` TEXT, 
                        `reminderDate` TEXT, `completionDate` INTEGER, `performedBy` TEXT, `dismissalDate` INTEGER, 
                        `dismissalReason` TEXT, `externalDate` TEXT, `source` TEXT, 
                        `lastReminderTime` INTEGER NOT NULL, `notificationSent` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        FOREIGN KEY(`patientId`) REFERENCES `patients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_states_patientId_originalVisitId_vaccineName` ON `reminder_states` (`patientId`, `originalVisitId`, `vaccineName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_states_status` ON `reminder_states` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_states_dueDate` ON `reminder_states` (`dueDate`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `patient_notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `patientId` TEXT NOT NULL, 
                        `content` TEXT NOT NULL, `author` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, 
                        `isSynced` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL,
                        FOREIGN KEY(`patientId`) REFERENCES `patients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_patient_notes_patientId` ON `patient_notes` (`patientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_patient_notes_timestamp` ON `patient_notes` (`timestamp`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `staff_profiles` (
                        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `email` TEXT NOT NULL, 
                        `role` TEXT NOT NULL, `department` TEXT, `permissions` TEXT, 
                        `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, 
                        `lastActive` INTEGER NOT NULL, PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_staff_profiles_email` ON `staff_profiles` (`email`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_auth` (
                        `id` TEXT NOT NULL, `email` TEXT NOT NULL, `name` TEXT NOT NULL, 
                        `biometricEnabled` INTEGER NOT NULL, `pinHash` TEXT, `lastLogin` INTEGER NOT NULL, 
                        `devices` TEXT, `securityStamp` TEXT NOT NULL, PRIMARY KEY(`id`)
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_auth_email` ON `user_auth` (`email`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `finance_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, 
                        `type` TEXT NOT NULL, `category` TEXT NOT NULL, `amount` REAL NOT NULL, 
                        `currency` TEXT NOT NULL, `paymentMethod` TEXT NOT NULL, `patientId` TEXT, 
                        `visitId` TEXT, `referenceNumber` TEXT, `remarks` TEXT, `recordedBy` TEXT NOT NULL, 
                        `isSynced` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_finance_transactions_patientId` ON `finance_transactions` (`patientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_finance_transactions_visitId` ON `finance_transactions` (`visitId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_finance_transactions_timestamp` ON `finance_transactions` (`timestamp`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `borrow_records` (
                        `id` TEXT NOT NULL, `doctorName` TEXT NOT NULL, `vaccineId` TEXT NOT NULL, 
                        `batchId` TEXT NOT NULL, `borrowedDate` TEXT NOT NULL, `quantity` INTEGER NOT NULL, 
                        `isReturned` INTEGER NOT NULL, `returnedDate` TEXT, `type` TEXT NOT NULL, 
                        `notes` TEXT, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`),
                        FOREIGN KEY(`vaccineId`) REFERENCES `vaccines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`batchId`) REFERENCES `vaccine_batches`(`batchId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_borrow_records_vaccineId` ON `borrow_records` (`vaccineId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_borrow_records_batchId` ON `borrow_records` (`batchId`)")

                // 5. Migrate Reminders to reminder_states
                Log.d(TAG, "Migrating reminders to states...")
                db.execSQL("""
                    INSERT INTO reminder_states (patientId, originalVisitId, vaccineName, dueDate, status, reminderDate, priority, reminderEnabled, category, notes, lastReminderTime, notificationSent, createdAt, updatedAt, isSynced, isDeleted, vaccinationSource)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, status, reminderDate, priority, reminderEnabled, category, notes, lastReminderTime, notificationSent, createdAt, updatedAt, isSynced, isDeleted, NULL
                    FROM due_reminders
                """)
                db.execSQL("""
                    INSERT OR REPLACE INTO reminder_states (patientId, originalVisitId, vaccineName, dueDate, status, notes, completionDate, performedBy, createdAt, updatedAt, isSynced, isDeleted, priority, reminderEnabled, category, lastReminderTime, notificationSent, vaccinationSource)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, 'COMPLETED', notes, completionDate, completedBy, completionDate, completionDate, isSynced, isDeleted, 'MEDIUM', 0, 'VACCINATION', 0, 0, NULL
                    FROM completed_reminders
                """)
                db.execSQL("""
                    INSERT OR REPLACE INTO reminder_states (patientId, originalVisitId, vaccineName, dueDate, status, notes, dismissalDate, dismissalReason, createdAt, updatedAt, isSynced, isDeleted, priority, reminderEnabled, category, lastReminderTime, notificationSent, vaccinationSource)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, 'DISMISSED', NULL, dismissalDate, reason, dismissalDate, dismissalDate, isSynced, isDeleted, 'MEDIUM', 0, 'VACCINATION', 0, 0, NULL
                    FROM dismissed_reminders
                """)
                db.execSQL("""
                    INSERT OR REPLACE INTO reminder_states (patientId, originalVisitId, vaccineName, dueDate, status, notes, externalDate, source, performedBy, createdAt, updatedAt, isSynced, isDeleted, priority, reminderEnabled, category, lastReminderTime, notificationSent, vaccinationSource)
                    SELECT patientId, originalVisitId, vaccineName, dueDate, 'EXTERNAL', notes, externalDate, source, recordedBy, 0, 0, isSynced, isDeleted, 'MEDIUM', 0, 'VACCINATION', 0, 0, source
                    FROM external_reminders
                """)

                // 6. Refactor Vaccinations to patient_visits
                Log.d(TAG, "Refactoring vaccinations to patient_visits...")
                db.execSQL("ALTER TABLE `vaccinations` RENAME TO `patient_visits_old`")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `patient_visits` (
                        `id` TEXT NOT NULL, `patientId` TEXT NOT NULL, `dateGiven` TEXT NOT NULL, 
                        `doctor` TEXT NOT NULL, `vaccineNames` TEXT NOT NULL, `vaccineIds` TEXT NOT NULL, 
                        `batchIds` TEXT NOT NULL, `materialsUsed` TEXT, `notes` TEXT NOT NULL, 
                        `receiptNumber` TEXT NOT NULL, `totalPaid` REAL NOT NULL, `paymentId` TEXT, 
                        `nxtVaccineNames` TEXT NOT NULL, `nextDueDate` TEXT NOT NULL, `cost` REAL NOT NULL, 
                        `cashAmount` REAL NOT NULL, `onlineAmount` REAL NOT NULL, `withFees` INTEGER NOT NULL, 
                        `doctorsAcc` INTEGER NOT NULL, `isDone` INTEGER NOT NULL, `source` TEXT NOT NULL, 
                        `isSynced` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`id`),
                        FOREIGN KEY(`patientId`) REFERENCES `patients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                // Map old column names if they differed (e.g., performedBy -> doctor, batchNumbers -> batchIds)
                db.execSQL("""
                    INSERT INTO patient_visits (id, patientId, dateGiven, doctor, vaccineNames, vaccineIds, batchIds, notes, receiptNumber, totalPaid, nxtVaccineNames, nextDueDate, cost, cashAmount, onlineAmount, withFees, doctorsAcc, isDone, source, isSynced, isDeleted)
                    SELECT id, patientId, dateGiven, doctor, vaccineNames, vaccineIds, batchNumbers, notes, receiptNumber, totalPaid, nxtVaccineNames, nextDueDate, cost, cashAmount, onlineAmount, withFees, doctorsAcc, isDone, source, isSynced, isDeleted
                    FROM patient_visits_old
                """)
                db.execSQL("DROP TABLE `patient_visits_old`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_patient_visits_patientId` ON `patient_visits` (`patientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_patient_visits_receiptNumber` ON `patient_visits` (`receiptNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_patient_visits_doctor` ON `patient_visits` (`doctor`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_patient_visits_isSynced` ON `patient_visits` (`isSynced`)")

                // 7. Update Audit Logs and Merge Reminder Audits
                Log.d(TAG, "Merging audit logs...")
                db.execSQL("ALTER TABLE `audit_logs` RENAME TO `audit_logs_old`")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, 
                        `user` TEXT NOT NULL, `module` TEXT NOT NULL, `entityType` TEXT NOT NULL, 
                        `entityId` TEXT NOT NULL, `action` TEXT NOT NULL, `oldValue` TEXT, 
                        `newValue` TEXT, `remarks` TEXT, `device` TEXT, `isSynced` INTEGER NOT NULL, 
                        `patientId` TEXT
                    )
                """)
                db.execSQL("""
                    INSERT INTO audit_logs (timestamp, user, module, entityType, entityId, action, remarks, device, isSynced, patientId)
                    SELECT timestamp, COALESCE(staffMember, 'SYSTEM'), 'LEGACY', 'UNKNOWN', '0', action, details, device, isSynced, patientId
                    FROM audit_logs_old
                """)
                db.execSQL("""
                    INSERT INTO audit_logs (timestamp, user, module, entityType, entityId, action, remarks, isSynced, patientId)
                    SELECT timestamp, COALESCE(performedBy, 'SYSTEM'), 'REMINDER', 'REMINDER', (patientId || '||' || vaccineName), action, notes, isSynced, patientId
                    FROM reminder_audits
                """)
                
                // 8. Cleanup and Final Indices
                Log.d(TAG, "Cleaning up legacy tables...")
                db.execSQL("DROP TABLE IF EXISTS `audit_logs_old`")
                db.execSQL("DROP TABLE IF EXISTS `reminder_audits`")
                db.execSQL("DROP TABLE IF EXISTS `due_reminders`")
                db.execSQL("DROP TABLE IF EXISTS `completed_reminders`")
                db.execSQL("DROP TABLE IF EXISTS `dismissed_reminders`")
                db.execSQL("DROP TABLE IF EXISTS `external_reminders`")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_timestamp` ON `audit_logs` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_patientId` ON `audit_logs` (`patientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_entityType` ON `audit_logs` (`entityType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_entityId` ON `audit_logs` (`entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_module` ON `audit_logs` (`module`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_user` ON `audit_logs` (`user`)")
                
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_transactions_vaccineId` ON `inventory_transactions` (`vaccineId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_transactions_batchId` ON `inventory_transactions` (`batchId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_transactions_vaccinationId` ON `inventory_transactions` (`vaccinationId`)")

                Log.i(TAG, "Migration 18 -> 19 completed successfully.")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Starting Migration 19 -> 20 (Adding performance indices)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_patient_visits_isDone` ON `patient_visits` (`isDone`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_status` ON `sync_queue` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_priority` ON `sync_queue` (`priority`)")
                Log.i(TAG, "Migration 19 -> 20 completed.")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Starting Migration 20 -> 21 (Removing lowStockThreshold from vaccines)")
                
                // Create temporary table
                db.execSQL("""
                    CREATE TABLE `vaccines_new` (
                        `id` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `brandName` TEXT NOT NULL, 
                        `companyName` TEXT NOT NULL, 
                        `manufacturer` TEXT, 
                        `category` TEXT, 
                        `doseSchedule` TEXT, 
                        `storageDetails` TEXT, 
                        `lastUpdated` INTEGER NOT NULL, 
                        `isDeleted` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """)
                
                // Copy data
                db.execSQL("""
                    INSERT INTO vaccines_new (id, type, brandName, companyName, manufacturer, category, doseSchedule, storageDetails, lastUpdated, isDeleted)
                    SELECT id, type, brandName, companyName, manufacturer, category, doseSchedule, storageDetails, lastUpdated, isDeleted
                    FROM vaccines
                """)
                
                // Switch tables
                db.execSQL("DROP TABLE vaccines")
                db.execSQL("ALTER TABLE vaccines_new RENAME TO vaccines")
                
                Log.i(TAG, "Migration 20 -> 21 completed.")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Starting Migration 21 -> 22 (Adding fcmToken to staff and users)")
                db.execSQL("ALTER TABLE `staff_profiles` ADD COLUMN `fcmToken` TEXT")
                db.execSQL("ALTER TABLE `user_auth` ADD COLUMN `fcmToken` TEXT")
                Log.i(TAG, "Migration 21 -> 22 completed.")
            }
        }


        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Update patient_visits with inventoryStatus
                db.execSQL("ALTER TABLE `patient_visits` ADD COLUMN `inventoryStatus` TEXT NOT NULL DEFAULT 'COMPLETED'")
                
                // 2. Enhance inventory_transactions
                db.execSQL("ALTER TABLE `inventory_transactions` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'COMPLETED'")
                db.execSQL("ALTER TABLE `inventory_transactions` ADD COLUMN `failureReason` TEXT")
                db.execSQL("ALTER TABLE `inventory_transactions` ADD COLUMN `processedAt` INTEGER")
                db.execSQL("ALTER TABLE `inventory_transactions` ADD COLUMN `processedBy` TEXT")
                db.execSQL("ALTER TABLE `inventory_transactions` ADD COLUMN `isSynced` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Mark existing vaccinations as SKIPPED to avoid reconciliation of historical data
                db.execSQL("UPDATE patient_visits SET inventoryStatus = 'SKIPPED'")
                
                // 2. Create inventory_deductions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `inventory_deductions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `vaccinationId` TEXT NOT NULL, 
                        `vaccineId` TEXT NOT NULL, 
                        `vaccineName` TEXT NOT NULL, 
                        `batchId` TEXT, 
                        `quantity` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `errorMessage` TEXT, 
                        `resolvedAt` INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.i(TAG, "Starting Migration 24 -> 25 (Adding mrp and netRate to vaccines)")
                db.execSQL("ALTER TABLE `vaccines` ADD COLUMN `mrp` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `vaccines` ADD COLUMN `netRate` REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE patient_visits ADD COLUMN batchNumbers TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE patients ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE patient_visits ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE waste_records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vaccine_batches ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `consultations` (
                        `id` TEXT NOT NULL, 
                        `patientId` TEXT NOT NULL, 
                        `date` TEXT NOT NULL, 
                        `amount` REAL NOT NULL, 
                        `notes` TEXT NOT NULL, 
                        `nextFollowUpDate` TEXT NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `isSynced` INTEGER NOT NULL, 
                        `isDeleted` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`patientId`) REFERENCES `patients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_consultations_patientId` ON `consultations` (`patientId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_consultations_date` ON `consultations` (`date`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = context.getDatabasePath(DB_NAME)
                val isNewDatabase = !dbFile.exists()

                val passphrase = try {
                    SecurityUtils.getDatabasePassphrase(context, shouldGenerate = isNewDatabase)
                } catch (e: Exception) {
                    Log.e(TAG, "Passphrase unavailable. Preventing database initialization to protect data.", e)
                    // We throw here. The application should catch this at a higher level (e.g. in DatabaseModule or UI)
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
                        // CRITICAL: Never delete the database automatically.
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
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
