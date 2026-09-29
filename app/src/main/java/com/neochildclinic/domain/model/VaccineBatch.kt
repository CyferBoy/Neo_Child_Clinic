package com.neochildclinic.domain.model

data class VaccineBatch(
    val batchId: String,
    val vaccineId: String,
    val batchNumber: String,
    val manufacturer: String = "",
    val purchaseDate: String,
    val expiryDate: String,
    val purchaseQuantity: Int = 0,
    val remainingQuantity: Int,
    val reservedQuantity: Int = 0,
    val usedQuantity: Int = 0,
    val wastedQuantity: Int = 0,
    val borrowedQuantity: Int = 0,
    val supplier: String = "",
    val purchaseCost: Double,
    val sellingPrice: Double,
    val status: String = "ACTIVE",
    val updatedAt: String = "",
    val createdBy: String? = null,
    val updatedBy: String? = null
)
