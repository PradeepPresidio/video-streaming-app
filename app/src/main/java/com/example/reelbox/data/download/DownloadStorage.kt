package com.example.reelbox.data.download

import android.content.Context
import java.io.File

/** App-private storage for downloaded copies: files/downloads/. */
class DownloadStorage(context: Context) {
    val dir: File = File(context.filesDir, "downloads")

    fun usedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Deletes every downloaded file; returns the bytes freed. */
    fun deleteAll(): Long {
        val freed = usedBytes()
        dir.listFiles()?.forEach { it.delete() }
        return freed
    }
}
