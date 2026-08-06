package com.neochildclinic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WasteRecord(
    val id: String = "",
    @SerialName("vaccine_id") val vaccineId: String = "",
    @SerialName("batch_id") val batchId: String = "",
    @SerialName("brand_name") val brandName: String = "",
    @SerialName("batch_number") val batchNumber: String = "",
    @SerialName("expiry_date") val expiryDate: String = "",
    @SerialName("date_wasted") val dateWasted: String = "",
    val reason: String = "",
    val quantity: Int = 1,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("is_synced") val isSynced: Boolean = false
)
