package com.example.reelbox.data

import com.example.reelbox.data.catalog.CatalogRepository
import com.example.reelbox.data.db.ProgressDao
import com.example.reelbox.data.db.ProgressEntity
import com.example.reelbox.domain.ProgressEntry
import com.example.reelbox.domain.ProgressRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressRepository(
    private val dao: ProgressDao,
    private val profiles: ProfileRepository,
    private val catalogRepository: CatalogRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** All progress entries of the active profile. */
    val entries: Flow<List<ProgressEntry>> =
        profiles.activeProfileId.flatMapLatest { dao.observe(it) }.map { list -> list.map { it.toDomain() } }

    suspend fun get(mediaId: String): ProgressEntry? = dao.get(profiles.activeProfileId.first(), mediaId)?.toDomain()

    /**
     * Saves the position of a movie ([episodeId] null) or an episode. When an episode is finished, the next
     * episode becomes the series' newest entry so Continue Watching moves on to it.
     */
    suspend fun save(titleId: String, episodeId: String?, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        val profileId = profiles.activeProfileId.first()
        val finished = ProgressRules.isFinished(positionMs, durationMs)
        val at = now()
        dao.upsert(ProgressEntity(profileId, episodeId ?: titleId, titleId, episodeId, positionMs.coerceIn(0, durationMs), durationMs, finished, at))
        if (finished && episodeId != null) {
            val next = catalogRepository.catalog().nextEpisode(episodeId) ?: return
            val existing = dao.get(profileId, next.id)
            val keepPosition = existing != null && !existing.finished
            dao.upsert(
                ProgressEntity(
                    profileId, next.id, titleId, next.id,
                    positionMs = if (keepPosition) existing!!.positionMs else 0L,
                    durationMs = next.media.durationMs, finished = false, updatedAt = at + 1,
                ),
            )
        }
    }

    /** "Remove from Continue Watching": forgets the title's progress for the active profile. */
    suspend fun removeTitle(titleId: String) = dao.deleteTitle(profiles.activeProfileId.first(), titleId)

    private fun ProgressEntity.toDomain() = ProgressEntry(mediaId, titleId, episodeId, positionMs, durationMs, finished, updatedAt)
}
