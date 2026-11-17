package com.neochildclinic.domain.model

data class AuditLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: String,
    val user: String,
    val module: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val remarks: String? = null,
    val device: String? = null,
    val patientId: String? = null
)