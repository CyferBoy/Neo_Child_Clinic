package com.neochildclinic.domain.model

/** visitType: "NEW" for first consultation, "FOLLOW_UP" for return visits, empty = unset (legacy). */
data class ConsultationTodo(
    val id: String,
    val patientId: String? = null,
    val name: String,
    val mobile: String,
    val address: String,
    val todoDate: String,
    val status: String = "PENDING",
    val doctorId: String? = null,
    val doctorName: String? = null,
    val availabilitySlotId: String? = null,
    val visitType: String = ""
)

data class VaccinationTodo(
    val id: String,
    val patientId: String? = null,
    val name: String,
    val mobile: String,
    val vaccineNames: String,
    val address: String,
    val todoDate: String,
    val status: String = "PENDING",
    val doctorId: String? = null,
    val doctorName: String? = null,
    val availabilitySlotId: String? = null
)