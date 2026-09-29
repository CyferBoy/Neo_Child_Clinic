package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Vaccine(
    val id: String = "",
    val type: String = "",
    val brandName: String = "",
    val companyName: String = "",
    val manufacturer: String? = null,
    val category: String? = null,
    val doseSchedule: String? = null,
    val storageDetails: String? = null,
    val stock: Int = 0,
    val mrp: Double = 0.0,
    val netRate: Double = 0.0,
    val lastUpdated: String = "",
    val isLowStock: Boolean = false,
    val createdBy: String? = null,
    val updatedBy: String? = null
)
