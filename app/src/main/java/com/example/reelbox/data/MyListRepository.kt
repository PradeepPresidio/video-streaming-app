package com.example.reelbox.data

import com.example.reelbox.data.db.MyListDao
import com.example.reelbox.data.db.MyListEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class MyListRepository(
    private val dao: MyListDao,
    private val profiles: ProfileRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Title ids in the active profile's list, newest first. */
    val titleIds: Flow<List<String>> =
        profiles.activeProfileId.flatMapLatest { dao.observe(it) }.map { list -> list.map { it.titleId } }

    suspend fun add(titleId: String) = dao.upsert(MyListEntity(profiles.activeProfileId.first(), titleId, now()))

    suspend fun remove(titleId: String) = dao.delete(profiles.activeProfileId.first(), titleId)

    suspend fun toggle(titleId: String) {
        if (titleId in titleIds.first()) remove(titleId) else add(titleId)
    }
}
