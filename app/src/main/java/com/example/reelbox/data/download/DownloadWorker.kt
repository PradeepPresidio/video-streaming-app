package com.example.reelbox.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.reelbox.ReelBoxApp
import com.example.reelbox.domain.DownloadRules
import com.example.reelbox.domain.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * "Downloads" one movie or episode by copying it from the bundled library (assets) into
 * files/downloads/ in throttled chunks, as a real network download would arrive.
 * Writes `<id>.part` and renames it to `<id>.mp4` when complete; a stopped worker leaves the
 * `.part` so a resume continues from its length. Cancel/delete is done by [DownloadRepository].
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val container = (context.applicationContext as ReelBoxApp).container
    private val dao = container.database.downloads()
    private val storage = container.downloadStorage

    override suspend fun doWork(): Result {
        val mediaId = inputData.getString(KEY_MEDIA_ID) ?: return Result.failure()
        return DownloadLocks.of(mediaId).withLock {
            // Paused, cancelled or deleted while this work was queued: nothing to do.
            val row = dao.get(mediaId)
            if (row == null || DownloadStatus.valueOf(row.status) !in ACTIVE) return@withLock Result.success()
            val catalog = container.catalogRepository.catalog()
            val title = catalog.title(row.titleId) ?: return@withLock fail(mediaId, "unknown title ${row.titleId}")
            val episode = row.episodeId?.let(catalog::episode)
            val media = episode?.media ?: title.media ?: return@withLock fail(mediaId, "no media for $mediaId")
            val label = episode?.let { "${title.title} E${it.number}" } ?: title.title
            dao.transition(mediaId, ACTIVE_NAMES, DownloadStatus.RUNNING.name, now())
            try {
                copy(mediaId, label, media.video, row.totalBytes)
                media.subtitles?.let { srt -> assets().open(srt).use { i -> storage.subtitleFile(mediaId).outputStream().use { i.copyTo(it) } } }
                val part = storage.partFile(mediaId)
                if (!part.renameTo(storage.videoFile(mediaId))) throw IOException("rename failed for $part")
                dao.progress(mediaId, storage.videoFile(mediaId).length(), now())
                dao.transition(mediaId, ACTIVE_NAMES, DownloadStatus.DONE.name, now())
                Log.i(TAG, "done $mediaId -> ${storage.videoFile(mediaId)} (${storage.videoFile(mediaId).length()} B)")
                Result.success()
            } catch (e: IOException) {
                fail(mediaId, "copy failed for $mediaId: $e")
            }
        }
    }

    private suspend fun copy(mediaId: String, label: String, assetPath: String, totalBytes: Long) {
        storage.dir.mkdirs()
        val part = storage.partFile(mediaId)
        var offset = DownloadRules.resumeOffset(part.length(), totalBytes)
        if (offset == 0L) part.delete()
        Log.i(TAG, "start $mediaId from $offset/$totalBytes")
        setForegroundSafely(mediaId, label, offset, totalBytes)
        assets().open(assetPath).use { input ->
            input.skipFully(offset)
            FileOutputStream(part, true).use { out ->
                val buf = ByteArray(DownloadRules.CHUNK_BYTES)
                var lastReport = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    offset += n
                    val t = System.currentTimeMillis()
                    if (t - lastReport >= DownloadRules.PROGRESS_INTERVAL_MS) {
                        lastReport = t
                        dao.progress(mediaId, offset, t)
                        setProgress(workDataOf(KEY_BYTES to offset))
                        setForegroundSafely(mediaId, label, offset, totalBytes)
                    }
                    delay(DownloadRules.CHUNK_DELAY_MS) // also the cancellation point for pause/cancel
                }
            }
        }
        if (offset != totalBytes) throw IOException("copied $offset of $totalBytes bytes")
    }

    private suspend fun setForegroundSafely(mediaId: String, label: String, done: Long, total: Long) {
        val notification = DownloadNotifications.progress(applicationContext, label, done, total)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(DownloadNotifications.idFor(mediaId), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(DownloadNotifications.idFor(mediaId), notification)
        }
        try {
            setForeground(info)
        } catch (e: CancellationException) {
            throw e // CancellationException is an IllegalStateException; never swallow a pause/cancel
        } catch (e: IllegalStateException) {
            // Android 12+ refuses to start a foreground service while the app is in the background;
            // the copy still runs as ordinary background work, just without the notification.
            Log.w(TAG, "foreground not allowed for $mediaId: ${e.message}")
        }
    }

    private suspend fun fail(mediaId: String, why: String): Result {
        Log.e(TAG, why)
        dao.transition(mediaId, ACTIVE_NAMES, DownloadStatus.FAILED.name, now())
        return Result.failure()
    }

    private fun assets() = applicationContext.assets
    private fun now() = System.currentTimeMillis()

    private fun InputStream.skipFully(n: Long) {
        var left = n
        while (left > 0) {
            val s = skip(left)
            if (s <= 0) throw IOException("could not skip to $n")
            left -= s
        }
    }

    companion object {
        const val TAG = "ReelBoxDownload"
        const val KEY_MEDIA_ID = "mediaId"
        const val KEY_BYTES = "bytes"
        private val ACTIVE = setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING)
        private val ACTIVE_NAMES = ACTIVE.map { it.name }
    }
}
