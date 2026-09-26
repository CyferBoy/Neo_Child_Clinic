package com.neochildclinic.domain.model

data class BackupHistory(
    val id: String,
    val type: String,
    val location: String,
    val createdAt: String,
    val sizeBytes: Long = 0,
    val status: String
)