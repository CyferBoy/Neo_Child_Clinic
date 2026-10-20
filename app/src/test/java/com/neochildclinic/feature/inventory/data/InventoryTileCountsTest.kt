package com.neochildclinic.feature.inventory.data

import org.junit.Assert.assertEquals
import org.junit.Test

// Dashboard tile counts (report item F2): the tiles must count stock WITHOUT building every
// InventoryItem (batch lists mapped + sorted per vaccine). The pure counting rules below
// mirror exactly what getInventoryItems derives per vaccine, including:
//  - totals only over usable batches (>0 left, not expired),
//  - a vaccine with NO batch rows at all is out of stock (it has zero doses),
//  - the low tile must not double-count out-of-stock vaccines (isLowStock && !hasOutofStock).
class InventoryTileCountsTest {

    private fun vaccines(vararg ids: String) = ids.toList()
    private fun batch(vaccineId: String, remaining: Int, expiry: String = "2099-12-31") =
        BatchStock(vaccineId, remaining, expiry)
    private val notExpired: (String) -> Boolean = { false }

    @Test
    fun `zero-batch vaccines count only as out of stock`() {
        val counts = stockTileCounts(
            vaccineIds = vaccines("v1"),
            batches = emptyList(),
            threshold = 5,
            isExpired = notExpired
        )
        // in-stock tile: total 0 is not low; out tile: 0 <= 0.
        assertEquals(0 to 1, counts)
    }

    @Test
    fun `expired batches do not inflate a vaccine's usable total`() {
        val counts = stockTileCounts(
            vaccineIds = vaccines("v1"),
            batches = listOf(batch("v1", 2), batch("v1", 3, expiry = "2020-01-01")),
            threshold = 2,
            isExpired = { it.startsWith("2020") }
        )
        // usable total is 2, not 5: v1 is low-stock at threshold 2 only if expiry is excluded.
        assertEquals(1 to 0, counts)
    }

    @Test
    fun `an all-expired vaccine is out of stock, not low`() {
        val counts = stockTileCounts(
            vaccineIds = vaccines("v1"),
            batches = listOf(batch("v1", 4, expiry = "2020-01-01")),
            threshold = 5,
            isExpired = { it.startsWith("2020") }
        )
        assertEquals(0 to 1, counts)
    }

    @Test
    fun `low tile excludes out-of-stock vaccines at the same threshold`() {
        val counts = stockTileCounts(
            vaccineIds = vaccines("low", "out"),
            batches = listOf(batch("low", 5)),
            threshold = 5,
            isExpired = notExpired
        )
        // "low" (total 5 <= 5, > 0) is low; "out" (no rows) is out of stock only.
        assertEquals(1 to 1, counts)
    }

    @Test
    fun `batch rows pointing at unknown vaccines are ignored`() {
        val counts = stockTileCounts(
            vaccineIds = vaccines("v1"),
            batches = listOf(batch("v1", 2), batch("orphan", 50)),
            threshold = 1,
            isExpired = notExpired
        )
        // v1 has 2 doses (2 > threshold 1, 2 > 0): neither tile counts it, and the orphan
        // batch invents no tile of its own - like the per-vaccine original loop.
        assertEquals(0 to 0, counts)
    }
}
