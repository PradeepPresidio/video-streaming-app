package com.example.reelbox.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reelbox.R
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.domain.ContinueItem
import com.example.reelbox.ui.components.AssetImage
import com.example.reelbox.ui.components.MaturityBadge
import com.example.reelbox.ui.components.TitleRow

@Composable
fun HomeScreen(
    onOpenTitle: (Title) -> Unit,
    onPlay: (Title) -> Unit,
    onResume: (ContinueItem) -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by vm.state.collectAsStateWithLifecycle()
    val state = uiState
    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val home = state.content
    var removeCandidate by remember { mutableStateOf<Title?>(null) }
    removeCandidate?.let { t ->
        AlertDialog(
            onDismissRequest = { removeCandidate = null },
            title = { Text(t.title) },
            text = { Text("Remove from Continue Watching?") },
            confirmButton = {
                TextButton(onClick = { vm.removeFromContinueWatching(t.id); removeCandidate = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeCandidate = null }) { Text("Cancel") } },
        )
    }
    LazyColumn(Modifier.fillMaxSize().testTag("home"), state = rememberLazyListState()) {
        home.hero?.let { hero ->
            item(key = "hero") {
                HeroBanner(hero, inMyList = false, onPlay = { onPlay(hero) }, onToggleMyList = {}, onOpen = { onOpenTitle(hero) })
            }
        }
        items(home.rows, key = { it.key }) { row ->
            if (row.key == "continue") {
                // Continue Watching cards resume playback directly; long-press offers Remove.
                TitleRow(
                    row.label, row.titles,
                    onTitleClick = { t -> state.continueItems[t.id]?.let(onResume) },
                    progress = row.progress,
                    onLongClick = { t -> removeCandidate = t },
                    testTag = "row:${row.key}",
                )
            } else {
                TitleRow(row.label, row.titles, onTitleClick = onOpenTitle, progress = row.progress, testTag = "row:${row.key}")
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun HeroBanner(hero: Title, inMyList: Boolean, onPlay: () -> Unit, onToggleMyList: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("hero")) {
        // The backdrop already carries the title and genres (tools/prepare_media.sh); cropping from the top keeps them.
        Box(Modifier.fillMaxWidth().aspectRatio(2f).clickable(onClick = onOpen)) {
            AssetImage(
                hero.backdrop,
                contentDescription = "${hero.title}. ${hero.genres.joinToString(", ")}",
                modifier = Modifier.fillMaxSize().testTag("hero:backdrop"),
                alignment = Alignment.BottomCenter,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color(0xAA000000), 0.3f to Color.Transparent, 0.85f to Color.Transparent, 1f to MaterialTheme.colorScheme.background),
                ),
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(32.dp))
                Text("ReelBox", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            MaturityBadge(hero.maturity.label, Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.weight(1f).testTag("hero:play"),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Text("Play", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onToggleMyList, modifier = Modifier.weight(1f).testTag("hero:mylist")) {
                Icon(if (inMyList) Icons.Filled.Check else Icons.Filled.Add, null, tint = Color.White)
                Text("My List", color = Color.White)
            }
            OutlinedButton(onClick = onOpen, modifier = Modifier.testTag("hero:info")) { Text("Info", color = Color.White) }
        }
    }
}
