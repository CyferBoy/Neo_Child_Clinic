package com.neochildclinic.core.sync

import com.neochildclinic.data.local.entity.VaccineBatchEntity
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchUpsertTest {

    private fun batch() = VaccineBatchEntity(
        batchId = "b1",
        vaccineId = "v1",
        batchNumber = "B1",
        manufacturer = "M",
        purchaseDate = "2026-01-01",
        expiryDate = "2027-01-01",
        purchaseQuantity = 10,
        remainingQuantity = 7,
        supplier = "S",
        purchaseCost = 100.0,
        sellingPrice = 120.0
    )

    @Test
    fun createIsInsertOnlySoARetryCannotOverwriteStock() {
        val upsert = batchUpsert(batch(), isCreate = true)

        assertTrue("CREATE must be ON CONFLICT DO NOTHING", upsert.insertOnly)
        assertEquals(0, upsert.fields["remaining_quantity"]?.jsonPrimitive?.int)
    }

    @Test
    fun updateIsAPlainUpsertAndLeavesStockToTheTrigger() {
        val upsert = batchUpsert(batch(), isCreate = false)

        assertFalse(upsert.insertOnly)
        assertNull("UPDATE must not send remaining_quantity", upsert.fields["remaining_quantity"])
    }
}
