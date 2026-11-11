package com.example.reelbox.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.reelbox.ui.components.AssetImage
import com.example.reelbox.ui.components.MaturityBadge
import com.example.reelbox.ui.components.TitleRow

@Composable
fun HomeScreen(
    onOpenTitle: (Title) -> Unit,
    onPlay: (Title) -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val content by vm.content.collectAsStateWithLifecycle()
    val home = content
    if (home == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("home"), state = rememberLazyListState()) {
        home.hero?.let { hero ->
            item(key = "hero") {
                HeroBanner(hero, inMyList = false, onPlay = { onPlay(hero) }, onToggleMyList = {}, onOpen = { onOpenTitle(hero) })
            }
        }
        items(home.rows, key = { it.key }) { row ->
            TitleRow(row.label, row.titles, onTitleClick = onOpenTitle, progress = row.progress, testTag = "row:${row.key}")
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun HeroBanner(hero: Title, inMyList: Boolean, onPlay: () -> Unit, onToggleMyList: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("hero")) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AssetImage(hero.backdrop, contentDescription = hero.title, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color(0x99000000), 0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to MaterialTheme.colorScheme.background),
                ),
            )
            Row(Modifier.statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(36.dp))
                Text("ReelBox", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hero.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("hero:title"))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                MaturityBadge(hero.maturity.label)
                Spacer(Modifier.width(8.dp))
                Text(hero.genres.joinToString("  •  "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.testTag("hero:play"),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Text("Play", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onToggleMyList, modifier = Modifier.testTag("hero:mylist")) {
                    Icon(if (inMyList) Icons.Filled.Check else Icons.Filled.Add, null, tint = Color.White)
                    Text("My List", color = Color.White)
                }
                OutlinedButton(onClick = onOpen, modifier = Modifier.testTag("hero:info")) { Text("Info", color = Color.White) }
            }
        }
    }
}
