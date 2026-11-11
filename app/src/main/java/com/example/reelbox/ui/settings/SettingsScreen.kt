package com.example.reelbox.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reelbox.BuildConfig
import com.example.reelbox.container
import com.example.reelbox.data.prefs.SettingsStore
import com.example.reelbox.ui.components.SimpleTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val settings: SettingsStore,
    private val deleteAllDownloads: suspend () -> Unit,
    private val downloadsUsedBytes: suspend () -> Long,
) : ViewModel() {
    val autoplayNext: StateFlow<Boolean> = settings.autoplayNext.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val defaultSubtitles: StateFlow<Boolean> = settings.defaultSubtitles.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _used = MutableStateFlow(0L)
    val downloadsUsed: StateFlow<Long> = _used

    init { refreshUsage() }

    private fun refreshUsage() { viewModelScope.launch { _used.value = withContext(Dispatchers.IO) { downloadsUsedBytes() } } }

    fun setAutoplay(on: Boolean) { viewModelScope.launch { settings.setAutoplayNext(on) } }
    fun setDefaultSubtitles(on: Boolean) { viewModelScope.launch { settings.setDefaultSubtitles(on) } }
    fun deleteDownloads() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { deleteAllDownloads() }
            refreshUsage()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = container
                SettingsViewModel(c.settings, { c.downloadStorage.deleteAll() }, { c.downloadStorage.usedBytes() })
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onCredits: () -> Unit, vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val autoplay by vm.autoplayNext.collectAsStateWithLifecycle()
    val subs by vm.defaultSubtitles.collectAsStateWithLifecycle()
    val used by vm.downloadsUsed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all downloads?") },
            text = { Text("Frees ${Formatter.formatShortFileSize(context, used)}. Titles stay available to stream from the library.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteDownloads() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    Column(Modifier.fillMaxSize().testTag("settings")) {
        SimpleTopBar("Settings", onBack)
        ListItem(
            headlineContent = { Text("Autoplay next episode") },
            supportingContent = { Text("Start the next episode after a 10 s countdown") },
            trailingContent = { Switch(checked = autoplay, onCheckedChange = vm::setAutoplay, modifier = Modifier.testTag("settings:autoplay")) },
            modifier = Modifier.clickable { vm.setAutoplay(!autoplay) },
            colors = colors,
        )
        ListItem(
            headlineContent = { Text("Subtitles on by default") },
            supportingContent = { Text("Show subtitles automatically when a title has them") },
            trailingContent = { Switch(checked = subs, onCheckedChange = vm::setDefaultSubtitles, modifier = Modifier.testTag("settings:subtitles")) },
            modifier = Modifier.clickable { vm.setDefaultSubtitles(!subs) },
            colors = colors,
        )
        ListItem(
            headlineContent = { Text("Delete all downloads") },
            supportingContent = { Text("Using ${Formatter.formatShortFileSize(context, used)}", modifier = Modifier.testTag("settings:downloadsused")) },
            modifier = Modifier.clickable { confirmDelete = true }.testTag("settings:deletedownloads"),
            colors = colors,
        )
        ListItem(
            headlineContent = { Text("Credits") },
            supportingContent = { Text("Films, authors and licenses") },
            modifier = Modifier.clickable(onClick = onCredits).testTag("settings:credits"),
            colors = colors,
        )
        ListItem(
            headlineContent = { Text("App version") },
            supportingContent = { Text(BuildConfig.VERSION_NAME, modifier = Modifier.testTag("settings:version")) },
            colors = colors,
        )
    }
}
