package com.example.reelbox.ui.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reelbox.container
import com.example.reelbox.data.ProfileRepository
import com.example.reelbox.data.db.ProfileEntity
import com.example.reelbox.domain.ProfileRules
import com.example.reelbox.ui.components.SimpleTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileEditViewModel(private val repo: ProfileRepository, savedState: SavedStateHandle) : ViewModel() {
    /** 0 = adding a new profile. */
    val profileId: Long = savedState.get<Long>("id") ?: 0L
    private val _existing = MutableStateFlow<ProfileEntity?>(null)
    val existing: StateFlow<ProfileEntity?> = _existing
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        if (profileId != 0L) viewModelScope.launch { _existing.value = repo.get(profileId) }
    }

    fun save(name: String, avatar: Int, kids: Boolean, done: () -> Unit) {
        viewModelScope.launch {
            val ok = if (profileId == 0L) repo.create(name, avatar, kids) != null else repo.update(profileId, name, avatar, kids)
            if (ok) done() else _error.value = "Enter a unique name of up to ${ProfileRules.MAX_NAME_LENGTH} characters."
        }
    }

    fun delete(done: () -> Unit) {
        viewModelScope.launch { repo.delete(profileId); done() }
    }

    companion object {
        val Factory = viewModelFactory { initializer { ProfileEditViewModel(container.profileRepository, createSavedStateHandle()) } }
    }
}

@Composable
fun ProfileEditScreen(onDone: () -> Unit, vm: ProfileEditViewModel = viewModel(factory = ProfileEditViewModel.Factory)) {
    val existing by vm.existing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val adding = vm.profileId == 0L
    var name by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf((System.currentTimeMillis() % ProfileRules.AVATAR_COUNT).toInt()) }
    var kids by rememberSaveable { mutableStateOf(false) }
    var loaded by rememberSaveable { mutableStateOf(adding) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(existing) {
        val p = existing
        if (p != null && !loaded) { name = p.name; avatar = p.avatar; kids = p.isKids; loaded = true }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete profile?") },
            text = { Text("This removes ${existing?.name ?: "this profile"} with its Continue Watching, My List and searches.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    Column(Modifier.fillMaxSize().testTag("profileedit")) {
        SimpleTopBar(if (adding) "Add Profile" else "Edit Profile", onDone)
        if (!loaded) return@Column
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(avatar, 120.dp, kids = kids, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(ProfileRules.AVATAR_COUNT) { i ->
                    Avatar(
                        i, 44.dp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .border(BorderStroke(if (i == avatar) 3.dp else 0.dp, if (i == avatar) Color.White else Color.Transparent), RoundedCornerShape(8.dp))
                            .clickable { avatar = i }.testTag("avatar:$i"),
                    )
                }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(ProfileRules.MAX_NAME_LENGTH) },
                label = { Text("Name") }, singleLine = true, isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("profile:name"),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kids profile")
                    Text("Only U and U/A 7+ titles", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = kids, onCheckedChange = { kids = it }, modifier = Modifier.testTag("profile:kids"))
            }
            Button(onClick = { vm.save(name, avatar, kids, onDone) }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp).testTag("profile:save")) {
                Text("Save")
            }
            if (!adding) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("profile:delete")) {
                    Text("Delete Profile", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
