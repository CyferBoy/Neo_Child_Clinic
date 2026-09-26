package com.neochildclinic.domain.model

data class InventoryTransaction(
    val transactionId: String,
    val vaccineId: String,
    val batchId: String,
    val patientId: String? = null,
    val visitId: String? = null,
    val transactionType: String,
    val quantity: Int,
    val timestamp: String,
    val user: String,
    val notes: String? = null,
    val isSynced: Boolean = false
)