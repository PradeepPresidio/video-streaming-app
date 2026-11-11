package com.example.reelbox.domain

import com.example.reelbox.data.catalog.Catalog
import com.example.reelbox.data.catalog.Title

/** A horizontal row on Home. [progress] maps titleId → 0..1 for Continue Watching cards. */
data class HomeRow(
    val key: String,
    val label: String,
    val titles: List<Title>,
    val progress: Map<String, Float> = emptyMap(),
)

data class HomeContent(val hero: Title?, val rows: List<HomeRow>)

object HomeRowsBuilder {
    /**
     * Builds Home: hero (first featured title), then Continue Watching and My List (only when non-empty),
     * Trending, New Releases and one row per genre. [visible] filters the catalog (e.g. Kids profile).
     */
    fun build(
        catalog: Catalog,
        visible: (Title) -> Boolean = { true },
        continueWatching: List<Pair<Title, Float>> = emptyList(),
        myList: List<Title> = emptyList(),
    ): HomeContent {
        val titles = catalog.titles.filter(visible)
        val rows = buildList {
            val cw = continueWatching.filter { visible(it.first) }
            if (cw.isNotEmpty()) add(HomeRow("continue", "Continue Watching", cw.map { it.first }, cw.associate { it.first.id to it.second }))
            val ml = myList.filter(visible)
            if (ml.isNotEmpty()) add(HomeRow("mylist", "My List", ml))
            add(HomeRow("trending", "Trending Now", titles.sortedBy { it.trendingRank }))
            add(HomeRow("new", "New Releases", titles.sortedWith(compareByDescending<Title> { it.year }.thenBy { it.title })))
            titles.flatMap { it.genres }.distinct().forEach { genre ->
                add(HomeRow("genre:$genre", genre, titles.filter { genre in it.genres }))
            }
        }.filter { it.titles.isNotEmpty() }
        val hero = titles.firstOrNull { it.featured } ?: titles.firstOrNull()
        return HomeContent(hero, rows)
    }
}
