package com.neochildclinic.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Builds/reads the JSON envelope described in BackupModels.kt and drives BackupCrypto for
 * the encrypted container. This is the single place that knows the on-disk file format.
 */
object BackupSerializer {

    val json = Json {
        ignoreUnknownKeys = true // lets an older reader open a slightly newer, additive-only backup
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = false
    }

    fun buildEnvelope(
        payload: BackupPayloadV1,
        appVersionName: String,
        appVersionCode: Int,
        databaseVersion: Int,
        createdByUserId: String?,
        deviceInfo: BackupDeviceInfo,
        createdAtIso: String,
        backupId: String = UUID.randomUUID().toString()
    ): BackupEnvelope {
        val dataJson = json.encodeToString(payload)
        val checksum = BackupCrypto.sha256Hex(dataJson.toByteArray(Charsets.UTF_8))
        return BackupEnvelope(
            backupVersion = CURRENT_BACKUP_VERSION,
            backupId = backupId,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            databaseVersion = databaseVersion,
            createdAt = createdAtIso,
            createdByUserId = createdByUserId,
            deviceInfo = deviceInfo,
            recordCounts = payload.recordCounts(),
            checksum = checksum,
            data = payload
        )
    }

    /** JSON-encodes then encrypts [envelope] into the final .nccb container bytes. */
    fun encodeToContainer(envelope: BackupEnvelope, password: CharArray): ByteArray {
        val envelopeJson = json.encodeToString(envelope)
        return BackupCrypto.encrypt(envelopeJson.toByteArray(Charsets.UTF_8), password)
    }

    /**
     * Decrypts + parses + validates [container], returning a [BackupEnvelope] whose
     * `backupVersion` is guaranteed <= [CURRENT_BACKUP_VERSION] and whose checksum has been
     * verified. Throws [BackupException] subtypes for every failure mode (wrong password,
     * corrupted file, unsupported/newer version).
     */
    fun decodeFromContainer(container: ByteArray, password: CharArray): BackupEnvelope {
        val plaintext = BackupCrypto.decrypt(container, password)

        val envelope = try {
            json.decodeFromString(BackupEnvelope.serializer(), String(plaintext, Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupException.Corrupted("JSON decode failed: ${e.javaClass.simpleName}", e)
        }

        if (envelope.backupVersion > CURRENT_BACKUP_VERSION) {
            throw BackupException.UnsupportedVersion(envelope.backupVersion)
        }

        val migrated = BackupMigrator.migrateIfNeeded(envelope)

        val recomputedChecksum = BackupCrypto.sha256Hex(json.encodeToString(migrated.data).toByteArray(Charsets.UTF_8))
        if (recomputedChecksum != migrated.checksum) {
            throw BackupException.Corrupted("Checksum mismatch - file is corrupted or was tampered with")
        }

        return migrated
    }
}

/**
 * Extension point for future backup format migrations. There is only one schema
 * (backupVersion = 1) today, so this is a no-op - when backupVersion 2 is introduced, add a
 * `1 ->` branch here that maps an old [BackupPayloadV1] onto the new payload shape and
 * returns an envelope with the upgraded `data`, rather than changing what readers of old
 * backups need to expect.
 */
object BackupMigrator {
    fun migrateIfNeeded(envelope: BackupEnvelope): BackupEnvelope = when (envelope.backupVersion) {
        CURRENT_BACKUP_VERSION -> envelope
        else -> throw BackupException.UnsupportedVersion(envelope.backupVersion)
    }
}
