package com.neochildclinic.data.local.entity

import com.neochildclinic.domain.model.Vaccination
import org.junit.Assert.assertEquals
import org.junit.Test

class VaccinationEntityMappingTest {

    @Test
    fun `editing a visit preserves the original createdAt timestamp`() {
        val visit = Vaccination(
            id = "v1",
            patientId = "p1",
            dateGiven = "2026-05-09",
            updatedAt = "2026-06-01T10:30:00+05:30"
        )

        val entity = visit.toEntity(
            isSynced = false,
            createdAtOverride = "2026-05-09T09:00:00+05:30"
        )

        assertEquals("2026-05-09T09:00:00+05:30", entity.createdAt)
        assertEquals("2026-06-01T10:30:00+05:30", entity.updatedAt)
    }
}
