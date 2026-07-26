package com.clinic.neochild.domain.model

data class WasteRecord(
    val id: String = "",
    val vaccineId: String = "",
    val batchId: String = "",
    val brandName: String = "",
    val batchNumber: String = "",
    val expiryDate: String = "",
    val dateWasted: String = "",
    val reason: String = "",
    val quantity: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)
