package com.neochildclinic.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neochildclinic.data.local.entity.*

/**
 * Backup & Restore integration point.
 *
 * This DAO deliberately does NOT duplicate or replace any existing DAO/repository - it is
 * a second, additive Room DAO over the *same* tables the app already reads/writes through
 * PatientDao, VaccinationDao, ConsultationDao, etc. Room supports multiple DAOs over the
 * same entity; this one exists purely so the backup system has bulk get-all / insert-all /
 * clear-all operations in one place instead of scattering backup-only queries across 20
 * feature DAOs.
 *
 * Included tables mirror the mapping documented in docs/BACKUP_RESTORE.md ("Backup data
 * mapping"). Two real Room tables are intentionally NOT here:
 *  - sync_queue: transient outbox state, rebuilt after restore rather than restored verbatim.
 *  - widget_due_cache: a derived display cache, regenerated on refresh.
 *
 * Ordering: table groups below are listed parent-first. BackupRestorer inserts in exactly
 * this order (to satisfy FK constraints) and deletes in the reverse order.
 */
@Dao
interface BackupDao {

    // ---- profiles (doctor/staff) ----
    @Query("SELECT * FROM profiles")
    suspend fun getAllProfilesForBackup(): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(items: List<ProfileEntity>)

    @Query("DELETE FROM profiles")
    suspend fun clearProfiles()

    // ---- vaccines (catalog) ----
    @Query("SELECT * FROM vaccines")
    suspend fun getAllVaccinesForBackup(): List<VaccineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccines(items: List<VaccineEntity>)

    @Query("DELETE FROM vaccines")
    suspend fun clearVaccines()

    // ---- vaccine_batches (depends on vaccines) ----
    @Query("SELECT * FROM vaccine_batches")
    suspend fun getAllVaccineBatchesForBackup(): List<VaccineBatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccineBatches(items: List<VaccineBatchEntity>)

    @Query("DELETE FROM vaccine_batches")
    suspend fun clearVaccineBatches()

    // ---- doctor_weekly_slots ----
    @Query("SELECT * FROM doctor_weekly_slots")
    suspend fun getAllDoctorWeeklySlotsForBackup(): List<DoctorWeeklySlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoctorWeeklySlots(items: List<DoctorWeeklySlotEntity>)

    @Query("DELETE FROM doctor_weekly_slots")
    suspend fun clearDoctorWeeklySlots()

    // ---- doctor_slot_exceptions (loosely references doctor_weekly_slots, no enforced FK) ----
    @Query("SELECT * FROM doctor_slot_exceptions")
    suspend fun getAllDoctorSlotExceptionsForBackup(): List<DoctorSlotExceptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoctorSlotExceptions(items: List<DoctorSlotExceptionEntity>)

    @Query("DELETE FROM doctor_slot_exceptions")
    suspend fun clearDoctorSlotExceptions()

    // ---- patients ----
    @Query("SELECT * FROM patients")
    suspend fun getAllPatientsForBackup(): List<PatientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatients(items: List<PatientEntity>)

    @Query("DELETE FROM patients")
    suspend fun clearPatients()

    // ---- patient_visits / vaccinations (depends on patients) ----
    @Query("SELECT * FROM patient_visits")
    suspend fun getAllVisitsForBackup(): List<VisitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisits(items: List<VisitEntity>)

    @Query("DELETE FROM patient_visits")
    suspend fun clearVisits()

    // ---- vaccination_items (depends on patient_visits, vaccines, vaccine_batches) ----
    @Query("SELECT * FROM vaccination_items")
    suspend fun getAllVaccinationItemsForBackup(): List<VaccinationItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccinationItems(items: List<VaccinationItemEntity>)

    @Query("DELETE FROM vaccination_items")
    suspend fun clearVaccinationItems()

    @Query("DELETE FROM vaccination_items WHERE vaccinationId = :visitId")
    suspend fun clearVaccinationItemsForVisit(visitId: String)

    // ---- consultations (depends on patients, patient_visits) ----
    @Query("SELECT * FROM consultations")
    suspend fun getAllConsultationsForBackup(): List<ConsultationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultations(items: List<ConsultationEntity>)

    @Query("DELETE FROM consultations")
    suspend fun clearConsultations()

