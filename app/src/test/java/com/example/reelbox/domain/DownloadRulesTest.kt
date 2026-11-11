package com.example.reelbox.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadRulesTest {
    private fun item(id: String, status: DownloadStatus, done: Long, total: Long = 100) = DownloadItem(id, "s", id, status, done, total)

    @Test fun fractionIsClampedAndSafeForUnknownTotal() {
        assertEquals(0f, DownloadRules.fraction(10, 0))
        assertEquals(0.5f, DownloadRules.fraction(50, 100))
        assertEquals(1f, DownloadRules.fraction(150, 100))
    }

    @Test fun resumeContinuesFromPartialLengthUnlessImpossible() {
        assertEquals(0L, DownloadRules.resumeOffset(0, 1000))
        assertEquals(640L, DownloadRules.resumeOffset(640, 1000))
        assertEquals(1000L, DownloadRules.resumeOffset(1000, 1000))
        assertEquals(0L, DownloadRules.resumeOffset(1200, 1000)) // corrupt/oversized partial restarts
    }

    @Test fun singleItemStates() {
        assertEquals(DownloadState.None, DownloadRules.state(null))
        assertEquals(DownloadState.Active(0f), DownloadRules.state(item("m", DownloadStatus.QUEUED, 0)))
        assertEquals(DownloadState.Active(0.4f), DownloadRules.state(item("m", DownloadStatus.RUNNING, 40)))
        assertEquals(DownloadState.Paused(0.4f), DownloadRules.state(item("m", DownloadStatus.PAUSED, 40)))
        assertEquals(DownloadState.Failed, DownloadRules.state(item("m", DownloadStatus.FAILED, 40)))
        assertEquals(DownloadState.Done, DownloadRules.state(item("m", DownloadStatus.DONE, 100)))
    }

    @Test fun seriesStateCombinesEpisodes() {
        val eps = listOf("e1", "e2", "e3")
        assertEquals(DownloadState.None, DownloadRules.seriesState(emptyList(), eps))
        assertEquals(
            DownloadState.Active(0.5f),
            DownloadRules.seriesState(listOf(item("e1", DownloadStatus.DONE, 100), item("e2", DownloadStatus.RUNNING, 0)), eps),
        )
        assertEquals(
            DownloadState.Paused(0.25f),
            DownloadRules.seriesState(listOf(item("e1", DownloadStatus.PAUSED, 50), item("e2", DownloadStatus.PAUSED, 0)), eps),
        )
        // Some episodes downloaded, nothing running: the rest can still be downloaded.
        assertEquals(DownloadState.None, DownloadRules.seriesState(listOf(item("e1", DownloadStatus.DONE, 100)), eps))
        assertEquals(DownloadState.Done, DownloadRules.seriesState(eps.map { item(it, DownloadStatus.DONE, 100) }, eps))
        // Downloads of other titles are ignored.
        assertEquals(DownloadState.None, DownloadRules.seriesState(listOf(item("x", DownloadStatus.RUNNING, 5)), eps))
    }
}
