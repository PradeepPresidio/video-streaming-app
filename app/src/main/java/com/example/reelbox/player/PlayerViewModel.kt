package com.example.reelbox.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import com.example.reelbox.container
import com.example.reelbox.data.catalog.AssetUris
import com.example.reelbox.data.catalog.CatalogRepository
import com.example.reelbox.data.catalog.Episode
import com.example.reelbox.data.catalog.MediaInfo
import com.example.reelbox.data.catalog.Title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NowPlaying(val title: Title, val episode: Episode?) {
    val media: MediaInfo get() = episode?.media ?: title.media!!
    val heading: String get() = episode?.let { "${title.title} · E${it.number} ${it.title}" } ?: title.title
}

/** Owns the ExoPlayer so playback survives configuration changes (rotation). */
class PlayerViewModel(
    app: Application,
    private val catalogRepository: CatalogRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val player: ExoPlayer = ExoPlayer.Builder(app).build()
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying

    init {
        viewModelScope.launch {
            val catalog = catalogRepository.catalog()
            val title = catalog.title(requireNotNull(savedState.get<String>("titleId"))) ?: return@launch
            val episodeId = savedState.get<String>("episodeId")?.takeIf { it.isNotEmpty() }
            val episode = if (title.isSeries) (episodeId?.let(catalog::episode) ?: title.episodes.first()) else null
            load(NowPlaying(title, episode), startMs = savedState.get<Long>(KEY_POSITION) ?: 0L)
        }
    }

    private fun load(np: NowPlaying, startMs: Long) {
        _nowPlaying.value = np
        val media = np.media
        val builder = MediaItem.Builder().setUri(AssetUris.media(media.video)).setMediaId(np.episode?.id ?: np.title.id)
        media.subtitles?.let { srt ->
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(AssetUris.media(srt)))
                        .setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("en").setLabel("English").build(),
                ),
            )
        }
        Log.i(TAG, "play ${np.heading} uri=${AssetUris.media(media.video)} start=$startMs")
        player.setMediaItem(builder.build(), startMs)
        player.prepare()
        player.playWhenReady = true
    }

    /** Remember the position so a recreated ViewModel (process death) resumes where it was. */
    fun rememberPosition() { savedState[KEY_POSITION] = player.currentPosition }

    override fun onCleared() {
        player.release()
    }

    companion object {
        const val TAG = "ReelBoxPlayer"
        private const val KEY_POSITION = "position"
        val Factory = viewModelFactory {
            initializer { PlayerViewModel(this[APPLICATION_KEY]!!, container.catalogRepository, createSavedStateHandle()) }
        }
    }
}
