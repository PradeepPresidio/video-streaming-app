package com.example.reelbox.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.reelbox.ui.components.SimpleTopBar

@Composable
fun ProfileScreen(onSettings: () -> Unit) {
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    Column(Modifier.fillMaxSize().testTag("profile")) {
        SimpleTopBar("Profile")
        ListItem(
            headlineContent = { Text("Settings") },
            leadingContent = { Icon(Icons.Filled.Settings, null) },
            modifier = Modifier.clickable(onClick = onSettings).testTag("profile:settings"),
            colors = colors,
        )
    }
}
