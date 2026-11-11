package com.example.reelbox.data

import com.example.reelbox.data.db.RecentSearchDao
import com.example.reelbox.data.db.RecentSearchEntity
import com.example.reelbox.domain.SearchEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Per-profile recent searches (up to 5, newest first). */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchRepository(
    private val dao: RecentSearchDao,
    private val profiles: ProfileRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val recent: Flow<List<String>> = profiles.activeProfileId
        .flatMapLatest { dao.observe(it, SearchEngine.MAX_RECENT) }
        .map { list -> list.map { it.query } }

    suspend fun record(query: String) {
        val q = SearchEngine.normalizeForHistory(query) ?: return
        val profileId = profiles.activeProfileId.first()
        dao.upsert(RecentSearchEntity(profileId, q, now()))
        dao.trim(profileId, SearchEngine.MAX_RECENT)
    }

    suspend fun clear() = dao.clear(profiles.activeProfileId.first())
}
