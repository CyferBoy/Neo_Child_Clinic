package com.neochildclinic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BorrowedVaccine(
    val id: String = "",
    val doctorName: String = "",
    val borrowedDate: String = "",
    val vaccineName: String = "",
    val expiryDate: String = "",
    val batchNumber: String = "",
    val isReturned: Boolean = false,
    val returnedDate: String? = null,
    val type: String = "BY" // "BY" or "FROM"
)
