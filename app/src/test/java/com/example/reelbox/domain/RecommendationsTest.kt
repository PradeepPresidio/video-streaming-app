package com.example.reelbox.domain

import com.example.reelbox.data.catalog.Catalog
import com.example.reelbox.data.catalog.Maturity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationsTest {
    private val catalog = Catalog(
        listOf(
            testTitle("base", listOf("Animation", "Fantasy")),
            testTitle("both", listOf("Fantasy", "Animation"), rank = 9),
            testTitle("one", listOf("Animation"), rank = 1),
            testTitle("none", listOf("Sci-Fi")),
            testTitle("adult", listOf("Fantasy"), maturity = Maturity.A),
        ), emptyList(),
    )

    @Test fun sameGenreOnlyMostSharedFirstExcludingSelf() {
        val r = Recommendations.moreLikeThis(catalog, catalog.title("base")!!)
        assertEquals(listOf("both", "one", "adult"), r.map { it.id })
    }

    @Test fun respectsVisibilityFilter() {
        val r = Recommendations.moreLikeThis(catalog, catalog.title("base")!!, visible = { it.maturity.kidsSafe })
        assertEquals(listOf("both", "one"), r.map { it.id })
    }
}
