package com.example.reelbox

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.util.Rational
import android.view.KeyEvent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.example.reelbox.ui.navigation.ReelBoxNavHost
import com.example.reelbox.ui.theme.RbAccent
import com.example.reelbox.ui.theme.RbKidsAccent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.reelbox.ui.theme.ReelBoxTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : ComponentActivity() {
    /** True while the player is on screen; Home then shrinks playback into picture-in-picture. */
    var pipEnabled = false

    /** Set by the player while shown: handles hardware media keys (headset, keyboard). Returns true if consumed. */
    var mediaKeyHandler: ((Int) -> Boolean)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        mediaKeyHandler?.invoke(keyCode) == true || super.onKeyDown(keyCode, event)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipEnabled && !isInPictureInPictureMode) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ReelBox)
        // Edge-to-edge so Compose receives real system-bar/cutout insets (the player keeps its controls inside them).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Phone portrait browsing; PlayerScreen switches to landscape while it is shown.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            // Kids profiles get the brighter accent everywhere.
            val container = (application as ReelBoxApp).container
            val profile by container.profileRepository.activeProfile.collectAsStateWithLifecycle(initialValue = null)
            ReelBoxTheme(accent = if (profile?.isKids == true) RbKidsAccent else RbAccent) {
              // Expose Compose testTags as resource-ids so adb/uiautomator checks can find nodes.
              Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                // Skip the splash when restoring after process death or recreation.
                var showSplash by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                LaunchedEffect(Unit) { if (showSplash) { delay(900); showSplash = false } }
                if (showSplash) SplashContent() else ReelBoxNavHost(rememberNavController())
              }
            }
        }
    }
}

@Composable
fun SplashContent() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(140.dp))
            Text("ReelBox", color = RbAccent, fontSize = 36.sp, fontWeight = FontWeight.Black)
        }
    }
}

