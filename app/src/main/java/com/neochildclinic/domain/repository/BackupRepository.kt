package com.neochildclinic.domain.repository

import android.net.Uri
import com.neochildclinic.data.local.entity.BackupHistoryEntity
import com.neochildclinic.domain.model.AutoBackupSettings
import com.neochildclinic.domain.model.BackupOperationResult
import com.neochildclinic.domain.model.BackupProgress
import com.neochildclinic.domain.model.BackupValidationResult
import com.neochildclinic.domain.model.CloudBackupMetadata
import com.neochildclinic.domain.model.RestoreMode
import kotlinx.coroutines.flow.Flow

interface BackupRepository {

    // ============================================================== Local export ====

    suspend fun exportBackupToUri(
        uri: Uri,
        password: CharArray,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult

    // ============================================================== Local import ====

    suspend fun peekBackupFromUri(uri: Uri, password: CharArray): BackupValidationResult

    suspend fun restoreBackupFromUri(
        uri: Uri,
        password: CharArray,
        mode: RestoreMode,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult

    // ==================================================================== Cloud ====

    suspend fun isCloudConfigured(): Boolean

    suspend fun cloudBackupNow(
        password: CharArray,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult

    suspend fun listCloudBackups(): Result<List<CloudBackupMetadata>>

    suspend fun peekCloudBackup(backupId: String, password: CharArray): BackupValidationResult

    suspend fun cloudRestore(
        backupId: String,
        password: CharArray,
        mode: RestoreMode,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult

    suspend fun deleteCloudBackup(backupId: String): Result<Unit>

    // ================================================================= History ====

    fun observeHistory(): Flow<List<BackupHistoryEntity>>

    // ============================================================== Automatic ====

    suspend fun getAutoBackupSettings(): AutoBackupSettings

    suspend fun enableAutomaticBackup(password: CharArray, settings: AutoBackupSettings)

    suspend fun updateAutomaticBackupSettings(settings: AutoBackupSettings)

    suspend fun disableAutomaticBackup()

    /** Called by AutoBackupWorker. Never prompts for anything - uses the password stored
     * when the user enabled Automatic Backup. */
    suspend fun performAutomaticBackup(): BackupOperationResult

    // ================================================================== Safety ====

    suspend fun hasSafetyBackup(): Boolean

    suspend fun restoreSafetyBackup(onProgress: suspend (BackupProgress) -> Unit = {}): BackupOperationResult
}