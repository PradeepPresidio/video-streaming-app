package com.example.reelbox.domain

import com.example.reelbox.data.catalog.Catalog
import com.example.reelbox.data.catalog.Maturity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    private val catalog = Catalog(
        listOf(
            testTitle("sintel", listOf("Animation", "Fantasy"), rank = 2, cast = listOf("Halina Reijn")),
            testTitle("tears of steel", listOf("Sci-Fi"), rank = 4, cast = listOf("Derek de Lint"), maturity = Maturity.UA16),
            testTitle("steel protocol", listOf("Sci-Fi", "Action"), rank = 8, maturity = Maturity.A),
            testTitle("spring", listOf("Animation", "Fantasy"), rank = 1),
            testTitle("comedy club", listOf("Drama"), rank = 3),
            testTitle("big buck bunny", listOf("Comedy", "Kids"), rank = 5),
        ), emptyList(),
    )
    private fun ids(q: String, visible: (com.example.reelbox.data.catalog.Title) -> Boolean = { true }) =
        SearchEngine.search(catalog, q, visible).map { it.id }

    @Test fun partialTitleCaseInsensitive() = assertEquals(listOf("sintel"), ids("SINT"))

    @Test fun wordPrefixRanksBeforeContains() = assertEquals(listOf("steel protocol", "tears of steel"), ids("steel"))

    @Test fun exactGenreReturnsOnlyThatGenre() {
        assertEquals(listOf("big buck bunny"), ids("comedy"))   // not "comedy club", which isn't in the genre
        assertEquals(listOf("tears of steel", "steel protocol"), ids("Sci-Fi"))
    }

    @Test fun matchesCast() = assertEquals(listOf("tears of steel"), ids("derek"))

    @Test fun noResults() = assertTrue(ids("zzzz").isEmpty())

    @Test fun blankQueryIsEmpty() = assertTrue(ids("   ").isEmpty())

    @Test fun visibilityFilterHidesTitles() = assertEquals(emptyList<String>(), ids("steel") { it.maturity.kidsSafe })

    @Test fun topSearchesByTrending() =
        assertEquals(listOf("spring", "sintel", "comedy club"), SearchEngine.topSearches(catalog).take(3).map { it.id })

    @Test fun historyNormalization() {
        assertEquals("tears of", SearchEngine.normalizeForHistory("  tears   of "))
        assertNull(SearchEngine.normalizeForHistory("a"))
    }
}
