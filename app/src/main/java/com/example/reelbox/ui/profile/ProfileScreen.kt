package com.example.reelbox.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reelbox.container
import com.example.reelbox.data.ProfileRepository
import com.example.reelbox.data.db.ProfileEntity
import com.example.reelbox.ui.components.SimpleTopBar
import com.example.reelbox.ui.profiles.Avatar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ProfileTabViewModel(repo: ProfileRepository) : ViewModel() {
    val active: StateFlow<ProfileEntity?> = repo.activeProfile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory { initializer { ProfileTabViewModel(container.profileRepository) } }
    }
}

@Composable
fun ProfileScreen(
    onMyList: () -> Unit,
    onSettings: () -> Unit,
    onSwitchProfile: () -> Unit,
    vm: ProfileTabViewModel = viewModel(factory = ProfileTabViewModel.Factory),
) {
    val active by vm.active.collectAsStateWithLifecycle()
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    Column(Modifier.fillMaxSize().testTag("profile")) {
        SimpleTopBar("Profile")
        active?.let { p ->
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.avatar, 64.dp, kids = p.isKids)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("profile:activename"))
                    if (p.isKids) Text("Kids profile", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        ListItem(
            headlineContent = { Text("My List") },
            leadingContent = { Icon(Icons.Filled.Bookmarks, null) },
            modifier = Modifier.clickable(onClick = onMyList).testTag("profile:mylist"),
            colors = colors,
        )
        ListItem(
            headlineContent = { Text("Settings") },
            leadingContent = { Icon(Icons.Filled.Settings, null) },
            modifier = Modifier.clickable(onClick = onSettings).testTag("profile:settings"),
            colors = colors,
        )
        ListItem(
            headlineContent = { Text("Switch Profile") },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
            modifier = Modifier.clickable(onClick = onSwitchProfile).testTag("profile:switch"),
            colors = colors,
        )
    }
}
