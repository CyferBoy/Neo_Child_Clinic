package com.neochildclinic.feature.patient.data

import com.neochildclinic.domain.model.PatientDocument
import com.neochildclinic.feature.patient.domain.repository.DocumentRepository
import io.github.jan.supabase.storage.FileObject
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.upload
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val storage: Storage
) : DocumentRepository {

    private val bucket = storage.from("patient-docs")

    override suspend fun uploadDocument(patientId: String, fileName: String, bytes: ByteArray): String {
        val safeFileName = java.io.File(fileName).name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safePatientId = patientId.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        // A per-upload key keeps a second file with the same display name from replacing the
        // first one. The original name is kept in object metadata for the UI.
        val path = "$safePatientId/${UUID.randomUUID()}-$safeFileName"
        bucket.upload(path, bytes) {
            upsert = false
            userMetadata = buildJsonObject { put("originalName", safeFileName) }
        }
        return path
    }

    override suspend fun listDocuments(patientId: String): List<PatientDocument> {
        val safePatientId = patientId.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return bucket.list(safePatientId).map { it.toPatientDocument(safePatientId) }
    }

    override suspend fun getDownloadUrl(path: String): String {
        return bucket.createSignedUrl(path, expiresIn = 60.minutes)
    }

    override suspend fun deleteDocument(path: String) {
        bucket.delete(path)
    }
}

private fun FileObject.toPatientDocument(patientId: String): PatientDocument = PatientDocument(
    name = metadata?.get("originalName")?.jsonPrimitive?.contentOrNull ?: name,
    sizeKb = (metadata?.get("size")?.toString()?.toLongOrNull() ?: 0L) / 1024,
    path = "$patientId/$name"
)