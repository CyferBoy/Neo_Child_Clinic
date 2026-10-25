package com.neochildclinic.core.sync

/**
 * Reads every page of a PostgREST range query.
 *
 * Supabase caps a response at `max_rows` (1000 in this repo), so a plain `select` silently
 * returns only the first page. The loop advances by the number of rows actually returned,
 * which also works when the server cap is lower than [pageSize]. It stops on an empty page,
 * not on a short page: a short page may be the server cap rather than the end.
 *
 * Callers must order by a unique column (for example `id`); PostgREST does not guarantee
 * a stable order across range requests otherwise, and rows can repeat or go missing.
 */
internal suspend fun <T> fetchAllPages(
    pageSize: Int = 1000,
    fetchPage: suspend (from: Long, to: Long) -> List<T>
): List<T> {
    require(pageSize > 0)
    val all = mutableListOf<T>()
    var offset = 0L
    while (true) {
        val page = fetchPage(offset, offset + pageSize - 1)
        if (page.isEmpty()) break
        all += page
        offset += page.size
    }
    return all
}
