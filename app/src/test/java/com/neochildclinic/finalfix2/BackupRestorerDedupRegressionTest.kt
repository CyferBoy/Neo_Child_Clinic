package com.neochildclinic.finalfix2

import com.neochildclinic.data.backup.deductionMergeKey
import com.neochildclinic.data.backup.deductionNaturalKey
import com.neochildclinic.data.backup.deductionsToApply
import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * CL-09/CL-10 regression: the deduction ledger must survive both a same-visit collision
 * (two legitimate rows with an identical resolvedAt) and a cross-device merge (the same
 * dose backed up by another device with different local auto-increment ids).
 */
class BackupRestorerDedupRegressionTest {

    private fun deduction(id: Long, resolvedAt: Long = 1_700_000_000_000L) = InventoryDeductionEntity(
        id = id,
        vaccinationId = "visit-1",
        vaccineId = "vax-1",
        vaccineName = "Vax",
        batchId = "B1",
        quantity = 1,
        status = "COMPLETED",
        errorMessage = null,
        resolvedAt = resolvedAt
    )

    @Test
    fun sameDoseFromAnotherDevice_isNotAppendedAgain() {
        // Device B already recorded the dose with local id 5; device A's backup holds the
        // very same dose with local id 10. Old id-based key treated them as different rows.
        val deviceB = listOf(deduction(5))
        val deviceABackup = listOf(deduction(10))

        assertEquals(deductionNaturalKey(deviceB.single()), deductionNaturalKey(deviceABackup.single()))
        assertNotEquals(deductionMergeKey(deviceB.single()), deductionMergeKey(deviceABackup.single()))
        assertEquals(0, deductionsToApply(deviceB, deviceABackup).size)
    }

    @Test
    fun twoRowsWithIdenticalResolvedAtInOneVisit_bothSurvive() {
        // Same natural key, different restored ids: the tie-breaker must keep both rows.
        val oneVisit = listOf(deduction(10), deduction(11))

        assertEquals(2, deductionsToApply(emptyList(), oneVisit).size)
        assertEquals(2, oneVisit.map { deductionMergeKey(it) }.toSet().size)
    }

    @Test
    fun twoDevicesEachWithTwoSameKeyRows_stillDedupeToOneVisitPair() {
        // The combined regression: collapsed pairing loses a dose, id-only pairing doubles it.
        val deviceB = listOf(deduction(20), deduction(21))
        val deviceABackup = listOf(deduction(10), deduction(11))

        assertEquals(0, deductionsToApply(deviceB, deviceABackup).size)
        assertEquals(2, deductionsToApply(emptyList(), deviceABackup).size)
    }
}
