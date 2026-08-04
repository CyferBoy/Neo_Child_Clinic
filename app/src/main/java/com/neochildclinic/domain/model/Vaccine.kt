package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Vaccine(
    val id: String = "",
    val type: String = "",
    val brandName: String = "",
    val companyName: String = "",
    val stock: Int = 0,
    val batchNumber: String = "",
    val expiryDate: String = "",
    val mrp: Double = 0.0,
    val netRate: Double = 0.0,
    val isLowStock: Boolean = false
)
