package com.neochildclinic.feature.inventory.data

/**
 * The only batch fields the dashboard tiles need. Keeping the projection this narrow is
 * what lets the tile counts run without building, domain-mapping and sorting every
 * vaccine's full batch list on each invalidation (report item F2).
 */
internal data class BatchStock(val vaccineId: String, val remaining: Int, val expiryDate: String)

/**
 * (low, outOfStock) dashboard tile counts, derived EXACTLY the way
 * [InventoryRepositoryImpl.getInventoryItems] derives isLowStock/hasOutofStock per vaccine:
 * totals only over usable batches (remaining > 0, not expired), then
 * low   = 0 <  total <= threshold,
 * out   = total <= 0.
 * A vaccine with no batch rows at all has total 0, so it lands only in the out tile; batch
 * rows pointing at unknown vaccines are ignored, like the per-vaccine loop of the original.
 */
internal fun stockTileCounts(
    vaccineIds: List<String>,
    batches: List<BatchStock>,
    threshold: Int,
    isExpired: (String) -> Boolean
): Pair<Int, Int> {
    val totals = HashMap<String, Int>()
    for (b in batches) {
        if (b.remaining <= 0 || isExpired(b.expiryDate)) continue
        totals[b.vaccineId] = (totals[b.vaccineId] ?: 0) + b.remaining
    }
    var low = 0
    var out = 0
    for (id in vaccineIds) {
        val total = totals[id] ?: 0
        if (total <= 0) out++ else if (total <= threshold) low++
    }
    return low to out
}
