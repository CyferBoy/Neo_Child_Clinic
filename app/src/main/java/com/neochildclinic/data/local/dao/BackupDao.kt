package com.neochildclinic.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
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

    @Upsert
    suspend fun insertProfiles(items: List<ProfileEntity>)

    @Query("DELETE FROM profiles")
    suspend fun clearProfiles()

    // ---- vaccines (catalog) ----
    @Query("SELECT * FROM vaccines")
    suspend fun getAllVaccinesForBackup(): List<VaccineEntity>

    @Upsert
    suspend fun insertVaccines(items: List<VaccineEntity>)

    @Query("DELETE FROM vaccines")
    suspend fun clearVaccines()

    // ---- vaccine_batches (depends on vaccines) ----
    @Query("SELECT * FROM vaccine_batches")
    suspend fun getAllVaccineBatchesForBackup(): List<VaccineBatchEntity>

    @Upsert
    suspend fun insertVaccineBatches(items: List<VaccineBatchEntity>)

    @Query("DELETE FROM vaccine_batches")
    suspend fun clearVaccineBatches()

    // ---- doctor_weekly_slots ----
    @Query("SELECT * FROM doctor_weekly_slots")
    suspend fun getAllDoctorWeeklySlotsForBackup(): List<DoctorWeeklySlotEntity>

    @Upsert
    suspend fun insertDoctorWeeklySlots(items: List<DoctorWeeklySlotEntity>)

    @Query("DELETE FROM doctor_weekly_slots")
    suspend fun clearDoctorWeeklySlots()

    // ---- doctor_slot_exceptions (loosely references doctor_weekly_slots, no enforced FK) ----
    @Query("SELECT * FROM doctor_slot_exceptions")
    suspend fun getAllDoctorSlotExceptionsForBackup(): List<DoctorSlotExceptionEntity>

    @Upsert
    suspend fun insertDoctorSlotExceptions(items: List<DoctorSlotExceptionEntity>)

    @Query("DELETE FROM doctor_slot_exceptions")
    suspend fun clearDoctorSlotExceptions()

    // ---- patients ----
    @Query("SELECT * FROM patients")
    suspend fun getAllPatientsForBackup(): List<PatientEntity>

    @Upsert
    suspend fun insertPatients(items: List<PatientEntity>)

    @Query("DELETE FROM patients")
    suspend fun clearPatients()

    // ---- patient_visits / vaccinations (depends on patients) ----
    @Query("SELECT * FROM patient_visits")
    suspend fun getAllVisitsForBackup(): List<VisitEntity>

    @Upsert
    suspend fun insertVisits(items: List<VisitEntity>)

    @Query("DELETE FROM patient_visits")
    suspend fun clearVisits()

    // ---- vaccination_items (depends on patient_visits, vaccines, vaccine_batches) ----
    @Query("SELECT * FROM vaccination_items")
    suspend fun getAllVaccinationItemsForBackup(): List<VaccinationItemEntity>

    @Upsert
    suspend fun insertVaccinationItems(items: List<VaccinationItemEntity>)

    @Query("DELETE FROM vaccination_items")
    suspend fun clearVaccinationItems()

    @Query("DELETE FROM vaccination_items WHERE vaccinationId = :visitId")
    suspend fun clearVaccinationItemsForVisit(visitId: String)

    // ---- consultations (depends on patients, patient_visits) ----
    @Query("SELECT * FROM consultations")
    suspend fun getAllConsultationsForBackup(): List<ConsultationEntity>

    @Upsert
    suspend fun insertConsultations(items: List<ConsultationEntity>)

    @Query("DELETE FROM consultations")
    suspend fun clearConsultations()

    // ---- reminders (depends on patients) ----
    @Query("SELECT * FROM reminders WHERE is_deleted = 0")
    suspend fun getAllRemindersForBackup(): List<ReminderEntity>

    @Upsert
    suspend fun insertReminders(items: List<ReminderEntity>)

    @Query("DELETE FROM reminders")
    suspend fun clearReminders()

    // ---- personal_vaccine_reminders (depends on patients, vaccines[SET NULL]) ----
    @Query("SELECT * FROM personal_vaccine_reminders")
    suspend fun getAllPersonalRemindersForBackup(): List<PersonalReminderEntity>

    @Upsert
    suspend fun insertPersonalReminders(items: List<PersonalReminderEntity>)

    @Query("DELETE FROM personal_vaccine_reminders")
    suspend fun clearPersonalReminders()

    // ---- borrow_records (depends on vaccines, vaccine_batches) ----
    @Query("SELECT * FROM borrow_records")
    suspend fun getAllBorrowRecordsForBackup(): List<BorrowEntity>

    @Upsert
    suspend fun insertBorrowRecords(items: List<BorrowEntity>)

    @Query("DELETE FROM borrow_records")
    suspend fun clearBorrowRecords()

    // ---- borrow_returns (depends on borrow_records, vaccine_batches) ----
    @Query("SELECT * FROM borrow_returns")
    suspend fun getAllBorrowReturnsForBackup(): List<BorrowReturnEntity>

    @Upsert
    suspend fun insertBorrowReturns(items: List<BorrowReturnEntity>)

    @Query("DELETE FROM borrow_returns")
    suspend fun clearBorrowReturns()

    // ---- waste_records ----
    @Query("SELECT * FROM waste_records")
    suspend fun getAllWasteForBackup(): List<WasteEntity>

    @Upsert
    suspend fun insertWaste(items: List<WasteEntity>)

    @Query("DELETE FROM waste_records")
    suspend fun clearWaste()

    // ---- inventory_transactions ----
    @Query("SELECT * FROM inventory_transactions")
    suspend fun getAllInventoryTransactionsForBackup(): List<InventoryTransactionEntity>

    @Upsert
    suspend fun insertInventoryTransactions(items: List<InventoryTransactionEntity>)

    @Query("DELETE FROM inventory_transactions")
    suspend fun clearInventoryTransactions()

    // ---- inventory_deductions ----
    @Query("SELECT * FROM inventory_deductions")
    suspend fun getAllInventoryDeductionsForBackup(): List<InventoryDeductionEntity>

    @Upsert
    suspend fun insertInventoryDeductions(items: List<InventoryDeductionEntity>)

    @Query("DELETE FROM inventory_deductions")
    suspend fun clearInventoryDeductions()

    // ---- finance_transactions ----
    @Query("SELECT * FROM finance_transactions")
    suspend fun getAllFinanceForBackup(): List<FinanceEntity>

    @Upsert
    suspend fun insertFinance(items: List<FinanceEntity>)

    @Query("DELETE FROM finance_transactions")
    suspend fun clearFinance()

    // ---- expenses ----
    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesForBackup(): List<ExpenseEntity>

    @Upsert
    suspend fun insertExpenses(items: List<ExpenseEntity>)

    @Query("DELETE FROM expenses")
    suspend fun clearExpenses()

    // ---- patient_notes (depends on patients) ----
    @Query("SELECT * FROM patient_notes")
    suspend fun getAllPatientNotesForBackup(): List<PatientNotesEntity>

    @Upsert
    suspend fun insertPatientNotes(items: List<PatientNotesEntity>)

    @Query("DELETE FROM patient_notes")
    suspend fun clearPatientNotes()

    // ---- consultation_todos / vaccination_todos (today's patient list) ----
    @Query("SELECT * FROM consultation_todos")
    suspend fun getAllConsultationTodosForBackup(): List<ConsultationTodoEntity>

    @Upsert
    suspend fun insertConsultationTodos(items: List<ConsultationTodoEntity>)

    @Query("DELETE FROM consultation_todos")
    suspend fun clearConsultationTodos()

    @Query("SELECT * FROM vaccination_todos")
    suspend fun getAllVaccinationTodosForBackup(): List<VaccinationTodoEntity>

    @Upsert
    suspend fun insertVaccinationTodos(items: List<VaccinationTodoEntity>)

    @Query("DELETE FROM vaccination_todos")
    suspend fun clearVaccinationTodos()

    // ---- audit_logs (append-only) ----
    @Query("SELECT * FROM audit_logs")
    suspend fun getAllAuditLogsForBackup(): List<AuditLogEntity>

    @Upsert
    suspend fun insertAuditLogs(items: List<AuditLogEntity>)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAuditLogs()

    // Note: BackupRestorer's merge-mode conflict resolution reuses the full-row
    // getAllXForBackup() queries above (building an id -> row map) rather than duplicating
    // id-only lookup queries here.

    // ---- backup history ----

    @Upsert
    suspend fun upsertHistory(entry: BackupHistoryEntity)

    @Query("SELECT * FROM backup_history ORDER BY createdAt DESC")
    fun observeHistory(): kotlinx.coroutines.flow.Flow<List<BackupHistoryEntity>>
}
