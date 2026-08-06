package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
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
    val updatedAt: String = ""
)
