package com.example.reelbox.data.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Loads the bundled catalog once (off the main thread) and caches it for the process lifetime. */
class CatalogRepository(private val context: Context) {
    private val mutex = Mutex()
    @Volatile private var cached: Catalog? = null

    suspend fun catalog(): Catalog = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val json = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
            CatalogParser.parse(json)
        }.also { cached = it }
    }
}
