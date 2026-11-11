package com.example.reelbox.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.reelbox.BuildConfig
import com.example.reelbox.ui.components.SimpleTopBar

@Composable
fun SettingsScreen(onBack: () -> Unit, onCredits: () -> Unit) {
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    Column(Modifier.fillMaxSize().testTag("settings")) {
        SimpleTopBar("Settings", onBack)
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
