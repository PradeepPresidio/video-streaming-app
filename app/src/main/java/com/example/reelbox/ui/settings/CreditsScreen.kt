package com.example.reelbox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reelbox.ReelBoxApp
import com.example.reelbox.data.catalog.Credit
import com.example.reelbox.ui.components.SimpleTopBar

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as ReelBoxApp).container.catalogRepository
    var credits by remember { mutableStateOf<List<Credit>>(emptyList()) }
    LaunchedEffect(Unit) { credits = repo.catalog().credits }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar("Credits", onBack)
        LazyColumn(Modifier.testTag("credits")) {
            item {
                Text(
                    "ReelBox bundles clips from these Creative Commons films. Series episodes are segments cut from the films above. " +
                        "Posters and artwork are made from frames of the films.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(credits, key = { it.film }) { c ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).testTag("credit:${c.film}")) {
                    Text(c.film, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(c.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("License: ${c.license}", color = MaterialTheme.colorScheme.primary)
                    Text(c.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}
