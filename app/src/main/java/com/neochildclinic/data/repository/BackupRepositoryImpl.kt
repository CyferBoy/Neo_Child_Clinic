package com.neochildclinic.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.neochildclinic.BuildConfig
import com.neochildclinic.core.network.NetworkMonitor
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.core.utils.SecurityUtils
import com.neochildclinic.data.backup.*
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.BackupHistoryEntity
import com.neochildclinic.data.local.entity.BackupHistoryStatus
import com.neochildclinic.data.local.entity.BackupHistoryType
import com.neochildclinic.data.local.entity.BackupLocation
import com.neochildclinic.domain.manager.SyncManager
import com.neochildclinic.domain.model.*
import com.neochildclinic.features.settings.BackupSettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BackupRepository"

@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val cloudApi: CloudBackupApi,
    private val syncManager: SyncManager,
    private val networkMonitor: NetworkMonitor,
    private val backupSettingsManager: BackupSettingsManager,
    private val auth: Auth
) {

    private val backupDao = database.backupDao()
    private val collector = BackupCollector(backupDao)
    private val restorer = BackupRestorer(database, backupDao)
    private val safetyStore = SafetyBackupStore(context)

    // ============================================================== Local export ====

    suspend fun exportBackupToUri(
        uri: Uri,
        password: CharArray,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult = withContext(Dispatchers.IO) {
        try {
            runCatching {
                onProgress(BackupProgress.Stage("Creating backup..."))
                val payload = collector.collect()
                val envelope = buildEnvelope(payload)

                onProgress(BackupProgress.Stage("Validating..."))
                BackupValidator.validate(envelope) // throws BackupException if inconsistent

                onProgress(BackupProgress.Stage("Encrypting..."))
                val container = BackupSerializer.encodeToContainer(envelope, password)

            onProgress(BackupProgress.Stage("Saving..."))
            writeBytesToUri(uri, container)

            recordHistory(
                id = envelope.backupId, type = BackupHistoryType.LOCAL_EXPORT, location = BackupLocation.LOCAL,
                sizeBytes = container.size.toLong(), envelope = envelope, status = BackupHistoryStatus.SUCCESS,
                storagePath = uri.toString(), triggeredBy = "MANUAL"
            )
            onProgress(BackupProgress.Done)
            BackupOperationResult(true, envelope.backupId, container.size.toLong(), envelope.recordCounts)
        }.getOrElse { e -> handleFailure(e, BackupHistoryType.LOCAL_EXPORT, BackupLocation.LOCAL) }
        } finally {
            password.fill('\u0000')
        }
    }

    // ============================================================== Local import ====

    suspend fun peekBackupFromUri(uri: Uri, password: CharArray): BackupValidationResult =
        withContext(Dispatchers.IO) {
            try {
                val bytes = readBytesFromUri(uri) ?: return@withContext BackupValidationResult.Invalid(
                    BackupFailureReason.CORRUPTED_OR_INCOMPLETE, "Could not read the selected file."
                )
                val envelope = BackupSerializer.decodeFromContainer(bytes, password)
                val summary = BackupValidator.validate(envelope)
                BackupValidationResult.Valid(summary)
            } catch (e: BackupException) {
                BackupValidationResult.Invalid(e.reason, e.userMessage)
            } catch (e: Exception) {
                Log.e(TAG, "peekBackupFromUri failed", e)
                BackupValidationResult.Invalid(BackupFailureReason.UNKNOWN, "Backup failed.\nPlease try again.")
            } finally {
                password.fill('\u0000')
            }
        }

    suspend fun restoreBackupFromUri(
        uri: Uri,
        password: CharArray,
        mode: RestoreMode,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult = withContext(Dispatchers.IO) {
        try {
            runCatching {
                onProgress(BackupProgress.Stage("Preparing restore..."))
                val bytes = readBytesFromUri(uri) ?: throw BackupException.Corrupted("Could not read the selected file")

                onProgress(BackupProgress.Stage("Validating backup..."))
                val envelope = BackupSerializer.decodeFromContainer(bytes, password)
                BackupValidator.validate(envelope)

                val outcome = performRestore(envelope, mode, onProgress)

                recordHistory(
                    id = UUID.randomUUID().toString(), type = BackupHistoryType.LOCAL_IMPORT, location = BackupLocation.LOCAL,
                    sizeBytes = bytes.size.toLong(), envelope = envelope, status = BackupHistoryStatus.SUCCESS,
                    storagePath = uri.toString(), triggeredBy = "MANUAL", overrideCounts = outcome.appliedRecordCounts
                )
                onProgress(BackupProgress.Done)
                BackupOperationResult(true, envelope.backupId, bytes.size.toLong(), outcome.appliedRecordCounts)
            }.getOrElse { e -> handleFailure(e, BackupHistoryType.LOCAL_IMPORT, BackupLocation.LOCAL) }
        } finally {
            password.fill('\u0000')
        }
    }

    /** Shared by local and cloud restore: safety snapshot -> apply -> resync trigger. */
    private suspend fun performRestore(
        envelope: BackupEnvelope,
        mode: RestoreMode,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupRestorer.Outcome {
        onProgress(BackupProgress.Stage("Creating safety backup..."))
        try {
            val currentPayload = collector.collect()
            safetyStore.write(BackupSerializer.json.encodeToString(currentPayload).toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            // A failed safety snapshot must not silently allow a destructive restore to
            // proceed - fail closed.
            throw BackupException.Failed("Could not create pre-restore safety backup", e)
        }

        onProgress(BackupProgress.Stage("Restoring data..."))
        val outcome = try {
            restorer.restore(envelope.data, mode)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            // Room's withTransaction rolls back automatically on any thrown exception -
            // existing data is guaranteed intact.
            throw BackupException.Failed("Restore transaction failed, rolled back", e)
        }

        onProgress(BackupProgress.Stage("Rebuilding indexes..."))
        // Room maintains its own indexes as part of the same transaction; nothing extra
        // needed here beyond letting callers know the stage happened.

        onProgress(BackupProgress.Stage("Refreshing data..."))
        syncManager.scheduleImmediateSync() // pushes any rows performRestore re-enqueued

        return outcome
    }

    // ==================================================================== Cloud ====

    suspend fun isCloudConfigured(): Boolean = cloudApi.isConfigured()

    suspend fun cloudBackupNow(
        password: CharArray,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult = withContext(Dispatchers.IO) {
        try {
            runCatching {
                if (!cloudApi.isConfigured()) throw BackupException.Failed("Cloud backup is not configured for this build")
                if (!networkMonitor.isOnline.first()) throw BackupException.NoInternet()

                onProgress(BackupProgress.Stage("Creating backup..."))
                val payload = collector.collect()
                val envelope = buildEnvelope(payload)

                onProgress(BackupProgress.Stage("Validating..."))
                BackupValidator.validate(envelope)

                onProgress(BackupProgress.Stage("Encrypting..."))
                val container = BackupSerializer.encodeToContainer(envelope, password)

            onProgress(BackupProgress.Stage("Uploading..."))
            val metadata = cloudApi.upload(envelope.backupId, container, envelope.appVersionName, envelope.backupVersion, envelope.createdAt)

            onProgress(BackupProgress.Stage("Verifying upload..."))
            // cloudApi.upload() only returns after the Worker has confirmed the object
            // exists in R2 with the expected size (see cloudflare/backup-worker) - a
            // failed/partial upload throws before reaching this point, so the previous
            // successful cloud backup is never removed by a bad new one.

            val settings = backupSettingsManager.getSettings()
            runCatching { cloudApi.applyRetention(settings.retainCount) }
                .onFailure { Log.e(TAG, "Retention cleanup failed (non-fatal)", it) }

            recordHistory(
                id = envelope.backupId, type = BackupHistoryType.CLOUD_BACKUP, location = BackupLocation.CLOUD,
                sizeBytes = container.size.toLong(), envelope = envelope, status = BackupHistoryStatus.SUCCESS,
                storagePath = "backups/${auth.currentSessionOrNull()?.user?.id}/${envelope.backupId}/backup.nccb",
                triggeredBy = "MANUAL"
            )
            onProgress(BackupProgress.Done)
            BackupOperationResult(true, metadata.backupId, metadata.sizeBytes, envelope.recordCounts)
        }.getOrElse { e -> handleFailure(e, BackupHistoryType.CLOUD_BACKUP, BackupLocation.CLOUD) }
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun listCloudBackups(): Result<List<CloudBackupMetadata>> = withContext(Dispatchers.IO) {
        if (!cloudApi.isConfigured()) return@withContext Result.failure(BackupException.Failed("Cloud backup is not configured"))
        runCatching { cloudApi.list() }
    }

    suspend fun peekCloudBackup(backupId: String, password: CharArray): BackupValidationResult =
        withContext(Dispatchers.IO) {
            try {
                if (!networkMonitor.isOnline.first()) return@withContext BackupValidationResult.Invalid(
                    BackupFailureReason.NO_INTERNET, "No internet connection. Cloud backup requires an internet connection - your local data is unaffected."
                )
                val bytes = cloudApi.download(backupId)
                val envelope = BackupSerializer.decodeFromContainer(bytes, password)
                val summary = BackupValidator.validate(envelope)
                BackupValidationResult.Valid(summary)
            } catch (e: BackupException) {
                BackupValidationResult.Invalid(e.reason, e.userMessage)
            } catch (e: Exception) {
                Log.e(TAG, "peekCloudBackup failed", e)
                BackupValidationResult.Invalid(BackupFailureReason.UNKNOWN, "Backup failed.\nPlease try again.")
            } finally {
                password.fill('\u0000')
            }
        }

    suspend fun cloudRestore(
        backupId: String,
        password: CharArray,
        mode: RestoreMode,
        onProgress: suspend (BackupProgress) -> Unit = {}
    ): BackupOperationResult = withContext(Dispatchers.IO) {
        try {
            runCatching {
                if (!networkMonitor.isOnline.first()) throw BackupException.NoInternet()

                onProgress(BackupProgress.Stage("Downloading backup..."))
                val bytes = cloudApi.download(backupId)

                onProgress(BackupProgress.Stage("Verifying integrity..."))
                val envelope = BackupSerializer.decodeFromContainer(bytes, password)
                BackupValidator.validate(envelope)

                val outcome = performRestore(envelope, mode, onProgress)

                recordHistory(
                    id = UUID.randomUUID().toString(), type = BackupHistoryType.CLOUD_RESTORE, location = BackupLocation.CLOUD,
                    sizeBytes = bytes.size.toLong(), envelope = envelope, status = BackupHistoryStatus.SUCCESS,
                    storagePath = backupId, triggeredBy = "MANUAL", overrideCounts = outcome.appliedRecordCounts
                )
                onProgress(BackupProgress.Done)
                BackupOperationResult(true, envelope.backupId, bytes.size.toLong(), outcome.appliedRecordCounts)
            }.getOrElse { e -> handleFailure(e, BackupHistoryType.CLOUD_RESTORE, BackupLocation.CLOUD) }
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun deleteCloudBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { cloudApi.delete(backupId) }
    }

    // ================================================================= History ====

    fun observeHistory(): Flow<List<BackupHistoryEntity>> = backupDao.observeHistory()

    // ============================================================== Automatic ====

    suspend fun getAutoBackupSettings(): AutoBackupSettings = backupSettingsManager.getSettings()

    suspend fun enableAutomaticBackup(password: CharArray, settings: AutoBackupSettings) {
        try {
            // EncryptedSharedPreferences (SecurityUtils) only accepts String - this is the
            // one unavoidable String boundary in this feature, since Automatic Backup must
            // survive an app restart with nobody present to re-type the password.
            SecurityUtils.saveBackupPassword(context, String(password))
            backupSettingsManager.updateSettings(settings.copy(enabled = true))
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun updateAutomaticBackupSettings(settings: AutoBackupSettings) {
        val current = backupSettingsManager.getSettings()
        backupSettingsManager.updateSettings(settings.copy(enabled = current.enabled))
    }

    suspend fun disableAutomaticBackup() {
        backupSettingsManager.updateSettings(backupSettingsManager.getSettings().copy(enabled = false))
        SecurityUtils.clearBackupPassword(context)
    }

    /** Called by AutoBackupWorker. Never prompts for anything - uses the password stored
     * when the user enabled Automatic Backup. */
    suspend fun performAutomaticBackup(): BackupOperationResult = withContext(Dispatchers.IO) {
        val settings = backupSettingsManager.getSettings()
        if (!settings.enabled) return@withContext BackupOperationResult(false, userMessage = "Automatic backup is disabled")
        val storedPassword = SecurityUtils.getBackupPassword(context)
            ?: return@withContext BackupOperationResult(false, failureReason = BackupFailureReason.UNKNOWN, userMessage = "No backup password set")
        // Converted to CharArray immediately and cleared below once this run is done -
        // storedPassword itself is a String (see enableAutomaticBackup) and can't be
        // zeroed, but its lifetime here is kept as short as possible.
        val password = storedPassword.toCharArray()

        try {
            val result = if (settings.cloudEnabled) cloudBackupNow(password.copyOf()) else {
                // A local automatic backup still needs a destination: written to app-private
                // storage (not user-visible SAF) since there is no one present to pick a
                // folder. It is retained/rotated the same way cloud backups are.
                runCatching {
                    val payload = collector.collect()
                    val envelope = buildEnvelope(payload)
                    BackupValidator.validate(envelope)
                    val container = BackupSerializer.encodeToContainer(envelope, password.copyOf())
                    val dir = java.io.File(context.filesDir, "backups/automatic").apply { mkdirs() }
                    val file = java.io.File(dir, "${envelope.backupId}.nccb")
                    file.writeBytes(container)
                    rotateAutomaticLocalBackups(dir, settings.retainCount)
                    recordHistory(
                        id = envelope.backupId, type = BackupHistoryType.LOCAL_EXPORT, location = BackupLocation.LOCAL,
                        sizeBytes = container.size.toLong(), envelope = envelope, status = BackupHistoryStatus.SUCCESS,
                        storagePath = file.absolutePath, triggeredBy = "AUTOMATIC"
                    )
                    BackupOperationResult(true, envelope.backupId, container.size.toLong(), envelope.recordCounts)
                }.getOrElse { e -> handleFailure(e, BackupHistoryType.LOCAL_EXPORT, BackupLocation.LOCAL, triggeredBy = "AUTOMATIC") }
            }

            backupSettingsManager.recordRun(
                atIso = PatientUtils.getCurrentIsoTimestamp(),
                status = if (result.success) "SUCCESS" else "FAILED"
            )
            result
        } finally {
            password.fill('\u0000')
        }
    }

    private fun rotateAutomaticLocalBackups(dir: java.io.File, keep: Int) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(keep.coerceAtLeast(1)).forEach { it.delete() }
    }

    // ================================================================== Safety ====

    suspend fun hasSafetyBackup(): Boolean = withContext(Dispatchers.IO) { safetyStore.exists() }

    suspend fun restoreSafetyBackup(onProgress: suspend (BackupProgress) -> Unit = {}): BackupOperationResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = safetyStore.read() ?: throw BackupException.Corrupted("No safety backup is available")
                onProgress(BackupProgress.Stage("Restoring previous data..."))
                val payload = BackupSerializer.json.decodeFromString(BackupPayloadV1.serializer(), String(bytes, Charsets.UTF_8))
                val outcome = restorer.restore(payload, RestoreMode.REPLACE)
                syncManager.scheduleImmediateSync()
                onProgress(BackupProgress.Done)
                BackupOperationResult(true, recordCounts = outcome.appliedRecordCounts)
            }.getOrElse { e -> handleFailure(e, BackupHistoryType.SAFETY_BACKUP, BackupLocation.LOCAL) }
        }

    // ================================================================== Helpers ====

    private suspend fun buildEnvelope(payload: BackupPayloadV1): BackupEnvelope {
        return BackupSerializer.buildEnvelope(
            payload = payload,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            databaseVersion = AppDatabase.DB_VERSION,
            createdByUserId = auth.currentSessionOrNull()?.user?.id,
            deviceInfo = BackupDeviceInfo(platform = "Android", osVersion = Build.VERSION.RELEASE ?: "", model = Build.MODEL ?: ""),
            createdAtIso = PatientUtils.getCurrentIsoTimestamp()
        )
    }

    private fun writeBytesToUri(uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw BackupException.Failed("Could not open destination for writing")
    }

    private fun readBytesFromUri(uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

    private suspend fun recordHistory(
        id: String,
        type: BackupHistoryType,
        location: BackupLocation,
        sizeBytes: Long,
        envelope: BackupEnvelope,
        status: BackupHistoryStatus,
        storagePath: String?,
        triggeredBy: String,
        overrideCounts: Map<String, Int>? = null
    ) {
        val counts = overrideCounts ?: envelope.recordCounts
        backupDao.upsertHistory(
            BackupHistoryEntity(
                id = id, type = type.name, location = location.name, createdAt = PatientUtils.getCurrentIsoTimestamp(),
                sizeBytes = sizeBytes, backupVersion = envelope.backupVersion, appVersion = envelope.appVersionName,
                status = status.name, storagePath = storagePath,
                recordCountsJson = BackupSerializer.json.encodeToString(counts), triggeredBy = triggeredBy
            )
        )
    }

    private suspend fun handleFailure(
        e: Throwable,
        type: BackupHistoryType,
        location: BackupLocation,
        triggeredBy: String = "MANUAL"
    ): BackupOperationResult {
        val backupException = e as? BackupException ?: BackupException.Failed(e.message ?: "Unknown error", e)
        Log.e(TAG, "${type.name} failed: ${backupException.logDetail}", backupException)
        runCatching {
            backupDao.upsertHistory(
                BackupHistoryEntity(
                    id = UUID.randomUUID().toString(), type = type.name, location = location.name,
                    createdAt = PatientUtils.getCurrentIsoTimestamp(), status = BackupHistoryStatus.FAILED.name,
                    errorMessage = backupException.reason.name, triggeredBy = triggeredBy
                )
            )
        }
        return BackupOperationResult(
            success = false,
            failureReason = backupException.reason,
            userMessage = backupException.userMessage
        )
    }
}
