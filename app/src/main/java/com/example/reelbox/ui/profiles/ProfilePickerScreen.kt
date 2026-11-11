package com.example.reelbox.ui.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reelbox.R
import com.example.reelbox.container
import com.example.reelbox.data.ProfileRepository
import com.example.reelbox.data.db.ProfileEntity
import com.example.reelbox.domain.ProfileRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilePickerViewModel(private val repo: ProfileRepository) : ViewModel() {
    val profiles: StateFlow<List<ProfileEntity>?> = repo.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(id: Long, then: () -> Unit) {
        viewModelScope.launch { repo.select(id); then() }
    }

    companion object {
        val Factory = viewModelFactory { initializer { ProfilePickerViewModel(container.profileRepository) } }
    }
}

/** "Who's watching?" — shown at launch and from Profile > Switch Profile. */
@Composable
fun ProfilePickerScreen(
    onProfileChosen: () -> Unit,
    onEdit: (Long) -> Unit,
    onAdd: () -> Unit,
    vm: ProfilePickerViewModel = viewModel(factory = ProfilePickerViewModel.Factory),
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    var managing by rememberSaveable { mutableStateOf(false) }
    val list = profiles ?: return
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).testTag("profiles"), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(top = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(40.dp))
            Text("ReelBox", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        Text(
            if (managing) "Manage profiles" else "Who's watching?",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp).testTag("profiles:title"),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            items(list, key = { it.id }) { p ->
                ProfileTile(p, managing) { if (managing) onEdit(p.id) else vm.select(p.id, onProfileChosen) }
            }
            if (ProfileRules.canAdd(list.size)) {
                item(key = "add") {
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onAdd).padding(8.dp).testTag("profiles:add"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(104.dp).border(BorderStroke(2.dp, Color(0x66FFFFFF)), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Add, null, Modifier.size(48.dp)) }
                        Text("Add Profile", modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { managing = !managing },
            modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(0.6f).testTag("profiles:manage"),
        ) { Text(if (managing) "Done" else "Manage Profiles", color = Color.White) }
    }
}

@Composable
private fun ProfileTile(p: ProfileEntity, managing: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp).testTag("profile:${p.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(p.avatar, 104.dp, kids = p.isKids)
            if (managing) {
                Box(Modifier.size(104.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Edit, "Edit ${p.name}", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
        }
        Text(p.name, modifier = Modifier.padding(top = 8.dp))
    }
}
