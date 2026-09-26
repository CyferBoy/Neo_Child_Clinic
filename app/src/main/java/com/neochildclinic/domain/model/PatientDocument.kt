package com.neochildclinic.domain.model

/** Read-only projection of a patient document on cloud storage, decoupling UI from the SDK. */
data class PatientDocument(
    val name: String,
    val sizeKb: Long
)