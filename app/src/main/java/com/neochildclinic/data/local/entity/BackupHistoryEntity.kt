package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Local record of one backup/restore operation (Settings -> Backup & Restore -> History).
 *
 * Deliberately holds only metadata - never patient/clinic data - so this table itself is
 * safe to show on screen and safe to include in a backup of the app's *own* operational
 * history (it is NOT part of the portable .nccb payload; see BackupModels.kt).
 *
 * [recordCountsJson] is a small JSON object of table -> row count (e.g. {"patients":120}),
 * used to render "120 patients, 45 consultations..." in the history list without ever
 * touching actual patient data.
 */
@Entity(
    tableName = "backup_history",
    indices = [Index("createdAt"), Index("type"), Index("status")]
)
@Serializable
data class BackupHistoryEntity(
    @PrimaryKey val id: String, // backupId (UUID), shared with the backup envelope's backupId
    val type: String, // BackupHistoryType name
    val location: String, // BackupLocation name: LOCAL | CLOUD
    val createdAt: String, // ISO-8601 UTC
    val sizeBytes: Long = 0,
    val backupVersion: Int = 0,
    val appVersion: String = "",
    val status: String, // BackupHistoryStatus name: SUCCESS | FAILED | IN_PROGRESS
    val errorMessage: String? = null, // sanitized - never patient data, never a raw stack trace
    val storagePath: String? = null, // local SAF URI string, or R2 object path for cloud
    val recordCountsJson: String? = null,
    val triggeredBy: String = "MANUAL" // MANUAL | AUTOMATIC
)

enum class BackupHistoryType {
    LOCAL_EXPORT, LOCAL_IMPORT, CLOUD_BACKUP, CLOUD_RESTORE, SAFETY_BACKUP
}

enum class BackupLocation { LOCAL, CLOUD }

enum class BackupHistoryStatus { SUCCESS, FAILED, IN_PROGRESS }
