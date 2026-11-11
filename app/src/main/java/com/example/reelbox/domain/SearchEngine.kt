package com.example.reelbox.domain

import com.example.reelbox.data.catalog.Catalog
import com.example.reelbox.data.catalog.Title

object SearchEngine {
    const val MAX_RECENT = 5
    private const val TOP_SEARCHES = 6

    /**
     * Instant search over title, genre and cast (case-insensitive, substring).
     * A query that names a genre exactly returns only that genre's titles.
     * Ranking: title prefix, word prefix, title contains, genre, cast; ties by trending rank.
     */
    fun search(catalog: Catalog, query: String, visible: (Title) -> Boolean = { true }): List<Title> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val titles = catalog.titles.filter(visible)
        val genre = catalog.genres.firstOrNull { it.lowercase() == q }
        if (genre != null) return titles.filter { genre in it.genres }.sortedBy { it.trendingRank }
        return titles.mapNotNull { t ->
            val name = t.title.lowercase()
            val rank = when {
                name.startsWith(q) -> 0
                name.split(' ', ':').any { it.startsWith(q) } -> 1
                q in name -> 2
                t.genres.any { q in it.lowercase() } -> 3
                t.cast.any { q in it.lowercase() } -> 4
                else -> return@mapNotNull null
            }
            t to rank
        }.sortedWith(compareBy<Pair<Title, Int>> { it.second }.thenBy { it.first.trendingRank }).map { it.first }
    }

    /** "Top searches" shown for an empty query: the most popular titles. */
    fun topSearches(catalog: Catalog, visible: (Title) -> Boolean = { true }): List<Title> =
        catalog.titles.filter(visible).sortedBy { it.trendingRank }.take(TOP_SEARCHES)

    /** Normalised form stored in recent searches, or null if it isn't worth keeping. */
    fun normalizeForHistory(query: String): String? = query.trim().replace(Regex("\\s+"), " ").takeIf { it.length >= 2 }
}
