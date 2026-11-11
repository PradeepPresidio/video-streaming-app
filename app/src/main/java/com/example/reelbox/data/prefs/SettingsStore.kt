package com.example.reelbox.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** App-wide settings (DataStore). activeProfileId == 0 means "no profile chosen yet". */
class SettingsStore(private val context: Context) {
    private object Keys {
        val activeProfile = longPreferencesKey("active_profile")
        val autoplayNext = booleanPreferencesKey("autoplay_next")
        val defaultSubtitles = booleanPreferencesKey("default_subtitles")
    }

    val activeProfileId: Flow<Long> = context.dataStore.data.map { it[Keys.activeProfile] ?: 0L }.distinctUntilChanged()
    val autoplayNext: Flow<Boolean> = context.dataStore.data.map { it[Keys.autoplayNext] ?: true }.distinctUntilChanged()
    val defaultSubtitles: Flow<Boolean> = context.dataStore.data.map { it[Keys.defaultSubtitles] ?: false }.distinctUntilChanged()

    suspend fun setActiveProfile(id: Long) = context.dataStore.edit { it[Keys.activeProfile] = id }
    suspend fun setAutoplayNext(on: Boolean) = context.dataStore.edit { it[Keys.autoplayNext] = on }
    suspend fun setDefaultSubtitles(on: Boolean) = context.dataStore.edit { it[Keys.defaultSubtitles] = on }
}
