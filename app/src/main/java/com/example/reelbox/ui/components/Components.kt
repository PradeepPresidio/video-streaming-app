package com.example.reelbox.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.reelbox.data.catalog.AssetUris
import com.example.reelbox.data.catalog.Title

@Composable
fun AssetImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    AsyncImage(
        model = AssetUris.image(path),
        contentDescription = contentDescription,
        contentScale = contentScale,
        alignment = alignment,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** 2:3 poster card. [progress] (0..1) draws a Continue Watching bar under the image. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: Title,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 88.dp,
    progress: Float? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .width(width)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = "poster:${title.id}" }
            .testTag("poster:${title.id}"),
    ) {
        AssetImage(title.poster, contentDescription = title.title, modifier = Modifier.fillMaxWidth().height(width * 1.5f))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0x55FFFFFF),
            )
        }
    }
}

@Composable
fun TitleRow(
    label: String,
    titles: List<Title>,
    onTitleClick: (Title) -> Unit,
    modifier: Modifier = Modifier,
    progress: Map<String, Float> = emptyMap(),
    onLongClick: ((Title) -> Unit)? = null,
    testTag: String = "row:$label",
) {
    Column(modifier.fillMaxWidth().testTag(testTag)) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(titles, key = { it.id }) { t ->
                PosterCard(
                    t, onClick = { onTitleClick(t) },
                    progress = if (progress.isEmpty()) null else progress[t.id],
                    onLongClick = onLongClick?.let { cb -> { cb(t) } },
                )
            }
        }
    }
}

@Composable
fun MaturityBadge(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
}

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}
