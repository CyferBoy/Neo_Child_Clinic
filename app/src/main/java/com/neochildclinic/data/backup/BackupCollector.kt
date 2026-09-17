package com.neochildclinic.data.backup

import com.neochildclinic.data.local.dao.BackupDao

/**
 * Collects the current state of every backup-eligible table into a [BackupPayloadV1].
 * Read-only - never mutates anything. Used for both real exports and the internal
 * pre-restore safety snapshot.
 */
class BackupCollector(private val backupDao: BackupDao) {

    suspend fun collect(): BackupPayloadV1 {
        return BackupPayloadV1(
            // fcmToken is a device push token, not clinic data - never leaves the device.
            profiles = backupDao.getAllProfilesForBackup().map { it.copy(fcmToken = null) },
            vaccines = backupDao.getAllVaccinesForBackup(),
            vaccineBatches = backupDao.getAllVaccineBatchesForBackup(),
            doctorWeeklySlots = backupDao.getAllDoctorWeeklySlotsForBackup(),
            doctorSlotExceptions = backupDao.getAllDoctorSlotExceptionsForBackup(),
            patients = backupDao.getAllPatientsForBackup(),
            visits = backupDao.getAllVisitsForBackup(),
            vaccinationItems = backupDao.getAllVaccinationItemsForBackup(),
            consultations = backupDao.getAllConsultationsForBackup(),
            reminders = backupDao.getAllRemindersForBackup(),
            personalReminders = backupDao.getAllPersonalRemindersForBackup(),
            borrowRecords = backupDao.getAllBorrowRecordsForBackup(),
            borrowReturns = backupDao.getAllBorrowReturnsForBackup(),
            wasteRecords = backupDao.getAllWasteForBackup(),
            inventoryTransactions = backupDao.getAllInventoryTransactionsForBackup(),
            inventoryDeductions = backupDao.getAllInventoryDeductionsForBackup(),
            financeTransactions = backupDao.getAllFinanceForBackup(),
            expenses = backupDao.getAllExpensesForBackup(),
            patientNotes = backupDao.getAllPatientNotesForBackup(),
            consultationTodos = backupDao.getAllConsultationTodosForBackup(),
            vaccinationTodos = backupDao.getAllVaccinationTodosForBackup(),
            auditLogs = backupDao.getAllAuditLogsForBackup()
        )
    }
}
