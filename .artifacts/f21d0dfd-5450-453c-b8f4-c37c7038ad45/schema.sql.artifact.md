# Consultation Database Schema (SQLite)

The following SQL scripts create the tables, indexes, and foreign key relationships used by the consultation and patient management system.

```sql
-- 1. Patients Table
CREATE TABLE IF NOT EXISTS `patients` (
    `id` TEXT NOT NULL,
    `patient_clinic_id` TEXT,
    `name` TEXT NOT NULL,
    `phone` TEXT NOT NULL,
    `alternate_phone` TEXT,
    `dob` TEXT NOT NULL,
    `gender` TEXT NOT NULL,
    `address` TEXT,
    `registration_date` TEXT,
    `attachments` TEXT,
    `updated_at` TEXT,
    `is_synced` INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY(`id`)
);

CREATE UNIQUE INDEX IF NOT EXISTS `index_patients_patient_clinic_id` ON `patients` (`patient_clinic_id`);
CREATE INDEX IF NOT EXISTS `index_patients_name` ON `patients` (`name`);
CREATE INDEX IF NOT EXISTS `index_patients_phone` ON `patients` (`phone`);
CREATE INDEX IF NOT EXISTS `index_patients_is_synced` ON `patients` (`is_synced`);


-- 2. Patient Visits Table
CREATE TABLE IF NOT EXISTS `patient_visits` (
    `id` TEXT NOT NULL,
    `patient_id` TEXT NOT NULL,
    `date_given` TEXT NOT NULL,
    `doctor` TEXT NOT NULL DEFAULT '',
    `vaccine_names` TEXT NOT NULL DEFAULT '',
    `vaccine_ids` TEXT NOT NULL DEFAULT '',
    `batch_ids` TEXT NOT NULL DEFAULT '',
    `batch_numbers` TEXT NOT NULL DEFAULT '',
    `materials_used` TEXT,
    `notes` TEXT NOT NULL DEFAULT '',
    `receipt_number` TEXT NOT NULL DEFAULT '',
    `total_paid` REAL NOT NULL DEFAULT 0.0,
    `payment_id` TEXT,
    `nxt_vaccine_names` TEXT NOT NULL DEFAULT '',
    `next_due_date` TEXT NOT NULL DEFAULT '',
    `cash_amount` REAL NOT NULL DEFAULT 0.0,
    `online_amount` REAL NOT NULL DEFAULT 0.0,
    `with_fees` INTEGER NOT NULL DEFAULT 0,
    `doctors_acc` INTEGER NOT NULL DEFAULT 0,
    `status` TEXT NOT NULL DEFAULT 'ACTIVE',
    `source` TEXT NOT NULL DEFAULT 'CLINIC',
    `visit_type` TEXT NOT NULL DEFAULT 'VACCINATION',
    `inventory_status` TEXT NOT NULL DEFAULT 'PENDING',
    `created_at` TEXT NOT NULL DEFAULT '',
    `updated_at` TEXT NOT NULL DEFAULT '',
    `is_synced` INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`patient_id`) REFERENCES `patients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS `index_patient_visits_patient_id` ON `patient_visits` (`patient_id`);
CREATE INDEX IF NOT EXISTS `index_patient_visits_receipt_number` ON `patient_visits` (`receipt_number`);
CREATE INDEX IF NOT EXISTS `index_patient_visits_doctor` ON `patient_visits` (`doctor`);
CREATE INDEX IF NOT EXISTS `index_patient_visits_is_synced` ON `patient_visits` (`is_synced`);
CREATE INDEX IF NOT EXISTS `index_patient_visits_status` ON `patient_visits` (`status`);


-- 3. Consultations Table
CREATE TABLE IF NOT EXISTS `consultations` (
    `id` TEXT NOT NULL,
    `visitId` TEXT NOT NULL DEFAULT '',
    `patientId` TEXT NOT NULL,
    `doctorId` TEXT NOT NULL DEFAULT '',
    `doctorName` TEXT NOT NULL DEFAULT '',
    `date` TEXT NOT NULL,
    `amount` REAL NOT NULL,
    `cashAmount` REAL NOT NULL DEFAULT 0.0,
    `onlineAmount` REAL NOT NULL DEFAULT 0.0,
    `problem` TEXT NOT NULL DEFAULT '',
    `notes` TEXT NOT NULL DEFAULT '',
    `nextFollowUpDate` TEXT NOT NULL DEFAULT '',
    `createdAt` TEXT NOT NULL DEFAULT '',
    `updatedAt` TEXT NOT NULL DEFAULT '',
    `isSynced` INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`patientId`) REFERENCES `patients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY(`visitId`) REFERENCES `patient_visits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS `index_consultations_patientId` ON `consultations` (`patientId`);
CREATE INDEX IF NOT EXISTS `index_consultations_visitId` ON `consultations` (`visitId`);
CREATE INDEX IF NOT EXISTS `index_consultations_date` ON `consultations` (`date`);


-- 4. Finance Transactions Table
CREATE TABLE IF NOT EXISTS `finance_transactions` (
    `id` TEXT NOT NULL,
    `timestamp` TEXT NOT NULL,
    `type` TEXT NOT NULL,
    `category` TEXT NOT NULL,
    `amount` REAL NOT NULL,
    `cash_amount` REAL NOT NULL DEFAULT 0.0,
    `online_amount` REAL NOT NULL DEFAULT 0.0,
    `currency` TEXT NOT NULL DEFAULT 'INR',
    `payment_method` TEXT NOT NULL,
    `patient_id` TEXT,
    `visit_id` TEXT,
    `reference_number` TEXT,
    `remarks` TEXT,
    `recorded_by` TEXT NOT NULL,
    `is_synced` INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(`id`)
);

CREATE INDEX IF NOT EXISTS `index_finance_transactions_patient_id` ON `finance_transactions` (`patient_id`);
CREATE INDEX IF NOT EXISTS `index_finance_transactions_visit_id` ON `finance_transactions` (`visit_id`);
CREATE INDEX IF NOT EXISTS `index_finance_transactions_timestamp` ON `finance_transactions` (`timestamp`);
```
