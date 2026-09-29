package com.neochildclinic.domain.model

data class InventoryDeduction(
    val id: Long = 0L,
    val vaccinationId: String,
    val vaccineId: String = "",
    val vaccineName: String,
    val batchId: String? = null,
    val quantity: Int,
    val status: String,
    val errorMessage: String?,
    val resolvedAt: Long = 0,
    val createdBy: String? = null,
    val updatedBy: String? = null
)