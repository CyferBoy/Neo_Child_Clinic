package com.neochildclinic.domain.model

data class VaccineBatch(
    val batchId: String,
    val vaccineId: String,
    val batchNumber: String,
    val purchaseDate: String,
    val expiryDate: String,
    val remainingQuantity: Int,
    val purchaseCost: Double,
    val sellingPrice: Double
)