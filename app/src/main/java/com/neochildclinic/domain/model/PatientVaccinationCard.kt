package com.neochildclinic.domain.model

data class PatientVaccinationCard(
    val vaccination: Vaccination,
    val items: List<VaccinationItem> = emptyList(),
    val reminders: List<Reminder> = emptyList()
)
