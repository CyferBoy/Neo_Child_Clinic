package com.neochildclinic.feature.patient.presentation

import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.PatientNote
import com.neochildclinic.domain.model.Vaccination

/**
 * Explicit loading/data state for the three lists on Patient Details.
 *
 * The screen used to map an unresolved Room flow (`null`) straight to `orEmpty()`, so
 * the UI showed "No vaccination records found." during the window before the first
 * emission - a false empty state. [isLoaded] stays false until Room has actually
 * answered once, and an empty message is only ever derived from a *successful* query.
 *
 * The state is built purely from Room Flows (no network), so a background refresh never
 * resets it: it only changes when Room itself changes.
 */
data class PatientHistoryUiState(
    val isLoaded: Boolean = false,
    val vaccinationCards: List<PatientVaccinationCardData> = emptyList(),
    val consultations: List<Consultation> = emptyList(),
    val notes: List<PatientNote> = emptyList()
) {
    val vaccinations: List<Vaccination> get() = vaccinationCards.map { it.vaccination }

    /**
     * Empty-state message for a history segment, or null while still loading or when
     * the segment has data. This is the single decision point the UI must use - it is
     * what makes "loading" and "loaded but empty" distinguishable.
     */
    fun segmentEmptyMessage(segment: Int): String? = when {
        !isLoaded -> null
        segment == SEGMENT_VACCINATIONS && vaccinationCards.isEmpty() -> "No vaccination records found."
        segment == SEGMENT_CONSULTATIONS && consultations.isEmpty() -> "No consultation records found."
        else -> null
    }

    companion object {
        const val SEGMENT_VACCINATIONS = 0
        const val SEGMENT_CONSULTATIONS = 1

        /**
         * Builds the loaded state from raw Room emissions: vaccination segment only
         * (visitType == VACCINATION), one entry per visit id (overlapping invalidations
         * or duplicate emissions must never render the same record twice), and a
         * deterministic order (date desc, id desc as the tie-break).
         */
        fun loaded(
            cards: List<PatientVaccinationCardData>,
            consultations: List<Consultation>,
            notes: List<PatientNote>
        ): PatientHistoryUiState = PatientHistoryUiState(
            isLoaded = true,
            vaccinationCards = cards
                .filter { it.vaccination.visitType == "VACCINATION" }
                .distinctBy { it.vaccination.id }
                .sortedWith(
                    compareByDescending<PatientVaccinationCardData> {
                        PatientUtils.parseDate(it.vaccination.dateGiven)?.time ?: 0L
                    }.thenByDescending { it.vaccination.id }
                ),
            consultations = consultations.sortedByDescending {
                PatientUtils.parseDate(it.date)?.time ?: 0L
            },
            notes = notes
        )
    }
}
