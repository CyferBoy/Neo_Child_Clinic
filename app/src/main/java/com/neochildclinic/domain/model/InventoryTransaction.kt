package com.neochildclinic.domain.model

data class InventoryTransaction(
    val transactionId: String,
    val vaccineId: String,
    val batchId: String,
    val patientId: String? = null,
    val visitId: String? = null,
    val transactionType: String,
    val quantity: Int,
    val previousQuantity: Int = 0,
    val currentQuantity: Int = 0,
    val timestamp: String,
    val user: String,
    val notes: String? = null,
    val status: String = "COMPLETED",
    val failureReason: String? = null,
    val processedAt: String? = null,
    val processedBy: String? = null,
    val isSynced: Boolean = false,
    val createdBy: String? = null,
    val updatedBy: String? = null
)
