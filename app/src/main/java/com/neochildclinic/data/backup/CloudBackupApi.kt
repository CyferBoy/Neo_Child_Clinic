package com.neochildclinic.data.backup

import com.neochildclinic.BuildConfig
import com.neochildclinic.domain.model.CloudBackupMetadata
import io.github.jan.supabase.auth.Auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to the Cloudflare Worker described in cloudflare/backup-worker/. Every request
 * carries the caller's Supabase access token as a Bearer token; the Worker verifies it and
 * derives the account/clinic id from the token itself (see docs/BACKUP_RESTORE.md
 * "Authentication & Security") - the Android app never sends an account id the server has
 * to trust blindly, and never talks to R2 directly.
 */
@Singleton
class CloudBackupApi @Inject constructor(private val auth: Auth) {

    companion object {
        // Must match MAX_BACKUP_BYTES in cloudflare/backup-worker/src/index.ts.
        const val MAX_BACKUP_BYTES = 200L * 1024 * 1024
    }

    @Serializable
    private data class ConfirmBody(val sizeBytes: Long, val appVersion: String, val backupVersion: Int, val createdAt: String)

    @Serializable
    private data class RetentionBody(val keep: Int)

    @Serializable
    private data class BackupMetadataDto(
        val backupId: String,
        val createdAt: String,
        val sizeBytes: Long,
        val appVersion: String,
        val backupVersion: Int,
        val status: String
    )

    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
            }
        }
    }

    private val baseUrl: String get() = BuildConfig.BACKUP_WORKER_URL.trimEnd('/')

    fun isConfigured(): Boolean = baseUrl.isNotBlank()

    private suspend fun currentToken(): String? = auth.currentSessionOrNull()?.accessToken

    private suspend fun authenticatedToken(): String {
        currentToken()?.let { return it }
        return refreshToken()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private suspend fun refreshToken(): String {
        return try {
            auth.refreshCurrentSession()
            currentToken() ?: throw BackupException.AuthExpired()
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException.AuthExpired(e)
        }
    }

    /** Runs [call] with a bearer token; on a 401/403 it refreshes the Supabase session once
     * and retries exactly once, rather than guessing at the session's expiry-field shape. */
    private suspend fun withAuthRetry(call: suspend (token: String) -> HttpResponse): HttpResponse {
        val token = authenticatedToken()
        val response = call(token)
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            val refreshed = refreshToken()
            return call(refreshed)
        }
        return response
    }

    private suspend fun <T> guarded(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: BackupException) {
            throw e
        } catch (e: UnknownHostException) {
            throw BackupException.NoInternet()
        } catch (e: java.net.SocketTimeoutException) {
            throw BackupException.Timeout(e)
        } catch (e: IOException) {
            throw BackupException.UploadFailed(e)
        }
    }

    private fun checkStatus(response: HttpResponse) {
        when (response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw BackupException.Unauthorized()
            HttpStatusCode.InsufficientStorage -> throw BackupException.InsufficientStorage()
            HttpStatusCode.PayloadTooLarge -> throw BackupException.TooLarge(-1L, MAX_BACKUP_BYTES)
            else -> if (response.status.value !in 200..299) throw BackupException.ServerError(response.status.value)
        }
    }

    suspend fun upload(backupId: String, encryptedBytes: ByteArray, appVersion: String, backupVersion: Int, createdAt: String): CloudBackupMetadata = guarded {
        // Fail fast with a specific message rather than waiting on a round trip the
        // server-side MAX_BACKUP_BYTES check (cloudflare/backup-worker/src/index.ts) would
        // reject anyway.
        if (encryptedBytes.size > MAX_BACKUP_BYTES) throw BackupException.TooLarge(encryptedBytes.size.toLong(), MAX_BACKUP_BYTES)

        val putResponse = withAuthRetry { token ->
            client.put("$baseUrl/backups/$backupId") {
                bearer(token)
                contentType(ContentType.Application.OctetStream)
                setBody(encryptedBytes)
            }
        }
        checkStatus(putResponse)

        val confirmResponse = withAuthRetry { token ->
            client.post("$baseUrl/backups/$backupId/confirm") {
                bearer(token)
                contentType(ContentType.Application.Json)
                setBody(ConfirmBody(encryptedBytes.size.toLong(), appVersion, backupVersion, createdAt))
            }
        }
        checkStatus(confirmResponse)
        val dto = confirmResponse.body<BackupMetadataDto>()
        dto.toDomain()
    }

    suspend fun applyRetention(keep: Int) = guarded {
        val response = withAuthRetry { token ->
            client.post("$baseUrl/backups/retention") {
                bearer(token)
                contentType(ContentType.Application.Json)
                setBody(RetentionBody(keep))
            }
        }
        checkStatus(response)
    }

    suspend fun list(): List<CloudBackupMetadata> = guarded {
        val response = withAuthRetry { token ->
            client.get("$baseUrl/backups") {
                bearer(token)
            }
        }
        checkStatus(response)
        response.body<List<BackupMetadataDto>>().map { it.toDomain() }
    }

    suspend fun download(backupId: String): ByteArray = guarded {
        val response = withAuthRetry { token ->
            client.get("$baseUrl/backups/$backupId/download") {
                bearer(token)
            }
        }
        if (response.status == HttpStatusCode.NotFound) throw BackupException.Corrupted("Backup not found on server")
        checkStatus(response)
        response.bodyAsBytes()
    }

    suspend fun delete(backupId: String) = guarded {
        val response = withAuthRetry { token ->
            client.delete("$baseUrl/backups/$backupId") {
                bearer(token)
            }
        }
        checkStatus(response)
    }

    private fun BackupMetadataDto.toDomain() = CloudBackupMetadata(
        backupId = backupId, createdAt = createdAt, sizeBytes = sizeBytes,
        appVersion = appVersion, backupVersion = backupVersion, status = status
    )
}
