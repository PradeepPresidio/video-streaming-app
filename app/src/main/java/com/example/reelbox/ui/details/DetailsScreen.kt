package com.example.reelbox.ui.details

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reelbox.data.catalog.Episode
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.ui.components.AssetImage
import com.example.reelbox.ui.components.CenteredMessage
import com.example.reelbox.ui.components.MaturityBadge
import com.example.reelbox.ui.components.TitleRow
import com.example.reelbox.domain.PlayTarget
import com.example.reelbox.util.formatDurationShort

@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onPlay: (title: Title, episode: Episode?) -> Unit,
    onOpenTitle: (Title) -> Unit,
    onToggleMyList: (Title) -> Unit = {},
    onDownload: (Title) -> Unit = {},
    vm: DetailsViewModel = viewModel(factory = DetailsViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val title = state.title
    when {
        state.notFound -> CenteredMessage("Title not found")
        title == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> LazyColumn(Modifier.fillMaxSize().testTag("details:${title.id}")) {
            item(key = "header") {
                DetailsHeader(title, state, onBack, onPlay = { onPlay(title, state.playTarget?.episode) }, onToggleMyList = { onToggleMyList(title) }, onDownload = { onDownload(title) })
            }
            if (title.isSeries) {
                item(key = "episodes-header") { EpisodesHeader(title.seasons, state.selectedSeason, vm::selectSeason) }
                items(title.episodes.filter { it.season == state.selectedSeason }, key = { it.id }) { ep ->
                    EpisodeItem(ep, state.progress[ep.id], onClick = { onPlay(title, ep) })
                }
            }
            if (state.moreLikeThis.isNotEmpty()) {
                item(key = "more") {
                    TitleRow("More Like This", state.moreLikeThis, onTitleClick = onOpenTitle, testTag = "row:morelikethis")
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailsHeader(
    title: Title,
    state: DetailsUiState,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onToggleMyList: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AssetImage(title.backdrop, contentDescription = title.title, modifier = Modifier.fillMaxSize().testTag("details:backdrop"))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x88000000), 0.3f to Color.Transparent)))
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(8.dp).clip(CircleShape).background(Color(0x66000000)).testTag("details:back"),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(title.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp).testTag("details:title"))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(title.year.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("details:year"))
                Spacer(Modifier.width(10.dp))
                MaturityBadge(title.maturity.label, Modifier.testTag("details:maturity"))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (title.isSeries) "${title.episodes.size} Episodes" else formatDurationShort(title.totalDurationMs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("details:length"),
                )
            }
            Text(
                title.genres.joinToString("  •  "),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp).testTag("details:genres"),
            )
            val target = state.playTarget
            Button(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("details:play"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(6.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Text(playLabel(title, target), fontWeight = FontWeight.Bold)
            }
            if (target?.remainingMs != null) {
                Text(
                    "${formatDurationShort(target.remainingMs)} left",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp).testTag("details:timeleft"),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ActionButton(if (state.inMyList) Icons.Filled.Check else Icons.Filled.Add, "My List", "details:mylist", onToggleMyList)
                ActionButton(Icons.Filled.Download, "Download", "details:download", onDownload)
            }
            Text(title.description, modifier = Modifier.padding(top = 8.dp).testTag("details:description"))
            if (title.cast.isNotEmpty()) {
                Text(
                    "Cast: " + title.cast.joinToString(", "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).testTag("details:cast"),
                )
            }
        }
    }
}

private fun playLabel(title: Title, target: PlayTarget?): String {
    val ep = target?.episode
    val verb = if (target?.remainingMs != null) "Resume" else "Play"
    return if (title.isSeries && ep != null) "$verb S${ep.season}:E${ep.number}" else verb
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(8.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Color.White)
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun EpisodesHeader(seasons: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (seasons.size > 1) {
            var open by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { open = true }, modifier = Modifier.testTag("details:season")) {
                    Text("Season $selected", color = Color.White)
                    Icon(Icons.Filled.ArrowDropDown, null, tint = Color.White)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    seasons.forEach { s -> DropdownMenuItem(text = { Text("Season $s") }, onClick = { onSelect(s); open = false }) }
                }
            }
        } else {
            Text("Season $selected", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EpisodeItem(ep: Episode, progress: Float?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp).testTag("episode:${ep.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(128.dp).clip(RoundedCornerShape(4.dp))) {
                AssetImage(ep.thumbnail, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Icon(
                    Icons.Filled.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(32.dp).clip(CircleShape).background(Color(0x88000000)),
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary, trackColor = Color(0x55FFFFFF),
                    )
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("${ep.number}. ${ep.title}", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatDurationShort(ep.media.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        if (ep.description.isNotEmpty()) {
            Text(ep.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
