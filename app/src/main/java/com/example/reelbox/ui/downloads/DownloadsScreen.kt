package com.example.reelbox.ui.downloads

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reelbox.container
import com.example.reelbox.data.ProfileRepository
import com.example.reelbox.data.catalog.CatalogRepository
import com.example.reelbox.data.catalog.Episode
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.data.download.DownloadRepository
import com.example.reelbox.domain.DownloadItem
import com.example.reelbox.domain.DownloadRules
import com.example.reelbox.domain.DownloadStatus
import com.example.reelbox.ui.components.AssetImage
import com.example.reelbox.ui.components.CenteredMessage
import com.example.reelbox.ui.components.DownloadActions
import com.example.reelbox.ui.components.DownloadButton
import com.example.reelbox.ui.components.SimpleTopBar
import com.example.reelbox.ui.components.downloadLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadRow(val item: DownloadItem, val title: Title, val episode: Episode?, val sizeBytes: Long) {
    val mediaId: String get() = item.mediaId
    val name: String get() = title.title
    val subtitle: String? get() = episode?.let { "S${it.season}:E${it.number} · ${it.title}" }
}

data class DownloadsUiState(val loading: Boolean = true, val rows: List<DownloadRow> = emptyList(), val usedBytes: Long = 0)

class DownloadsViewModel(
    catalogRepository: CatalogRepository,
    private val downloads: DownloadRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {
    val state: StateFlow<DownloadsUiState> = combine(
        flow { emit(catalogRepository.catalog()) },
        downloads.items,
        profileRepository.visibility,
    ) { catalog, items, visible ->
        val rows = items.mapNotNull { item ->
            // A Kids profile doesn't see downloads of titles it may not watch.
            val title = catalog.title(item.titleId)?.takeIf(visible) ?: return@mapNotNull null
            DownloadRow(item, title, item.episodeId?.let(catalog::episode), downloads.sizeOf(item.mediaId))
        }.sortedWith(compareBy({ it.title.title }, { it.episode?.season ?: 0 }, { it.episode?.number ?: 0 }))
        DownloadsUiState(loading = false, rows = rows, usedBytes = downloads.usedBytes())
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun pause(mediaId: String) { viewModelScope.launch { downloads.pause(mediaId) } }
    fun resume(mediaId: String) { viewModelScope.launch { downloads.resume(mediaId) } }
    fun remove(mediaId: String) { viewModelScope.launch { downloads.remove(mediaId) } }

    companion object {
        val Factory = viewModelFactory {
            initializer { DownloadsViewModel(container.catalogRepository, container.downloadRepository, container.profileRepository) }
        }
    }
}

@Composable
fun DownloadsScreen(
    onPlay: (title: Title, episode: Episode?) -> Unit,
    vm: DownloadsViewModel = viewModel(factory = DownloadsViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Downloads")
        if (!state.loading && state.rows.isEmpty()) {
            CenteredMessage("No downloads yet. Tap Download on a title to watch it offline.", Modifier.testTag("downloads:empty"))
            return@Column
        }
        Text(
            "${state.rows.count { it.item.status == DownloadStatus.DONE }} downloaded · Using ${Formatter.formatShortFileSize(context, state.usedBytes)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("downloads:used"),
        )
        LazyColumn(Modifier.fillMaxSize().testTag("downloads:list")) {
            items(state.rows, key = { it.mediaId }) { row ->
                DownloadRowItem(
                    row,
                    sizeText = Formatter.formatShortFileSize(context, row.sizeBytes),
                    onPlay = { onPlay(row.title, row.episode) },
                    actions = DownloadActions(
                        start = { vm.resume(row.mediaId) }, pause = { vm.pause(row.mediaId) }, resume = { vm.resume(row.mediaId) },
                        cancel = { vm.remove(row.mediaId) }, delete = { vm.remove(row.mediaId) },
                    ),
                    onDelete = { vm.remove(row.mediaId) },
                )
            }
        }
    }
}

@Composable
private fun DownloadRowItem(row: DownloadRow, sizeText: String, onPlay: () -> Unit, actions: DownloadActions, onDelete: () -> Unit) {
    val done = row.item.status == DownloadStatus.DONE
    Row(
        Modifier.fillMaxWidth()
            .then(if (done) Modifier.clickable(onClick = onPlay) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("downloads:item:${row.mediaId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssetImage(
            row.episode?.thumbnail ?: row.title.backdrop, contentDescription = null,
            modifier = Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            row.subtitle?.let { Text(it, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(
                if (done) sizeText else "${downloadLabel(DownloadRules.state(row.item))} · $sizeText",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                modifier = Modifier.testTag("downloads:size:${row.mediaId}"),
            )
        }
        if (done) {
            IconButton(onClick = onDelete, modifier = Modifier.testTag("downloads:delete:${row.mediaId}")) {
                Icon(Icons.Filled.Delete, "Delete download of ${row.subtitle ?: row.name}", tint = Color.White)
            }
        } else {
            DownloadButton(DownloadRules.state(row.item), actions, "downloads:status:${row.mediaId}", showLabel = false)
        }
    }
}
