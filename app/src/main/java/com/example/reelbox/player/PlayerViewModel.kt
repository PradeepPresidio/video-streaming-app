package com.example.reelbox.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.reelbox.BuildConfig
import com.example.reelbox.container
import com.example.reelbox.data.catalog.AssetUris
import com.example.reelbox.data.catalog.Catalog
import com.example.reelbox.data.catalog.CatalogRepository
import com.example.reelbox.data.catalog.Episode
import com.example.reelbox.data.catalog.MediaInfo
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.domain.PlayerRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NowPlaying(val title: Title, val episode: Episode?) {
    val media: MediaInfo get() = episode?.media ?: title.media!!
    val heading: String get() = episode?.let { "${title.title} · E${it.number} ${it.title}" } ?: title.title
}

data class PlayerUiState(
    val nowPlaying: NowPlaying? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val isPlaying: Boolean = false,
    val ended: Boolean = false,
    val speed: Float = 1f,
    val subtitlesOn: Boolean = false,
    val nextEpisode: Episode? = null,
    /** Seconds left before the next episode auto-plays; null when no countdown is showing. */
    val countdown: Int? = null,
    val countdownCancelled: Boolean = false,
    val showSkipIntro: Boolean = false,
    val autoplayNext: Boolean = true,
) {
    val hasSubtitles: Boolean get() = nowPlaying?.media?.subtitles != null
}

/** Owns the ExoPlayer so playback survives configuration changes (rotation, PiP). */
class PlayerViewModel(
    app: Application,
    private val catalogRepository: CatalogRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setSeekBackIncrementMs(PlayerRules.SEEK_STEP_MS)
        .setSeekForwardIncrementMs(PlayerRules.SEEK_STEP_MS)
        .build()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private var catalog: Catalog? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) maybeAutoplayNext()
        }
    }

    init {
        player.addListener(listener)
        viewModelScope.launch {
            val catalog = catalogRepository.catalog().also { catalog = it }
            val title = catalog.title(requireNotNull(savedState.get<String>(ARG_TITLE))) ?: return@launch
            val episodeId = savedState.get<String>(ARG_EPISODE)?.takeIf { it.isNotEmpty() }
            val episode = if (title.isSeries) (episodeId?.let(catalog::episode) ?: title.episodes.first()) else null
            setSubtitles(savedState[KEY_SUBS] ?: false)
            load(NowPlaying(title, episode), startMs = savedState.get<Long>(KEY_POSITION) ?: 0L)
        }
        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                refresh()
                maybeAutoplayNext()
                // Debug builds log playback state once a second; adb-driven checks read it from logcat.
                if (BuildConfig.DEBUG && tick++ % 4 == 0) logState()
                delay(250)
            }
        }
    }

    private fun load(np: NowPlaying, startMs: Long) {
        val media = np.media
        val builder = MediaItem.Builder().setUri(AssetUris.media(media.video)).setMediaId(np.episode?.id ?: np.title.id)
        media.subtitles?.let { srt ->
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(AssetUris.media(srt)))
                        .setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("en").setLabel("English")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build(),
                ),
            )
        }
        Log.i(TAG, "play ${np.heading} uri=${AssetUris.media(media.video)} start=$startMs")
        _state.update {
            it.copy(
                nowPlaying = np, ended = false, countdown = null, countdownCancelled = false,
                nextEpisode = np.episode?.let { ep -> catalog?.nextEpisode(ep.id) },
            )
        }
        player.setMediaItem(builder.build(), startMs)
        player.prepare()
        player.playWhenReady = true
    }

    private fun refresh() {
        val np = _state.value.nowPlaying ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: np.media.durationMs
        val position = player.currentPosition
        val ended = player.playbackState == Player.STATE_ENDED
        _state.update {
            it.copy(
                positionMs = position,
                durationMs = duration,
                bufferedMs = player.bufferedPosition,
                isPlaying = player.isPlaying,
                ended = ended,
                speed = player.playbackParameters.speed,
                showSkipIntro = PlayerRules.inIntro(np.media, position) && !ended,
                countdown = if (it.countdownCancelled || !it.autoplayNext) null
                else PlayerRules.nextEpisodeCountdown(position, duration, it.nextEpisode != null, ended),
            )
        }
    }

    private fun logState() {
        val s = _state.value
        val np = s.nowPlaying ?: return
        Log.d(
            TAG,
            "state id=${np.episode?.id ?: np.title.id} pos=${s.positionMs} dur=${s.durationMs} playing=${s.isPlaying} " +
                "speed=${s.speed} subs=${s.subtitlesOn} skipIntro=${s.showSkipIntro} countdown=${s.countdown} ended=${s.ended}",
        )
    }

    private fun maybeAutoplayNext() {
        val s = _state.value
        val next = s.nextEpisode ?: return
        if (s.countdownCancelled || !s.autoplayNext) return
        if (s.ended || s.countdown == 0) playEpisode(next)
    }

    fun togglePlay() {
        // At the end playWhenReady is still true, so "play" must restart rather than toggle it off.
        when {
            player.playbackState == Player.STATE_ENDED -> { player.seekTo(0); player.play() }
            player.playWhenReady -> player.pause()
            else -> player.play()
        }
        refresh()
    }

    fun seekBy(deltaMs: Long) = seekTo(PlayerRules.seekTarget(player.currentPosition, deltaMs, _state.value.durationMs))

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        // Leaving the end window re-arms the countdown.
        if (_state.value.durationMs - positionMs > PlayerRules.NEXT_EPISODE_COUNTDOWN_MS) _state.update { it.copy(countdownCancelled = false) }
        refresh()
    }

    fun skipIntro() {
        val media = _state.value.nowPlaying?.media ?: return
        seekTo(media.introEndMs)
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        refresh()
    }

    fun setSubtitles(on: Boolean) {
        savedState[KEY_SUBS] = on
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
            .setPreferredTextLanguage(if (on) "en" else null)
            .setSelectUndeterminedTextLanguage(on)
            .build()
        _state.update { it.copy(subtitlesOn = on) }
    }

    fun cancelCountdown() = _state.update { it.copy(countdownCancelled = true, countdown = null) }

    fun playNextNow() = _state.value.nextEpisode?.let(::playEpisode)

    private fun playEpisode(ep: Episode) {
        val title = _state.value.nowPlaying?.title ?: return
        savedState[ARG_EPISODE] = ep.id
        savedState[KEY_POSITION] = 0L
        load(NowPlaying(title, ep), 0L)
    }

    fun setAutoplayNext(enabled: Boolean) = _state.update { it.copy(autoplayNext = enabled) }

    /** Remember the position so a recreated ViewModel (process death) resumes where it was. */
    fun rememberPosition() { savedState[KEY_POSITION] = player.currentPosition }

    fun pause() { player.pause() }

    override fun onCleared() {
        player.removeListener(listener)
        player.release()
    }

    companion object {
        const val TAG = "ReelBoxPlayer"
        private const val ARG_TITLE = "titleId"
        private const val ARG_EPISODE = "episodeId"
        private const val KEY_POSITION = "position"
        private const val KEY_SUBS = "subtitles"
        val Factory = viewModelFactory {
            initializer { PlayerViewModel(this[APPLICATION_KEY]!!, container.catalogRepository, createSavedStateHandle()) }
        }
    }
}
