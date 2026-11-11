package com.example.reelbox.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.reelbox.MainActivity
import com.example.reelbox.domain.PlayerRules
import com.example.reelbox.util.formatTime
import kotlinx.coroutines.delay

private const val AUTO_HIDE_MS = 3_000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(onBack: () -> Unit, vm: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val inPip = rememberIsInPipMode()
    FullscreenLandscape()
    PipEligibility()
    PauseWhenStopped(vm)

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var seekFlash by remember { mutableStateOf<String?>(null) }
    val bump: () -> Unit = { interaction++ }

    // Controls hide 3 s after the last interaction while playing.
    LaunchedEffect(controlsVisible, interaction, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(AUTO_HIDE_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekFlash) { if (seekFlash != null) { delay(700); seekFlash = null } }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("player")) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    subtitleView?.setFractionalTextSize(0.065f)
                    player = vm.player
                }
            },
            onRelease = { it.player = null },
        )
        if (inPip) return@Box

        // Gesture layer: single tap toggles controls, double tap seeks ±10 s on the left/right half.
        Box(
            Modifier.fillMaxSize().testTag("player:surface").pointerInput(locked) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible; bump() },
                    onDoubleTap = { offset ->
                        if (!locked) {
                            val forward = offset.x > size.width / 2f
                            vm.seekBy(if (forward) PlayerRules.SEEK_STEP_MS else -PlayerRules.SEEK_STEP_MS)
                            seekFlash = if (forward) "+10s" else "−10s"
                        }
                    },
                )
            },
        )

        AnimatedVisibility(controlsVisible && !locked, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        }
        // Overlays stay inside the safe area (hidden nav bar + display cutout) so nothing sits under the
        // nav-bar strip or the camera cutout, where it can't be reliably touched. A plain Box doesn't consume taps.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.displayCutout))) {
            seekFlash?.let { label ->
                Text(
                    label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(if (label.startsWith("+")) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 96.dp).testTag("player:seekflash"),
                )
            }

            AnimatedVisibility(controlsVisible && !locked, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Controls(state, vm, onBack = onBack, onLock = { locked = true; bump() }, bump = bump)
            }
            AnimatedVisibility(controlsVisible && locked, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterStart)) {
                IconButton(
                    onClick = { locked = false; bump() },
                    modifier = Modifier.padding(24.dp).clip(CircleShape).background(Color(0x99000000)).testTag("player:unlock"),
                ) { Icon(Icons.Filled.Lock, "Unlock controls", tint = Color.White) }
            }

            if (state.showSkipIntro && !locked) {
                OutlinedButton(
                    onClick = { vm.skipIntro(); bump() },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 96.dp).testTag("player:skipintro"),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xAA000000)),
                ) { Text("Skip Intro", color = Color.White, fontWeight = FontWeight.Bold) }
            }

            state.countdown?.let { secs -> NextEpisodeOverlay(state, secs, vm) }
        }
    }
}

