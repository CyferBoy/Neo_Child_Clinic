package com.neochildclinic.domain.model

data class Reminder(
    val id: String,
    val patientId: String,
    val vaccineName: String,
    val dueDate: String,
    val status: String,
    val category: String = "VACCINATION",
    val reminderEnabled: Boolean = true,
    val type: String = "",
    val nxtVaccineId: List<String>? = null
)