package com.example.reelbox.domain

import com.example.reelbox.data.catalog.Catalog
import com.example.reelbox.data.catalog.Episode
import com.example.reelbox.data.catalog.Title

/** Saved progress of one movie or episode (domain view of the progress table). */
data class ProgressEntry(
    val mediaId: String,
    val titleId: String,
    val episodeId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean,
    val updatedAt: Long,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** What Play/Resume does: start [episode] (null for a movie) at [positionMs]; [remainingMs] is set when resuming. */
data class PlayTarget(val episode: Episode?, val positionMs: Long, val remainingMs: Long?)

/** A Continue Watching card. */
data class ContinueItem(val title: Title, val episode: Episode?, val positionMs: Long, val fraction: Float, val updatedAt: Long)

object ProgressRules {
    const val FINISHED_FRACTION = 0.95
    const val SAVE_INTERVAL_MS = 5_000L

    fun isFinished(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs * FINISHED_FRACTION

    /**
     * Continue Watching: one card per title, from its most recently updated entry, newest first.
     * Finished movies drop out; a finished episode is replaced by the next episode (or drops out if it was the last).
     */
    fun continueWatching(catalog: Catalog, entries: List<ProgressEntry>): List<ContinueItem> =
        entries.groupBy { it.titleId }.mapNotNull { (titleId, list) ->
            val title = catalog.title(titleId) ?: return@mapNotNull null
            val latest = list.maxBy { it.updatedAt }
            when {
                !latest.finished -> ContinueItem(title, latest.episodeId?.let(catalog::episode), latest.positionMs, latest.fraction, latest.updatedAt)
                title.isSeries -> latest.episodeId?.let(catalog::nextEpisode)?.let { next ->
                    val nextEntry = list.firstOrNull { it.mediaId == next.id && !it.finished }
                    ContinueItem(title, next, nextEntry?.positionMs ?: 0L, nextEntry?.fraction ?: 0f, latest.updatedAt)
                }
                else -> null
            }
        }.sortedByDescending { it.updatedAt }

    /** Details page Play/Resume target for [title] given its progress entries. */
    fun playTarget(catalog: Catalog, title: Title, entries: List<ProgressEntry>): PlayTarget {
        val mine = entries.filter { it.titleId == title.id }
        val latest = mine.maxByOrNull { it.updatedAt }
        if (!title.isSeries) {
            return if (latest != null && !latest.finished && latest.positionMs > 0) {
                PlayTarget(null, latest.positionMs, (latest.durationMs - latest.positionMs).coerceAtLeast(0))
            } else PlayTarget(null, 0, null)
        }
        val first = title.episodes.first()
        if (latest == null) return PlayTarget(first, 0, null)
        val ep = latest.episodeId?.let(catalog::episode) ?: first
        if (!latest.finished) {
            return if (latest.positionMs > 0) PlayTarget(ep, latest.positionMs, (latest.durationMs - latest.positionMs).coerceAtLeast(0))
            else PlayTarget(ep, 0, null)
        }
        // Finished the latest episode: offer the next one (or start over after the last).
        return PlayTarget(catalog.nextEpisode(ep.id) ?: first, 0, null)
    }

    /** Where playback of [mediaId] should start: the saved position unless it was finished. */
    fun startPosition(entry: ProgressEntry?): Long = if (entry == null || entry.finished) 0L else entry.positionMs
}
