package com.neochildclinic.domain.model

data class InventoryDeduction(
    val id: Long,
    val vaccinationId: String,
    val vaccineName: String,
    val quantity: Int,
    val status: String,
    val errorMessage: String?
)