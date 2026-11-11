package com.example.reelbox.data

import com.example.reelbox.data.db.ProfileDao
import com.example.reelbox.data.db.ProfileEntity
import com.example.reelbox.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

class ProfileRepository(private val dao: ProfileDao, private val settings: SettingsStore) {
    /** The active profile id; only emits once a profile has been chosen/created. */
    val activeProfileId: Flow<Long> = settings.activeProfileId.filter { it > 0 }

    val profiles: Flow<List<ProfileEntity>> = dao.observeAll()

    /** First launch: create a default profile and make sure the active id points at an existing profile. */
    suspend fun ensureDefaultProfile() {
        var all = dao.all()
        if (all.isEmpty()) {
            dao.insert(ProfileEntity(name = "Me", avatar = 0, isKids = false, createdAt = System.currentTimeMillis()))
            all = dao.all()
        }
        val active = settings.activeProfileId.first()
        if (all.none { it.id == active }) settings.setActiveProfile(all.first().id)
    }
}