@Composable
private fun Controls(state: PlayerUiState, vm: PlayerViewModel, onBack: () -> Unit, onLock: () -> Unit, bump: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        // Top bar: back, title, speed, subtitles, lock.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("player:back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                state.nowPlaying?.heading.orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("player:title"),
            )
            SpeedButton(state.speed, onSelect = { vm.setSpeed(it); bump() }, bump = bump)
            if (state.hasSubtitles) {
                IconButton(
                    onClick = { vm.setSubtitles(!state.subtitlesOn); bump() },
                    modifier = Modifier.testTag("player:subtitles").semantics {
                        contentDescription = if (state.subtitlesOn) "Subtitles on" else "Subtitles off"
                    },
                ) {
                    Icon(
                        if (state.subtitlesOn) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionDisabled, null,
                        tint = if (state.subtitlesOn) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            }
            IconButton(onClick = onLock, modifier = Modifier.testTag("player:lock")) {
                Icon(Icons.Filled.LockOpen, "Lock controls", tint = Color.White)
            }
        }

        // Centre: −10 s, play/pause, +10 s.
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            IconButton(onClick = { vm.seekBy(-PlayerRules.SEEK_STEP_MS); bump() }, modifier = Modifier.size(64.dp).testTag("player:rew")) {
                Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(44.dp))
            }
            IconButton(
                onClick = { vm.togglePlay(); bump() },
                modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0x55FFFFFF)).testTag("player:playpause"),
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(52.dp),
                )
            }
            IconButton(onClick = { vm.seekBy(PlayerRules.SEEK_STEP_MS); bump() }, modifier = Modifier.size(64.dp).testTag("player:ffwd")) {
                Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }

        // Bottom: elapsed, seek bar, total (+ next episode shortcut for series).
        var dragging by remember { mutableStateOf(false) }
        var dragFraction by remember { mutableFloatStateOf(0f) }
        val duration = state.durationMs.coerceAtLeast(1)
        val fraction = if (dragging) dragFraction else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatTime(if (dragging) (dragFraction * duration).toLong() else state.positionMs), color = Color.White, modifier = Modifier.testTag("player:elapsed"))
            Slider(
                value = fraction,
                onValueChange = { dragging = true; dragFraction = it; bump() },
                onValueChangeFinished = { vm.seekTo((dragFraction * duration).toLong()); dragging = false },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).testTag("player:seekbar"),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color(0x66FFFFFF),
                ),
            )
            Text(formatTime(state.durationMs), color = Color.White, modifier = Modifier.testTag("player:total"))
            if (state.nextEpisode != null) {
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { vm.playNextNow(); bump() }, modifier = Modifier.testTag("player:nextbutton")) {
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White)
                    Text("Next Episode", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SpeedButton(speed: Float, onSelect: (Float) -> Unit, bump: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true; bump() }, modifier = Modifier.testTag("player:speed")) {
            Icon(Icons.Filled.Speed, null, tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text(PlayerRules.speedLabel(speed), color = Color.White, modifier = Modifier.testTag("player:speedlabel"))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PlayerRules.SPEEDS.forEach { s ->
                val label = PlayerRules.speedLabel(s)
                DropdownMenuItem(
                    text = { Text(if (s == speed) "✓ $label" else label) },
                    onClick = { onSelect(s); open = false },
                    modifier = Modifier.testTag("speed:$label"),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.NextEpisodeOverlay(state: PlayerUiState, secs: Int, vm: PlayerViewModel) {
    val next = state.nextEpisode ?: return
    Column(
        Modifier.align(Alignment.BottomEnd).padding(end = 32.dp, bottom = 96.dp)
            .clip(RoundedCornerShape(8.dp)).background(Color(0xDD161A22)).padding(16.dp)
            .testTag("player:countdown"),
    ) {
        Text("Next episode in $secs", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("player:countdowntext"))
        Text("E${next.number} · ${next.title}", color = Color(0xFFA7ADBA))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.playNextNow() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.testTag("player:playnext"),
            ) { Text("Play now") }
            OutlinedButton(onClick = { vm.cancelCountdown() }, modifier = Modifier.testTag("player:cancelnext")) {
                Text("Cancel", color = Color.White)
            }
        }
    }
}

/** Forces landscape + immersive mode while on screen and returns to portrait browsing afterwards. */
@Composable
fun FullscreenLandscape() {
    val activity = LocalContext.current as Activity
    DisposableEffect(Unit) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            // Browsing is portrait-only (requirements §5); the player is the only landscape screen.
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}

/** Lets MainActivity enter picture-in-picture on Home while this screen is shown. */
@Composable
private fun PipEligibility() {
    val activity = LocalContext.current as? MainActivity ?: return
    DisposableEffect(Unit) {
        activity.pipEnabled = true
        onDispose { activity.pipEnabled = false }
    }
}

@Composable
private fun rememberIsInPipMode(): Boolean {
    val activity = LocalContext.current as ComponentActivity
    var inPip by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/** Keep playing in PiP, but pause when the activity is really stopped (PiP closed, app backgrounded). */
@Composable
private fun PauseWhenStopped(vm: PlayerViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalContext.current as Activity
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                vm.rememberPosition()
                if (!activity.isInPictureInPictureMode) vm.pause()
            }
        }
        // Closing the PiP window stops the activity while it may still report PiP mode; pause once PiP ends.
        val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            if (!info.isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.pause()
        }
        lifecycle.addObserver(observer)
        (activity as ComponentActivity).addOnPictureInPictureModeChangedListener(pipListener)
        onDispose {
            lifecycle.removeObserver(observer)
            activity.removeOnPictureInPictureModeChangedListener(pipListener)
        }
    }
}
