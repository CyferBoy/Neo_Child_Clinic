package com.neochildclinic.data.backup

import com.neochildclinic.domain.model.BackupFailureReason

/**
 * Every backup/restore failure surfaces as one of these. [userMessage] is exactly what the
 * UI shows (matches the wording in the spec). [logDetail] is what gets Log.e'd - technical,
 * never patient data, never a password, key, or credential. See docs/BACKUP_RESTORE.md
 * "Error handling" for the full list.
 */
sealed class BackupException(
    val reason: BackupFailureReason,
    val userMessage: String,
    val logDetail: String,
    cause: Throwable? = null
) : Exception(logDetail, cause) {

    class Corrupted(logDetail: String, cause: Throwable? = null) : BackupException(
        BackupFailureReason.CORRUPTED_OR_INCOMPLETE,
        "This backup is corrupted or incomplete.\nYour current data has not been changed.",
        logDetail, cause
    )

    class WrongPassword(logDetail: String = "GCM tag verification failed", cause: Throwable? = null) : BackupException(
        BackupFailureReason.WRONG_PASSWORD,
        "Incorrect backup password.\nYour current data has not been changed.",
        logDetail, cause
    )

    class UnsupportedVersion(foundVersion: Int) : BackupException(
        BackupFailureReason.UNSUPPORTED_VERSION_TOO_NEW,
        "This backup was created by a newer version of Neo Child Clinic.\nPlease update the app before restoring it.",
        "backupVersion=$foundVersion > supported=$CURRENT_BACKUP_VERSION"
    )

    class MissingFields(missing: List<String>) : BackupException(
        BackupFailureReason.MISSING_REQUIRED_FIELDS,
        "This backup is missing required data and cannot be restored safely.",
        "Missing/invalid fields: ${missing.joinToString(limit = 20)}"
    )

    class BrokenRelationships(issues: List<String>) : BackupException(
        BackupFailureReason.BROKEN_RELATIONSHIPS,
        "This backup contains inconsistent data and cannot be restored safely.",
        "Relationship issues: ${issues.joinToString(limit = 20)}"
    )

    class NoInternet : BackupException(
        BackupFailureReason.NO_INTERNET,
        "No internet connection. Cloud backup requires an internet connection - your local data is unaffected.",
        "No network available"
    )

    class Timeout(cause: Throwable? = null) : BackupException(
        BackupFailureReason.TIMEOUT,
        "The connection timed out. Please try again.",
        "Request timed out", cause
    )

    class AuthExpired(cause: Throwable? = null) : BackupException(
        BackupFailureReason.AUTH_EXPIRED,
        "Your session has expired. Please sign in again and retry.",
        "Auth session expired/refresh failed", cause
    )

    class Unauthorized(serverDetail: String? = null) : BackupException(
        BackupFailureReason.UNAUTHORIZED,
        "You are not authorized to access this backup.",
        "Server returned 401/403" + (serverDetail?.let { ": $it" } ?: "")
    )

    class UploadFailed(cause: Throwable? = null) : BackupException(
        BackupFailureReason.UPLOAD_FAILED,
        "Backup upload failed. Please try again.",
        "Upload to cloud storage failed", cause
    )

    class DownloadFailed(cause: Throwable? = null) : BackupException(
        BackupFailureReason.DOWNLOAD_FAILED,
        "Backup download failed. Please try again.",
        "Download from cloud storage failed", cause
    )

    class ServerError(code: Int? = null, cause: Throwable? = null, serverDetail: String? = null) : BackupException(
        BackupFailureReason.SERVER_ERROR,
        "The backup server encountered an error. Please try again later.",
        "Server error${code?.let { " (HTTP $it)" } ?: ""}" + (serverDetail?.let { ": $it" } ?: ""), cause
    )

    class InsufficientStorage : BackupException(
        BackupFailureReason.INSUFFICIENT_STORAGE,
        "Not enough storage space to complete the backup.",
        "Insufficient storage"
    )

    class Interrupted(cause: Throwable? = null) : BackupException(
        BackupFailureReason.INTERRUPTED,
        "The operation was interrupted. Your current data has not been changed.",
        "Operation interrupted", cause
    )

    /** Mirrors the Worker's MAX_BACKUP_BYTES ceiling (cloudflare/backup-worker/src/index.ts) -
     * caught client-side before attempting an upload that the server would reject anyway,
     * and also raised if the server responds 413 for any other reason (e.g. the two limits
     * drift out of sync in a future change). */
    class TooLarge(sizeBytes: Long, maxBytes: Long) : BackupException(
        BackupFailureReason.TOO_LARGE,
        "This backup is too large to upload to Cloud Backup.\nTry Local Export/Import instead.",
        "Backup size $sizeBytes bytes exceeds server limit of $maxBytes bytes"
    )

    class Failed(logDetail: String = "Backup failed", cause: Throwable? = null) : BackupException(
        BackupFailureReason.UNKNOWN,
        "Backup failed.\nPlease try again.",
        logDetail, cause
    )
}
