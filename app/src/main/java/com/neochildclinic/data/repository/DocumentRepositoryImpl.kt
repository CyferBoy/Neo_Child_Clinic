package com.neochildclinic.data.repository

import com.neochildclinic.domain.model.PatientDocument
import com.neochildclinic.domain.repository.DocumentRepository
import io.github.jan.supabase.storage.FileObject
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.upload
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val storage: Storage
) : DocumentRepository {

    private val bucket = storage.from("patient-docs")

    override suspend fun uploadDocument(patientId: String, fileName: String, bytes: ByteArray): String {
        val path = "$patientId/$fileName"
        bucket.upload(path, bytes) {
            upsert = true
        }
        return path
    }

    override suspend fun listDocuments(patientId: String): List<PatientDocument> {
        return bucket.list(patientId).map { it.toPatientDocument() }
    }

    override suspend fun getDownloadUrl(path: String): String {
        return bucket.createSignedUrl(path, expiresIn = 60.minutes)
    }

    override suspend fun deleteDocument(path: String) {
        bucket.delete(path)
    }
}

private fun FileObject.toPatientDocument(): PatientDocument = PatientDocument(
    name = name,
    sizeKb = (metadata?.get("size")?.toString()?.toLongOrNull() ?: 0L) / 1024
)