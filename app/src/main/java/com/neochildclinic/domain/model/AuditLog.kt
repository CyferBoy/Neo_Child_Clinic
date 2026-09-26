package com.neochildclinic.domain.model

data class AuditLog(
    val id: String,
    val timestamp: String,
    val user: String,
    val module: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val remarks: String? = null,
    val device: String? = null
)