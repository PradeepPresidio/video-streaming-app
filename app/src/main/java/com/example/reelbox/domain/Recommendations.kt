package com.example.reelbox.domain

import com.example.reelbox.data.catalog.Catalog
import com.example.reelbox.data.catalog.Title

object Recommendations {
    /**
     * "More Like This": other titles sharing at least one genre with [title], most shared genres first,
     * then by trending rank. [visible] applies the profile's maturity filter.
     */
    fun moreLikeThis(catalog: Catalog, title: Title, visible: (Title) -> Boolean = { true }, limit: Int = 10): List<Title> =
        catalog.titles.asSequence()
            .filter { it.id != title.id && visible(it) }
            .map { it to it.genres.count { g -> g in title.genres } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Title, Int>> { it.second }.thenBy { it.first.trendingRank })
            .map { it.first }
            .take(limit)
            .toList()
}
