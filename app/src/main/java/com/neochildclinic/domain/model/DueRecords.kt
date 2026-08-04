package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CompletedDueRecord(
    val id: String = "",
    val patientId: String = "",
    val originalDueDate: String = "",
    val completedDate: String = "",
    val completedTime: String = "",
    val completedBy: String = "",
    val linkedVaccinationId: String = "",
    val remarks: String = ""
)

@Serializable
data class DismissedDueRecord(
    val id: String = "",
    val patientId: String = "",
    val originalDueDate: String = "",
    val dismissedDate: String = "",
    val dismissedTime: String = "",
    val dismissedBy: String = "",
    val dismissReason: String = "",
    val remarks: String = ""
)

@Serializable
data class OtherEstablishmentDueRecord(
    val id: String = "",
    val patientId: String = "",
    val originalDueDate: String = "",
    val vaccinatedDate: String = "",
    val hospitalName: String = "",
    val recordedBy: String = "",
    val recordedDate: String = "",
    val recordedTime: String = "",
    val proof: String = "",
    val remarks: String = ""
)
