package com.example.reelbox.domain

import com.example.reelbox.data.catalog.MediaInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRulesTest {
    private val ep = MediaInfo("v", 55_000, 0, 10_000, null)

    @Test fun introWindow() {
        assertTrue(PlayerRules.inIntro(ep, 0))
        assertTrue(PlayerRules.inIntro(ep, 8_999))
        assertFalse(PlayerRules.inIntro(ep, 9_000))
        assertFalse(PlayerRules.inIntro(ep, 20_000))
        assertFalse(PlayerRules.inIntro(ep.copy(introEndMs = 0), 0))
    }

    @Test fun countdownOnlyInLastTenSecondsWithNext() {
        assertNull(PlayerRules.nextEpisodeCountdown(40_000, 55_000, hasNext = true, ended = false))
        assertEquals(10, PlayerRules.nextEpisodeCountdown(45_000, 55_000, hasNext = true, ended = false))
        assertEquals(3, PlayerRules.nextEpisodeCountdown(52_500, 55_000, hasNext = true, ended = false))
        assertEquals(0, PlayerRules.nextEpisodeCountdown(55_000, 55_000, hasNext = true, ended = true))
        assertNull(PlayerRules.nextEpisodeCountdown(54_000, 55_000, hasNext = false, ended = false))
    }

    @Test fun seekClamps() {
        assertEquals(20_000, PlayerRules.seekTarget(10_000, 10_000, 55_000))
        assertEquals(0, PlayerRules.seekTarget(4_000, -10_000, 55_000))
        assertEquals(55_000, PlayerRules.seekTarget(50_000, 10_000, 55_000))
    }

    @Test fun speedLabels() {
        assertEquals(listOf("0.5x", "1x", "1.25x", "1.5x", "2x"), PlayerRules.SPEEDS.map(PlayerRules::speedLabel))
    }
}
