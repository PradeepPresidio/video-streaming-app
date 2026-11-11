package com.example.reelbox.data

import com.example.reelbox.data.catalog.Title
import com.example.reelbox.data.db.ProfileDao
import com.example.reelbox.data.db.ProfileEntity
import com.example.reelbox.data.prefs.SettingsStore
import com.example.reelbox.domain.ProfileRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepository(
    private val dao: ProfileDao,
    private val settings: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** The active profile id; only emits once a profile has been chosen/created. */
    val activeProfileId: Flow<Long> = settings.activeProfileId.filter { it > 0 }

    val profiles: Flow<List<ProfileEntity>> = dao.observeAll()

    val activeProfile: Flow<ProfileEntity> = activeProfileId.flatMapLatest { dao.observe(it) }.filterNotNull()

    /** Maturity filter of the active profile (Kids: U and U/A 7+ only). */
    val visibility: Flow<(Title) -> Boolean> =
        activeProfile.map { it.isKids }.distinctUntilChanged().map { ProfileRules.visibility(it) }

    /** First launch: create a default profile and make sure the active id points at an existing profile. */
    suspend fun ensureDefaultProfile() {
        var all = dao.all()
        if (all.isEmpty()) {
            dao.insert(ProfileEntity(name = "Me", avatar = 0, isKids = false, createdAt = now()))
            all = dao.all()
        }
        val active = settings.activeProfileId.first()
        if (all.none { it.id == active }) settings.setActiveProfile(all.first().id)
    }

    suspend fun get(id: Long): ProfileEntity? = dao.get(id)

    suspend fun select(id: Long) = settings.setActiveProfile(id)

    /** Creates a profile; returns its id, or null when the name is invalid or 5 profiles already exist. */
    suspend fun create(name: String, avatar: Int, isKids: Boolean): Long? {
        val all = dao.all()
        if (!ProfileRules.canAdd(all.size)) return null
        val valid = ProfileRules.validName(name, all.map { it.name }) ?: return null
        return dao.insert(ProfileEntity(name = valid, avatar = avatar, isKids = isKids, createdAt = now()))
    }

    suspend fun update(id: Long, name: String, avatar: Int, isKids: Boolean): Boolean {
        val all = dao.all()
        val current = all.firstOrNull { it.id == id } ?: return false
        val valid = ProfileRules.validName(name, all.filter { it.id != id }.map { it.name }) ?: return false
        dao.update(current.copy(name = valid, avatar = avatar, isKids = isKids))
        return true
    }

    /** Deletes a profile and (via FK cascade) its progress, My List and recent searches. */
    suspend fun delete(id: Long) {
        dao.delete(id)
        ensureDefaultProfile() // keeps at least one profile and a valid active id
    }
}
