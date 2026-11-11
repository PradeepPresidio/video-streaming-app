package com.example.reelbox.data.download

import android.content.Context
import java.io.File

/**
 * App-private storage for downloaded copies: files/downloads/.
 * Per item: `<mediaId>.part` while copying, then `<mediaId>.mp4` (+ `<mediaId>.srt` if it has subtitles).
 */
class DownloadStorage(context: Context) {
    val dir: File = File(context.filesDir, "downloads")

    fun partFile(mediaId: String) = File(dir, "$mediaId.part")
    fun videoFile(mediaId: String) = File(dir, "$mediaId.mp4")
    fun subtitleFile(mediaId: String) = File(dir, "$mediaId.srt")

    /** The finished video, or null if it isn't fully downloaded. */
    fun completedVideo(mediaId: String): File? = videoFile(mediaId).takeIf { it.isFile && it.length() > 0 }

    /** Bytes on disk for one item (finished files and any partial copy). */
    fun sizeOf(mediaId: String): Long = listOf(partFile(mediaId), videoFile(mediaId), subtitleFile(mediaId)).sumOf { it.length() }

    fun usedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Removes every file of one item; returns the bytes freed. */
    fun delete(mediaId: String): Long {
        val freed = sizeOf(mediaId)
        listOf(partFile(mediaId), videoFile(mediaId), subtitleFile(mediaId)).forEach { it.delete() }
        return freed
    }

    /** Deletes every downloaded file; returns the bytes freed. */
    fun deleteAll(): Long {
        val freed = usedBytes()
        dir.listFiles()?.forEach { it.delete() }
        return freed
    }
}
