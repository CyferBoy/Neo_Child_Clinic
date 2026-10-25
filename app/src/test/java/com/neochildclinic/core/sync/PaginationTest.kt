package com.neochildclinic.core.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PaginationTest {

    @Test
    fun readsEveryPageUntilEmpty() = runBlocking {
        val rows = (1..2500).map { "row-$it" }
        val calls = mutableListOf<Pair<Long, Long>>()
        val result = fetchAllPages(pageSize = 1000) { from, to ->
            calls += from to to
            rows.drop(from.toInt()).take((to - from + 1).toInt())
        }
        assertEquals(rows, result)
        assertEquals(
            listOf(0L to 999L, 1000L to 1999L, 2000L to 2999L, 2500L to 3499L),
            calls
        )
    }

    @Test
    fun advancesByActualPageSizeWhenServerCapIsLower() = runBlocking {
        val rows = (1..1200).map { "row-$it" }
        val result = fetchAllPages(pageSize = 1000) { from, to ->
            rows.drop(from.toInt()).take(minOf(500, (to - from + 1).toInt()))
        }
        assertEquals(rows, result)
    }

    @Test
    fun emptyTableReturnsEmpty() = runBlocking {
        var calls = 0
        val result = fetchAllPages<String>(pageSize = 1000) { _, _ ->
            calls++
            emptyList()
        }
        assertEquals(emptyList<String>(), result)
        assertEquals(1, calls)
    }
}
