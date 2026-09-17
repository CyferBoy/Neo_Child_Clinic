package com.neochildclinic.domain.model

/** How a restore should reconcile backup rows against rows already in the local database. */
enum class RestoreMode {
    /** Wipe existing app data and replace it entirely with the backup's contents. */
    REPLACE,

    /** Merge backup rows into existing data using the deterministic per-table rules
     * documented in BackupRestorer.kt (last-write-wins on updated_at where available,
     * insert-if-absent for append-only/no-timestamp tables). */
    MERGE
}

/** One step of an in-progress export/import/backup/restore, for progress UI. */
sealed class BackupProgress {
    data class Stage(val label: String, val current: Int = 0, val total: Int = 0) : BackupProgress()
    data class TableDone(val tableLabel: String) : BackupProgress()
    data object Done : BackupProgress()
}

data class RestoreSummary(
    val backupId: String,
    val createdAt: String,
    val appVersion: String,
    val backupVersion: Int,
    val recordCounts: Map<String, Int>,
    val warnings: List<String> = emptyList()
)

sealed class BackupValidationResult {
    data class Valid(val summary: RestoreSummary) : BackupValidationResult()
    data class Invalid(val reason: BackupFailureReason, val message: String) : BackupValidationResult()
}

enum class BackupFailureReason {
    CORRUPTED_OR_INCOMPLETE,
    WRONG_PASSWORD,
    UNSUPPORTED_VERSION_TOO_NEW,
    MISSING_REQUIRED_FIELDS,
    BROKEN_RELATIONSHIPS,
    NO_INTERNET,
    TIMEOUT,
    AUTH_EXPIRED,
    UNAUTHORIZED,
    UPLOAD_FAILED,
    DOWNLOAD_FAILED,
    SERVER_ERROR,
    INSUFFICIENT_STORAGE,
    INTERRUPTED,
    TOO_LARGE,
    UNKNOWN
}

data class BackupOperationResult(
    val success: Boolean,
    val backupId: String? = null,
    val sizeBytes: Long = 0,
    val recordCounts: Map<String, Int> = emptyMap(),
    val failureReason: BackupFailureReason? = null,
    val userMessage: String? = null
)

data class CloudBackupMetadata(
    val backupId: String,
    val createdAt: String,
    val sizeBytes: Long,
    val appVersion: String,
    val backupVersion: Int,
    val status: String
)

enum class BackupFrequency { DAILY, WEEKLY }

data class AutoBackupSettings(
    val enabled: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.DAILY,
    val cloudEnabled: Boolean = false,
    val retainCount: Int = 5,
    val lastRunAt: String? = null,
    val lastRunStatus: String? = null
)
