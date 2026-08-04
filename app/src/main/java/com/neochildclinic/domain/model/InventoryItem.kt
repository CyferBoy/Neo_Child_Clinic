package com.neochildclinic.domain.model

import com.neochildclinic.data.local.entity.VaccineBatchEntity

data class InventoryItem(
    val id: String,
    val brandName: String,
    val stock: Int,
    val type: String,
    val company: String,
    val mrp: Double = 0.0,
    val netRate: Double = 0.0,
    val batches: List<VaccineBatchEntity> = emptyList(),
    val isLowStock: Boolean = false,
    val isNearExpiry: Boolean = false,
    val hasExpired: Boolean = false,
    val hasOutofStock: Boolean = false,
    val activeBatchesCount: Int = 0
)
