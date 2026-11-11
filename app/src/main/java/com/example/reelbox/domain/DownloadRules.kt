package com.example.reelbox.domain

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED;
    val active: Boolean get() = this == QUEUED || this == RUNNING
}

/** One downloadable item: a movie (mediaId = titleId) or an episode (mediaId = episodeId). */
data class DownloadItem(
    val mediaId: String,
    val titleId: String,
    val episodeId: String?,
    val status: DownloadStatus,
    val bytesDone: Long,
    val totalBytes: Long,
) {
    val fraction: Float get() = DownloadRules.fraction(bytesDone, totalBytes)
}

/** What a Download button shows for a movie, an episode, or a whole series. */
sealed interface DownloadState {
    data object None : DownloadState
    data class Active(val fraction: Float) : DownloadState
    data class Paused(val fraction: Float) : DownloadState
    data object Failed : DownloadState
    data object Done : DownloadState
}

object DownloadRules {
    /** Bytes copied per chunk, and the pause between chunks (keeps progress visible, like a real network download). */
    const val CHUNK_BYTES = 64 * 1024
    const val CHUNK_DELAY_MS = 120L
    const val PROGRESS_INTERVAL_MS = 250L

    fun fraction(done: Long, total: Long): Float =
        if (total <= 0) 0f else (done.toDouble() / total).toFloat().coerceIn(0f, 1f)

    /**
     * Where a resumed copy continues: the length of the partial file, unless that is impossible
     * (larger than the source), in which case the copy restarts from 0.
     */
    fun resumeOffset(partLength: Long, totalBytes: Long): Long = if (partLength in 0..totalBytes) partLength else 0L

    fun state(item: DownloadItem?): DownloadState = when (item?.status) {
        null -> DownloadState.None
        DownloadStatus.QUEUED, DownloadStatus.RUNNING -> DownloadState.Active(item.fraction)
        DownloadStatus.PAUSED -> DownloadState.Paused(item.fraction)
        DownloadStatus.FAILED -> DownloadState.Failed
        DownloadStatus.DONE -> DownloadState.Done
    }

    /**
     * Combined state of a series' episodes: Done only when every episode is downloaded; Active while
     * any episode is still copying (progress over the episodes in flight or finished); Paused when
     * something is paused and nothing runs; otherwise None (the rest can still be downloaded).
     */
    fun seriesState(items: List<DownloadItem>, episodeIds: List<String>): DownloadState {
        val mine = items.filter { it.mediaId in episodeIds }
        if (episodeIds.isNotEmpty() && episodeIds.all { id -> mine.any { it.mediaId == id && it.status == DownloadStatus.DONE } }) {
            return DownloadState.Done
        }
        val batch = mine.filter { it.status != DownloadStatus.FAILED }
        val frac = fraction(batch.sumOf { it.bytesDone }, batch.sumOf { it.totalBytes })
        return when {
            mine.any { it.status.active } -> DownloadState.Active(frac)
            mine.any { it.status == DownloadStatus.PAUSED } -> DownloadState.Paused(frac)
            else -> DownloadState.None
        }
    }
}
