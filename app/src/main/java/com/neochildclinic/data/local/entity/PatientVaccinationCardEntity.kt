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

/**
 * Visit plus its vaccination items, with no reminders.
 *
 * This is the batch shape behind [com.neochildclinic.feature.vaccination.domain.repository.VaccinationRepository.allVaccinations],
 * which used to build one Flow per visit and combine them - N queries for N visits. Room issues a
 * fixed two queries per emission here regardless of row count, and re-runs when either
 * patient_visits or vaccination_items changes, so it stays reactive.
 */
data class VaccinationWithItemsEntity(
    @Embedded val visit: VisitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "vaccinationId"
    )
    val items: List<VaccinationItemEntity>
)

fun VaccinationWithItemsEntity.toDomain() =
    visit.toVaccination().copy(items = items.map { it.toDomain() })

fun PatientVaccinationCardEntity.toDomain() = com.neochildclinic.domain.model.PatientVaccinationCard(
    vaccination = visit.toVaccination().copy(items = items.map { it.toDomain() }),
    items = items.map { it.toDomain() },
    reminders = reminders.map { it.toDomain() }
)
