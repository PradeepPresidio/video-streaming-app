package com.example.reelbox.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.reelbox.domain.DownloadState

class DownloadActions(
    val start: () -> Unit,
    val pause: () -> Unit,
    val resume: () -> Unit,
    val cancel: () -> Unit,
    val delete: () -> Unit,
)

/** What the button says for each state (also its accessibility description). */
fun downloadLabel(state: DownloadState, idle: String = "Download"): String = when (state) {
    DownloadState.None -> idle
    is DownloadState.Active -> "Downloading ${(state.fraction * 100).toInt()}%"
    is DownloadState.Paused -> "Paused ${(state.fraction * 100).toInt()}%"
    DownloadState.Failed -> "Retry download"
    DownloadState.Done -> "Downloaded"
}

/**
 * Download control: an icon (or progress ring while downloading) that starts a download, or opens a
 * menu to pause / resume / cancel / delete it. [showLabel] adds the text under the icon.
 */
@Composable
fun DownloadButton(
    state: DownloadState,
    actions: DownloadActions,
    tag: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    idleLabel: String = "Download",
    iconSize: Dp = 24.dp,
) {
    var menu by remember { mutableStateOf(false) }
    val requestNotifications = rememberNotificationPermissionRequest()
    val label = downloadLabel(state, idleLabel)
    Box(modifier) {
        Column(
            Modifier.clip(RoundedCornerShape(6.dp))
                .clickable {
                    when (state) {
                        DownloadState.None, DownloadState.Failed -> { requestNotifications(); actions.start() }
                        else -> menu = true
                    }
                }
                .padding(8.dp)
                .testTag(tag)
                .semantics(mergeDescendants = true) { contentDescription = label },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                when (state) {
                    is DownloadState.Active -> {
                        CircularProgressIndicator(
                            progress = { state.fraction }, modifier = Modifier.size(iconSize),
                            strokeWidth = 2.5.dp, trackColor = Color(0x44FFFFFF),
                        )
                        Icon(Icons.Filled.Stop, null, tint = Color.White, modifier = Modifier.size(iconSize * 0.45f))
                    }
                    is DownloadState.Paused -> {
                        CircularProgressIndicator(
                            progress = { state.fraction }, modifier = Modifier.size(iconSize),
                            strokeWidth = 2.5.dp, color = Color.Gray, trackColor = Color(0x44FFFFFF),
                        )
                        Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(iconSize * 0.5f))
                    }
                    DownloadState.Done -> Icon(Icons.Filled.DownloadDone, null, tint = MaterialTheme.colorScheme.primary)
                    DownloadState.Failed -> Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    DownloadState.None -> Icon(Icons.Filled.Download, null, tint = Color.White)
                }
            }
            if (showLabel) Text(label, fontSize = 12.sp)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val items: List<Pair<String, () -> Unit>> = when (state) {
                is DownloadState.Active -> listOf("Pause download" to actions.pause, "Cancel download" to actions.cancel)
                is DownloadState.Paused -> listOf("Resume download" to actions.resume, "Cancel download" to actions.cancel)
                DownloadState.Done -> listOf("Delete download" to actions.delete)
                else -> emptyList()
            }
            items.forEach { (text, action) -> DropdownMenuItem(text = { Text(text) }, onClick = { menu = false; action() }) }
        }
    }
}

/** On Android 13+ asks once for the notification permission used by the download progress notification. */
@Composable
private fun rememberNotificationPermissionRequest(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return {}
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* downloads run either way */ }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
