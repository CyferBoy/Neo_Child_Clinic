package com.neochildclinic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BorrowedVaccine(
    val id: String = "",
    @SerialName("doctor_name") val doctorName: String = "",
    @SerialName("vaccine_id") val vaccineId: String = "",
    @SerialName("batch_id") val batchId: String = "",
    @SerialName("borrowed_date") val borrowedDate: String = "",
    val quantity: Int = 1,
    @SerialName("is_returned") val isReturned: Boolean = false,
    @SerialName("returned_date") val returnedDate: String? = null,
    val type: String = "BY", // BY (Borrowed By us from someone), TO (Borrowed from us By someone)
    val notes: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = false
)
