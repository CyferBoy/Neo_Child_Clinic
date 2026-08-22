package com.neochildclinic.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Read-only Room snapshot used by the patient history screen.
 * It keeps the visit, its vaccination items, and its reminders together
 * so the UI receives one consistent local snapshot instead of collecting
 * nested flows independently.
 */
data class PatientVaccinationCardEntity(
    @Embedded val visit: VisitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "vaccinationId"
    )
    val items: List<VaccinationItemEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "originalVisitId"
    )
    val reminders: List<ReminderEntity>
)
