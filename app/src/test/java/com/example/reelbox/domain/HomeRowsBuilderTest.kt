package com.example.reelbox.domain

import com.example.reelbox.data.catalog.Catalog
import com.example.reelbox.data.catalog.Maturity
import com.example.reelbox.data.catalog.MediaInfo
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.data.catalog.TitleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

fun testTitle(
    id: String, genres: List<String> = listOf("Drama"), year: Int = 2020, rank: Int = 1,
    featured: Boolean = false, maturity: Maturity = Maturity.U, cast: List<String> = emptyList(),
) = Title(id, TitleType.MOVIE, id.replaceFirstChar { it.uppercase() }, year, genres, maturity, "", cast,
    "p", "b", featured, rank, MediaInfo("v", 60_000, 0, 0, null), emptyList())

class HomeRowsBuilderTest {
    private val catalog = Catalog(
        listOf(
            testTitle("a", listOf("Kids"), 2008, rank = 2),
            testTitle("b", listOf("Sci-Fi"), 2012, rank = 1, featured = true, maturity = Maturity.A),
            testTitle("c", listOf("Kids", "Comedy"), 2016, rank = 3),
        ), emptyList(),
    )

    @Test fun emptyContinueWatchingAndMyListAreHidden() {
        val keys = HomeRowsBuilder.build(catalog).rows.map { it.key }
        assertFalse("continue" in keys)
        assertFalse("mylist" in keys)
        assertEquals(listOf("trending", "new", "genre:Kids", "genre:Sci-Fi", "genre:Comedy"), keys)
    }

    @Test fun trendingAndNewReleasesOrder() {
        val rows = HomeRowsBuilder.build(catalog).rows.associateBy { it.key }
        assertEquals(listOf("b", "a", "c"), rows["trending"]!!.titles.map { it.id })
        assertEquals(listOf("c", "b", "a"), rows["new"]!!.titles.map { it.id })
    }

    @Test fun continueWatchingAndMyListFirstWhenPresent() {
        val c = catalog.title("c")!!
        val home = HomeRowsBuilder.build(catalog, continueWatching = listOf(c to 0.4f), myList = listOf(catalog.title("a")!!))
        assertEquals("continue", home.rows[0].key)
        assertEquals(0.4f, home.rows[0].progress["c"])
        assertEquals("mylist", home.rows[1].key)
    }

    @Test fun heroIsFeaturedAndFilterApplies() {
        assertEquals("b", HomeRowsBuilder.build(catalog).hero!!.id)
        val kids = HomeRowsBuilder.build(catalog, visible = { it.maturity.kidsSafe })
        assertEquals("a", kids.hero!!.id)
        assertTrue(kids.rows.none { r -> r.titles.any { it.id == "b" } })
    }
}
