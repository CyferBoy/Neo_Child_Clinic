package com.neochildclinic.domain.repository

import io.github.jan.supabase.storage.FileObject

interface DocumentRepository {
    suspend fun uploadDocument(patientId: String, fileName: String, bytes: ByteArray): String
    suspend fun listDocuments(patientId: String): List<FileObject>
    suspend fun getDownloadUrl(path: String): String
    suspend fun deleteDocument(path: String)
}
