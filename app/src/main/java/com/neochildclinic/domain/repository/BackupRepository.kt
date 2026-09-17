package com.neochildclinic.domain.repository

import android.net.Uri
import com.neochildclinic.data.local.entity.BackupHistoryEntity
import com.neochildclinic.domain.model.*
import kotlinx.coroutines.flow.Flow

interface BackupRepository {

    // Passwords are CharArray, not String, throughout this interface and its
    // implementation - a JVM String is immutable and cannot be actively zeroed once no
    // longer needed, so it can linger in the heap for the life of the GC root holding it.
    // BackupRepositoryImpl clears every CharArray it derives a key from (success or
    // failure) as soon as that crypto operation completes. The one place a String is
    // still unavoidable is SecurityUtils.saveBackupPassword/getBackupPassword, because
    // EncryptedSharedPreferences' underlying Android API only accepts/returns String.

    // ---- Local export / import ----

    /** Collects all backup-eligible data, validates, encrypts with [password], and writes
     * the result to [uri] (an SAF destination the user picked). */
    suspend fun exportBackupToUri(
        uri: Uri,
        password: CharArray,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult

    /** Reads and decrypts the backup at [uri] just enough to show a restore summary,
     * without changing any local data. */
    suspend fun peekBackupFromUri(uri: Uri, password: CharArray): BackupValidationResult

    /** Performs the full restore flow: safety backup -> validate -> restore -> re-enqueue
     * unsynced rows -> refresh caches. Local data is left untouched if any step before the
     * final commit fails. */
    suspend fun restoreBackupFromUri(
        uri: Uri,
        password: CharArray,
        mode: RestoreMode,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult

    // ---- Cloud ----

    suspend fun isCloudConfigured(): Boolean

    suspend fun cloudBackupNow(
        password: CharArray,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult

    suspend fun listCloudBackups(): Result<List<CloudBackupMetadata>>

    /** Downloads + decrypts + validates [backupId] just enough to show a restore summary,
     * without restoring anything. cloudRestore() below re-downloads to actually restore -
     * an accepted tradeoff (a second download of a modest-sized backup) for keeping the
     * "show summary, then confirm" UX simple and this interface small. */
    suspend fun peekCloudBackup(backupId: String, password: CharArray): BackupValidationResult

    suspend fun cloudRestore(
        backupId: String,
        password: CharArray,
        mode: RestoreMode,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult

    suspend fun deleteCloudBackup(backupId: String): Result<Unit>

    // ---- History ----

    fun observeHistory(): Flow<List<BackupHistoryEntity>>

    // ---- Automatic backup settings (used by Settings screen + AutoBackupWorker) ----

    suspend fun getAutoBackupSettings(): AutoBackupSettings

    /** Turns Automatic Backup on. [password] is stored (Keystore-wrapped, see
     * SecurityUtils.saveBackupPassword) only because this flow must run unattended - manual
     * export/cloud-backup-now never persist a password. */
    suspend fun enableAutomaticBackup(password: CharArray, settings: AutoBackupSettings)

    /** Updates frequency/cloud/retention for an already-enabled Automatic Backup without
     * touching the stored password. */
    suspend fun updateAutomaticBackupSettings(settings: AutoBackupSettings)

    /** Turns Automatic Backup off and immediately erases the stored password. */
    suspend fun disableAutomaticBackup()

    /** Invoked by AutoBackupWorker. Performs a local export (and, if enabled, a cloud
     * backup) using the stored backup password, honoring retention. */
    suspend fun performAutomaticBackup(): BackupOperationResult

    // ---- Safety backup recovery ----

    /** Restores the most recent pre-restore safety snapshot (internal, app-private storage,
     * no password prompt needed). Used if the user wants to undo a restore. */
    suspend fun hasSafetyBackup(): Boolean

    suspend fun restoreSafetyBackup(onProgress: suspend (BackupProgress) -> Unit = {}): BackupOperationResult
}