    // ---- reminders (depends on patients) ----
    @Query("SELECT * FROM reminders")
    suspend fun getAllRemindersForBackup(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(items: List<ReminderEntity>)

    @Query("DELETE FROM reminders")
    suspend fun clearReminders()

    // ---- personal_vaccine_reminders (depends on patients, vaccines[SET NULL]) ----
    @Query("SELECT * FROM personal_vaccine_reminders")
    suspend fun getAllPersonalRemindersForBackup(): List<PersonalReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonalReminders(items: List<PersonalReminderEntity>)

    @Query("DELETE FROM personal_vaccine_reminders")
    suspend fun clearPersonalReminders()

    // ---- borrow_records (depends on vaccines, vaccine_batches) ----
    @Query("SELECT * FROM borrow_records")
    suspend fun getAllBorrowRecordsForBackup(): List<BorrowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBorrowRecords(items: List<BorrowEntity>)

    @Query("DELETE FROM borrow_records")
    suspend fun clearBorrowRecords()

    // ---- borrow_returns (depends on borrow_records, vaccine_batches) ----
    @Query("SELECT * FROM borrow_returns")
    suspend fun getAllBorrowReturnsForBackup(): List<BorrowReturnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBorrowReturns(items: List<BorrowReturnEntity>)

    @Query("DELETE FROM borrow_returns")
    suspend fun clearBorrowReturns()

    // ---- waste_records ----
    @Query("SELECT * FROM waste_records")
    suspend fun getAllWasteForBackup(): List<WasteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaste(items: List<WasteEntity>)

    @Query("DELETE FROM waste_records")
    suspend fun clearWaste()

    // ---- inventory_transactions ----
    @Query("SELECT * FROM inventory_transactions")
    suspend fun getAllInventoryTransactionsForBackup(): List<InventoryTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryTransactions(items: List<InventoryTransactionEntity>)

    @Query("DELETE FROM inventory_transactions")
    suspend fun clearInventoryTransactions()

    // ---- inventory_deductions ----
    @Query("SELECT * FROM inventory_deductions")
    suspend fun getAllInventoryDeductionsForBackup(): List<InventoryDeductionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryDeductions(items: List<InventoryDeductionEntity>)

    @Query("DELETE FROM inventory_deductions")
    suspend fun clearInventoryDeductions()

    // ---- finance_transactions ----
    @Query("SELECT * FROM finance_transactions")
    suspend fun getAllFinanceForBackup(): List<FinanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinance(items: List<FinanceEntity>)

    @Query("DELETE FROM finance_transactions")
    suspend fun clearFinance()

    // ---- expenses ----
    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesForBackup(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(items: List<ExpenseEntity>)

    @Query("DELETE FROM expenses")
    suspend fun clearExpenses()

    // ---- patient_notes (depends on patients) ----
    @Query("SELECT * FROM patient_notes")
    suspend fun getAllPatientNotesForBackup(): List<PatientNotesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatientNotes(items: List<PatientNotesEntity>)

    @Query("DELETE FROM patient_notes")
    suspend fun clearPatientNotes()

    // ---- consultation_todos / vaccination_todos (today's patient list) ----
    @Query("SELECT * FROM consultation_todos")
    suspend fun getAllConsultationTodosForBackup(): List<ConsultationTodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultationTodos(items: List<ConsultationTodoEntity>)

    @Query("DELETE FROM consultation_todos")
    suspend fun clearConsultationTodos()

    @Query("SELECT * FROM vaccination_todos")
    suspend fun getAllVaccinationTodosForBackup(): List<VaccinationTodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccinationTodos(items: List<VaccinationTodoEntity>)

    @Query("DELETE FROM vaccination_todos")
    suspend fun clearVaccinationTodos()

    // ---- audit_logs (append-only) ----
    @Query("SELECT * FROM audit_logs")
    suspend fun getAllAuditLogsForBackup(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(items: List<AuditLogEntity>)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAuditLogs()

    // Note: BackupRestorer's merge-mode conflict resolution reuses the full-row
    // getAllXForBackup() queries above (building an id -> row map) rather than duplicating
    // id-only lookup queries here.

    // ---- backup history ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: BackupHistoryEntity)

    @Query("SELECT * FROM backup_history ORDER BY createdAt DESC")
    suspend fun getHistorySnapshot(): List<BackupHistoryEntity>

    @Query("SELECT * FROM backup_history ORDER BY createdAt DESC")
    fun observeHistory(): kotlinx.coroutines.flow.Flow<List<BackupHistoryEntity>>

    @Query("DELETE FROM backup_history WHERE id = :id")
    suspend fun deleteHistory(id: String)

    @Query("DELETE FROM backup_history WHERE createdAt < :before")
    suspend fun trimHistoryOlderThan(before: String)
}
