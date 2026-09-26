package com.neochildclinic.domain.model

data class PatientNote(
    val id: String,
    val patientId: String,
    val content: String,
    val author: String,
    val timestamp: String
)