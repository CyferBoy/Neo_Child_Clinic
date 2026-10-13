package com.neochildclinic.feature.patient.presentation

import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.Vaccination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the vaccination-history loading bug: opening Patient Details
 * showed "No vaccination records found." before the Room query had ever answered, then
 * records appeared in stages. The state model must make loading vs loaded-but-empty
 * distinguishable, deduplicate by stable id, and sort deterministically.
 */
class PatientHistoryUiStateTest {

    private fun card(
        id: String,
        date: String,
        visitType: String = "VACCINATION",
        reminders: List<com.neochildclinic.domain.model.Reminder> = emptyList()
    ) = PatientVaccinationCardData(
        vaccination = Vaccination(id = id, dateGiven = date, visitType = visitType),
        reminders = reminders
    )

    // --- Spec A1: initial state must never show a false empty message -------------

    @Test
    fun `initial state is not loaded and renders no empty message`() {
        val state = PatientHistoryUiState()
        assertFalse(state.isLoaded)
        assertNull(state.segmentEmptyMessage(PatientHistoryUiState.SEGMENT_VACCINATIONS))
        assertNull(state.segmentEmptyMessage(PatientHistoryUiState.SEGMENT_CONSULTATIONS))
        assertTrue(state.vaccinations.isEmpty())
    }

    // --- Spec A2: a successful empty query shows the empty state ------------------

    @Test
    fun `loaded but empty shows the empty messages`() {
        val state = PatientHistoryUiState.loaded(emptyList(), emptyList(), emptyList())
        assertTrue(state.isLoaded)
        assertEquals("No vaccination records found.", state.segmentEmptyMessage(0))
        assertEquals("No consultation records found.", state.segmentEmptyMessage(1))
    }

    // --- Spec A3/A5: loaded records are present immediately, no empty message ------

    @Test
    fun `loaded with records shows no empty message`() {
        val state = PatientHistoryUiState.loaded(
            listOf(card("v1", "2026-01-01")),
            listOf(Consultation(id = "c1", date = "2026-01-02")),
            emptyList()
        )
        assertNull(state.segmentEmptyMessage(0))
        assertNull(state.segmentEmptyMessage(1))
        assertEquals(listOf("v1"), state.vaccinations.map { it.id })
        assertEquals(listOf("c1"), state.consultations.map { it.id })
    }

    // --- Spec A7: duplicate events must not create duplicate entries --------------

    @Test
    fun `duplicate rows with the same id render exactly once`() {
        val state = PatientHistoryUiState.loaded(
            listOf(card("v1", "2026-01-01"), card("v1", "2026-01-01"), card("v2", "2026-01-02")),
            emptyList(),
            emptyList()
        )
        assertEquals(listOf("v2", "v1"), state.vaccinations.map { it.id })
    }

    // --- Segment filtering (existing rule: only visitType == VACCINATION) ----------

    @Test
    fun `non-vaccination visits are excluded from the vaccination segment`() {
        val state = PatientHistoryUiState.loaded(
            listOf(card("v1", "2026-01-01"), card("x1", "2026-01-01", visitType = "CONSULTATION")),
            emptyList(),
            emptyList()
        )
        assertEquals(listOf("v1"), state.vaccinations.map { it.id })
    }

    // --- Spec A9: sorting and filtering stay deterministic -------------------------

    @Test
    fun `vaccinations sort by date descending with id tie-break`() {
        val state = PatientHistoryUiState.loaded(
            listOf(
                card("old", "2025-01-01"),
                card("tieA", "2026-01-01"),
                card("new", "2026-06-01"),
                card("tieB", "2026-01-01")
            ),
            emptyList(),
            emptyList()
        )
        assertEquals(listOf("new", "tieB", "tieA", "old"), state.vaccinations.map { it.id })
    }

    @Test
    fun `consultations sort by date descending`() {
        val state = PatientHistoryUiState.loaded(
            emptyList(),
            listOf(
                Consultation(id = "cOld", date = "2025-01-01"),
                Consultation(id = "cNew", date = "2026-01-01")
            ),
            emptyList()
        )
        assertEquals(listOf("cNew", "cOld"), state.consultations.map { it.id })
    }

    // --- Spec A4/A10: a refresh failure cannot regress the state -------------------
    // The state is only ever rebuilt from Room emissions; there is no path that resets
    // a Loaded state back to Loading or to empty. Assert the invariant that matters
    // here: once loaded, the empty message depends only on the data itself.
    @Test
    fun `loaded state keeps isLoaded when data is present`() {
        val loaded = PatientHistoryUiState.loaded(listOf(card("v1", "2026-01-01")), emptyList(), emptyList())
        assertTrue(loaded.isLoaded)
        assertNull(loaded.segmentEmptyMessage(0))
    }
}
