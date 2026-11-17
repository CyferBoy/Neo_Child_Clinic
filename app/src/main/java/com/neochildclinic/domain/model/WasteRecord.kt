package com.neochildclinic.domain.model

/**
 * Pure Kotlin business domain model for a vaccine wastage record.
 * Decoupled from SQLite/Room annotations and persistence sync state (UNTANGLE-09).
 */
data class WasteRecord(
    val id: String,
    val vaccineId: String,
    val batchId: String,
    val brandName: String,
    val batchNumber: String,
    val expiryDate: String,
    val dateWasted: String,
    val reason: String,
    val quantity: Int,
    val updatedAt: String = "",
    val createdBy: String? = null,
    val updatedBy: String? = null
)
