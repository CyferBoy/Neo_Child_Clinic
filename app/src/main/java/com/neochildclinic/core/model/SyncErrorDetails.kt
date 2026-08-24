package com.neochildclinic.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Structured sync diagnostics. The payload is stored in the existing lastError column
 * so no Room migration is required. Sensitive HTTP headers are filtered before storage.
 */
@Serializable
data class SyncErrorDetails(
    val reason: String,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap()
) {
    fun encode(): String = PREFIX + Json.encodeToString(this)

    companion object {
        private const val PREFIX = "SYNC_DIAGNOSTICS:"
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(value: String?): SyncErrorDetails? {
            if (value.isNullOrBlank() || !value.startsWith(PREFIX)) return null
            return runCatching {
                json.decodeFromString<SyncErrorDetails>(value.removePrefix(PREFIX))
            }.getOrNull()
        }

        fun fromStoredError(value: String?): SyncErrorDetails {
            return decode(value) ?: SyncErrorDetails(reason = value ?: "Unknown error")
        }
    }
}
