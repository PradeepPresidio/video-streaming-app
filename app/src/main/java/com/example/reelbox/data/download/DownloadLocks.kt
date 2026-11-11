package com.example.reelbox.data.download

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * One lock per item, held by the worker while it writes the file and by the repository while it
 * deletes it. A resumed worker therefore can't append while a just-stopped one is still unwinding,
 * and a cancel can't race a final write.
 */
object DownloadLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()
    fun of(mediaId: String): Mutex = locks.getOrPut(mediaId) { Mutex() }
}
