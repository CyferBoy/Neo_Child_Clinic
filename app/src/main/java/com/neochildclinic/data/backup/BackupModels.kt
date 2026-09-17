package com.neochildclinic.data.backup

import com.neochildclinic.data.local.entity.*
import kotlinx.serialization.Serializable

/**
 * The current backup schema version this build of the app writes. Bump this (and add a
 * migration branch in [BackupMigrator]) whenever the shape of [BackupPayloadV1] changes in
 * a way older readers can't already tolerate (kotlinx.serialization's `ignoreUnknownKeys`
 * plus default values on every field already lets us add optional fields without a version
 * bump - only breaking/renaming/removing a field needs one).
 */
const val CURRENT_BACKUP_VERSION = 1

/**
 * Every table backed up, reusing the app's existing @Serializable Room entities directly as
 * the wire format instead of introducing a parallel DTO layer. Each entity already carries
 * the @SerialName annotations used for the Supabase sync payloads, so the JSON produced here
 * uses the same snake_case field names as the rest of the app's remote data - one documented
 * shape, not two.
 *
 * Tables intentionally NOT included (see docs/BACKUP_RESTORE.md "Excluded data"):
 *  - sync_queue: transient outbox state, rebuilt after restore.
 *  - widget_due_cache: derived display cache, regenerated on refresh.
 *  - backup_history: this table describes backups, it isn't clinic data.
 *  - ProfileEntity.fcmToken is always stripped to null before export (device token).
 */
@Serializable
data class BackupPayloadV1(
    val profiles: List<ProfileEntity> = emptyList(),
    val vaccines: List<VaccineEntity> = emptyList(),
    val vaccineBatches: List<VaccineBatchEntity> = emptyList(),
    val doctorWeeklySlots: List<DoctorWeeklySlotEntity> = emptyList(),
    val doctorSlotExceptions: List<DoctorSlotExceptionEntity> = emptyList(),
    val patients: List<PatientEntity> = emptyList(),
    val visits: List<VisitEntity> = emptyList(),
    val vaccinationItems: List<VaccinationItemEntity> = emptyList(),
    val consultations: List<ConsultationEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val personalReminders: List<PersonalReminderEntity> = emptyList(),
    val borrowRecords: List<BorrowEntity> = emptyList(),
    val borrowReturns: List<BorrowReturnEntity> = emptyList(),
    val wasteRecords: List<WasteEntity> = emptyList(),
    val inventoryTransactions: List<InventoryTransactionEntity> = emptyList(),
    val inventoryDeductions: List<InventoryDeductionEntity> = emptyList(),
    val financeTransactions: List<FinanceEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val patientNotes: List<PatientNotesEntity> = emptyList(),
    val consultationTodos: List<ConsultationTodoEntity> = emptyList(),
    val vaccinationTodos: List<VaccinationTodoEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList()
) {
    /** Table label -> row count, for progress UI, restore summaries, and backup_history -
     * never anything more specific than a count (req. 18: no sensitive data in history). */
    fun recordCounts(): Map<String, Int> = linkedMapOf(
        "profiles" to profiles.size,
        "vaccines" to vaccines.size,
        "vaccineBatches" to vaccineBatches.size,
        "doctorWeeklySlots" to doctorWeeklySlots.size,
        "doctorSlotExceptions" to doctorSlotExceptions.size,
        "patients" to patients.size,
        "vaccinations" to visits.size,
        "vaccinationItems" to vaccinationItems.size,
        "consultations" to consultations.size,
        "reminders" to reminders.size,
        "personalReminders" to personalReminders.size,
        "borrowRecords" to borrowRecords.size,
        "borrowReturns" to borrowReturns.size,
        "wasteRecords" to wasteRecords.size,
        "inventoryTransactions" to inventoryTransactions.size,
        "inventoryDeductions" to inventoryDeductions.size,
        "financeTransactions" to financeTransactions.size,
        "expenses" to expenses.size,
        "patientNotes" to patientNotes.size,
        "consultationTodos" to consultationTodos.size,
        "vaccinationTodos" to vaccinationTodos.size,
        "auditLogs" to auditLogs.size
    )
}

@Serializable
data class BackupDeviceInfo(
    val platform: String = "Android",
    val osVersion: String = "",
    val model: String = ""
)

/**
 * The full container that gets JSON-encoded and then AES-GCM encrypted (see
 * BackupCrypto.kt). [checksum] is the SHA-256 of the canonical JSON encoding of [data],
 * computed before encryption and re-verified after decryption - this is what lets a wrong
 * password (which decrypts to garbage bytes under GCM's own tag check) and a merely
 * bit-flipped-but-tag-valid file be told apart from a genuinely well-formed backup.
 */
@Serializable
data class BackupEnvelope(
    val backupVersion: Int = CURRENT_BACKUP_VERSION,
    val backupId: String,
    val appName: String = "Neo Child Clinic",
    val appVersionName: String,
    val appVersionCode: Int,
    val databaseVersion: Int,
    val createdAt: String,
    val createdByUserId: String? = null,
    val deviceInfo: BackupDeviceInfo = BackupDeviceInfo(),
    val recordCounts: Map<String, Int> = emptyMap(),
    val checksum: String,
    val data: BackupPayloadV1
)
