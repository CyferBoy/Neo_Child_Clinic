package com.neochildclinic.domain.repository

import com.neochildclinic.domain.model.PatientDocument

interface DocumentRepository {
    suspend fun uploadDocument(patientId: String, fileName: String, bytes: ByteArray): String
    suspend fun listDocuments(patientId: String): List<PatientDocument>
    suspend fun getDownloadUrl(path: String): String
    suspend fun deleteDocument(path: String)
}