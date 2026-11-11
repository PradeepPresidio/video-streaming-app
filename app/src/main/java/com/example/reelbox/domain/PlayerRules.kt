package com.example.reelbox.domain

import com.example.reelbox.data.catalog.MediaInfo

object PlayerRules {
    const val SEEK_STEP_MS = 10_000L
    const val NEXT_EPISODE_COUNTDOWN_MS = 10_000L
    val SPEEDS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

    /** Skip Intro is offered while the position is inside the intro window (until 1 s before it ends). */
    fun inIntro(media: MediaInfo, positionMs: Long): Boolean =
        media.hasIntro && positionMs >= media.introStartMs && positionMs < media.introEndMs - 1_000

    /**
     * Seconds left on the Next Episode countdown, or null when it shouldn't show.
     * The countdown covers the last 10 s of an episode that has a next episode.
     */
    fun nextEpisodeCountdown(positionMs: Long, durationMs: Long, hasNext: Boolean, ended: Boolean): Int? {
        if (!hasNext || durationMs <= 0) return null
        if (ended) return 0
        val remaining = durationMs - positionMs
        if (remaining > NEXT_EPISODE_COUNTDOWN_MS) return null
        return ((remaining.coerceAtLeast(0) + 999) / 1000).toInt()
    }

    /** Target for a relative seek, clamped to [0, duration]. */
    fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long =
        (positionMs + deltaMs).coerceIn(0, if (durationMs > 0) durationMs else Long.MAX_VALUE)

    fun speedLabel(speed: Float): String =
        if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
}
