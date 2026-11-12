package com.example.reelbox.data.download

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.reelbox.data.catalog.Episode
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.data.db.DownloadDao
import com.example.reelbox.data.db.DownloadEntity
import com.example.reelbox.domain.DownloadItem
import com.example.reelbox.domain.DownloadStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Starts, pauses, resumes, cancels and deletes downloads. Downloads are device-wide (shared by profiles). */
class DownloadRepository(
    context: Context,
    private val dao: DownloadDao,
    private val storage: DownloadStorage,
) {
    private val appContext = context.applicationContext
    private val workManager by lazy { WorkManager.getInstance(appContext) }

    val items: Flow<List<DownloadItem>> = dao.observeAll().map { rows -> rows.map { it.toItem() } }

    /** Downloads a movie, or one episode of a series. Does nothing if it's already downloaded or in progress. */
    suspend fun start(title: Title, episode: Episode?) {
        val mediaId = episode?.id ?: title.id
        val existing = dao.get(mediaId)?.let { DownloadStatus.valueOf(it.status) }
        when (existing) {
            DownloadStatus.DONE, DownloadStatus.QUEUED, DownloadStatus.RUNNING -> return
            DownloadStatus.PAUSED -> { resume(mediaId); return }
            DownloadStatus.FAILED, null -> Unit
        }
        val media = episode?.media ?: requireNotNull(title.media) { "${title.id} has no media" }
        val total = appContext.assets.openFd(media.video).use { it.length }
        val now = System.currentTimeMillis()
        dao.upsert(DownloadEntity(mediaId, title.id, episode?.id, DownloadStatus.QUEUED.name, 0, total, now, now))
        enqueue(mediaId, ExistingWorkPolicy.KEEP)
    }

    /** Downloads every episode of a series that isn't downloaded yet. */
    suspend fun startAll(title: Title) = title.episodes.forEach { start(title, it) }

    suspend fun pause(mediaId: String) {
        if (dao.transition(mediaId, ACTIVE, DownloadStatus.PAUSED.name, System.currentTimeMillis()) > 0) {
            workManager.cancelUniqueWork(workName(mediaId))
            Log.i(TAG, "paused $mediaId")
        }
    }

    suspend fun resume(mediaId: String) {
        if (dao.transition(mediaId, listOf(DownloadStatus.PAUSED.name, DownloadStatus.FAILED.name), DownloadStatus.QUEUED.name, System.currentTimeMillis()) > 0) {
            // REPLACE: a stopped worker may still be unwinding; the new one waits on DownloadLocks.
            enqueue(mediaId, ExistingWorkPolicy.REPLACE)
            Log.i(TAG, "resumed $mediaId")
        }
    }

    /**
     * Cancels (or deletes) one item: stops its work and removes its files, partial or complete.
     * Non-cancellable: once the work is cancelled, the caller leaving (e.g. Back clears the ViewModel while
     * waiting for the worker's lock) must not strand a .part file and a row with no worker behind it.
     */
    suspend fun remove(mediaId: String): Unit = withContext(NonCancellable) {
        workManager.cancelUniqueWork(workName(mediaId))
        DownloadLocks.of(mediaId).withLock {
            val freed = storage.delete(mediaId)
            dao.delete(mediaId)
            Log.i(TAG, "removed $mediaId, freed $freed B")
        }
    }

    suspend fun pauseTitle(titleId: String) = rowsOf(titleId).filter { it.status in ACTIVE }.forEach { pause(it.mediaId) }
    suspend fun resumeTitle(titleId: String) = rowsOf(titleId).filter { it.status == DownloadStatus.PAUSED.name }.forEach { resume(it.mediaId) }

    /** Cancels a series download: every episode not yet finished is stopped and its partial file removed. */
    suspend fun cancelTitle(titleId: String): Unit = withContext(NonCancellable) {
        rowsOf(titleId).filter { it.status != DownloadStatus.DONE.name }.forEach { remove(it.mediaId) }
    }

    /** Settings > Delete all downloads. Returns the bytes freed. Non-cancellable for the same reason as [remove]. */
    suspend fun deleteAll(): Long = withContext(NonCancellable) {
        val ids = dao.all().map { it.mediaId }
        workManager.cancelAllWorkByTag(WORK_TAG)
        ids.forEach { id -> DownloadLocks.of(id).withLock { dao.delete(id) } }
        dao.deleteAll()
        storage.deleteAll()
    }

    /** The downloaded video file for [mediaId], if complete. */
    fun localVideo(mediaId: String) = storage.completedVideo(mediaId)
    fun localSubtitles(mediaId: String) = storage.subtitleFile(mediaId).takeIf { it.isFile }
    fun sizeOf(mediaId: String) = storage.sizeOf(mediaId)
    fun usedBytes() = storage.usedBytes()

    private suspend fun rowsOf(titleId: String) = dao.all().filter { it.titleId == titleId }

    private fun enqueue(mediaId: String, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_MEDIA_ID to mediaId))
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(workName(mediaId), policy, request)
        Log.i(TAG, "enqueued $mediaId ($policy)")
    }


    companion object {
        const val TAG = "ReelBoxDownload"
        const val WORK_TAG = "download"
        private val ACTIVE = listOf(DownloadStatus.QUEUED.name, DownloadStatus.RUNNING.name)
        fun workName(mediaId: String) = "download:$mediaId"
    }
}

fun DownloadEntity.toItem() = DownloadItem(mediaId, titleId, episodeId, DownloadStatus.valueOf(status), bytesDone, totalBytes)
