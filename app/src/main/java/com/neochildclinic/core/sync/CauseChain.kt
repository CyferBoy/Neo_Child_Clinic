package com.neochildclinic.core.sync

/**
 * The error and its cause chain, nearest first.
 *
 * Replaces the duplicated `var current = error; repeat(8) { ... current = it.cause }` walks.
 * Stops at a null cause, on a cycle (visited-set guard - Throwable uses identity
 * equals/hashCode), or at 8 entries - the same bound the old walks used, kept so callers'
 * decisions are byte-for-byte unchanged.
 */
internal fun walkCauses(error: Throwable): List<Throwable> {
    val chain = ArrayList<Throwable>(8)
    val seen = HashSet<Throwable>()
    var current: Throwable? = error
    while (current != null && chain.size < 8 && seen.add(current)) {
        chain.add(current)
        current = current.cause
    }
    return chain
}
