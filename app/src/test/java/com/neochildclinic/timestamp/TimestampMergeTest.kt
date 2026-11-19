package com.neochildclinic.timestamp

import com.neochildclinic.core.common.DateUtils
import com.neochildclinic.data.backup.deductionMergeKey
import com.neochildclinic.data.backup.isNewerForLww
import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampMergeTest {

    // "…:30Z" vs "…:30.123Z" differ by 123 ms, but 'Z' (0x5A) sorts after '.' (0x2E),
    // so the old lexicographic compare called the OLDER whole-second row newer.
    @Test
    fun mixedPrecisionIso_newerFractionalRowWinsAndUnknownLoses() {
        val whole = "2026-08-22T03:48:30Z"
        val fractional = "2026-08-22T03:48:30.123Z"

        assertTrue("sanity: string compare gets this backwards", whole > fractional)
        assertEquals(123L, DateUtils.isoToLong(fractional) - DateUtils.isoToLong(whole))
        assertTrue(isNewerForLww(fractional, whole))
        assertFalse(isNewerForLww(whole, fractional))

        // 0/unparseable is "unknown": neither a valid baseline nor a winning challenger.
        assertFalse(isNewerForLww("garbage", fractional))
        assertFalse(isNewerForLww(null, fractional))
        assertFalse(isNewerForLww(fractional, "garbage"))
        assertFalse(isNewerForLww(fractional, null))
    }

    @Test
    fun identicalResolvedAtDeductions_stayDistinctByRestoredId() {
        fun deduction(id: Long) = InventoryDeductionEntity(
            id = id,
            vaccinationId = "visit-1",
            vaccineId = "vax-1",
            vaccineName = "Vax",
            batchId = "B1",
            quantity = 1,
            status = "COMPLETED",
            errorMessage = null,
            resolvedAt = 1_700_000_000_000L
        )
        val a = deduction(10)
        val b = deduction(11)

        val legacyKey = { d: InventoryDeductionEntity ->
            "${d.vaccinationId}:${d.vaccineId}:${d.batchId}:${d.resolvedAt}"
        }
        assertEquals("old natural key collides - the CL-09 bug", legacyKey(a), legacyKey(b))
        assertNotEquals(
            "restored primary key keeps two rows from one visit distinct",
            deductionMergeKey(a),
            deductionMergeKey(b)
        )
    }
}
