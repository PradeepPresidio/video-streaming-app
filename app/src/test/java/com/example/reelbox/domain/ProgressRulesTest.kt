package com.example.reelbox.domain

import com.example.reelbox.data.catalog.Catalog
import com.example.reelbox.data.catalog.Episode
import com.example.reelbox.data.catalog.Maturity
import com.example.reelbox.data.catalog.MediaInfo
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.data.catalog.TitleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressRulesTest {
    private fun ep(n: Int) = Episode("s-e$n", "s", 1, n, "E$n", "", "t", MediaInfo("v", 50_000, 0, 10_000, null))
    private val series = Title("s", TitleType.SERIES, "S", 2025, listOf("Kids"), Maturity.U, "", emptyList(), "p", "b", false, 1, null, listOf(ep(1), ep(2), ep(3)))
    private val catalog = Catalog(listOf(testTitle("m"), testTitle("m2"), series), emptyList())

    private fun movie(id: String, pos: Long, at: Long, dur: Long = 60_000) =
        ProgressEntry(id, id, null, pos, dur, ProgressRules.isFinished(pos, dur), at)
    private fun episode(n: Int, pos: Long, at: Long) =
        ProgressEntry("s-e$n", "s", "s-e$n", pos, 50_000, ProgressRules.isFinished(pos, 50_000), at)

    @Test fun finishedAt95Percent() {
        assertFalse(ProgressRules.isFinished(56_999, 60_000))
        assertTrue(ProgressRules.isFinished(57_000, 60_000))
        assertFalse(ProgressRules.isFinished(10, 0))
    }

    @Test fun continueWatchingNewestFirstWithFraction() {
        val cw = ProgressRules.continueWatching(catalog, listOf(movie("m", 30_000, 1), movie("m2", 6_000, 2)))
        assertEquals(listOf("m2", "m"), cw.map { it.title.id })
        assertEquals(0.5f, cw[1].fraction)
    }

    @Test fun finishedMovieLeavesContinueWatching() {
        assertTrue(ProgressRules.continueWatching(catalog, listOf(movie("m", 59_000, 1))).isEmpty())
    }

    @Test fun finishedEpisodeShowsNextEpisode() {
        val cw = ProgressRules.continueWatching(catalog, listOf(episode(1, 49_000, 5)))
        assertEquals("s-e2", cw.single().episode!!.id)
        assertEquals(0f, cw.single().fraction)
    }

    @Test fun finishedLastEpisodeDropsSeries() {
        assertTrue(ProgressRules.continueWatching(catalog, listOf(episode(3, 49_000, 5))).isEmpty())
    }

    @Test fun oneCardPerSeriesFromLatestEpisode() {
        val cw = ProgressRules.continueWatching(catalog, listOf(episode(1, 49_000, 1), episode(2, 20_000, 2)))
        assertEquals(1, cw.size)
        assertEquals("s-e2", cw.single().episode!!.id)
        assertEquals(20_000, cw.single().positionMs)
    }

    @Test fun movieResumeTargetHasRemainingTime() {
        val t = ProgressRules.playTarget(catalog, catalog.title("m")!!, listOf(movie("m", 20_000, 1)))
        assertEquals(20_000, t.positionMs)
        assertEquals(40_000L, t.remainingMs)
        assertNull(ProgressRules.playTarget(catalog, catalog.title("m")!!, emptyList()).remainingMs)
    }

    @Test fun seriesTargets() {
        assertEquals("s-e1", ProgressRules.playTarget(catalog, series, emptyList()).episode!!.id)
        val resume = ProgressRules.playTarget(catalog, series, listOf(episode(2, 10_000, 1)))
        assertEquals("s-e2", resume.episode!!.id)
        assertEquals(40_000L, resume.remainingMs)
        val next = ProgressRules.playTarget(catalog, series, listOf(episode(2, 49_000, 1)))
        assertEquals("s-e3", next.episode!!.id)
        assertNull(next.remainingMs)
    }

    @Test fun startPositionIgnoresFinished() {
        assertEquals(0, ProgressRules.startPosition(movie("m", 59_000, 1)))
        assertEquals(30_000, ProgressRules.startPosition(movie("m", 30_000, 1)))
        assertEquals(0, ProgressRules.startPosition(null))
    }
}
