package com.example.reelbox.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.ui.components.CenteredMessage
import com.example.reelbox.ui.components.PosterCard

@Composable
fun SearchScreen(onOpenTitle: (Title) -> Unit, vm: SearchViewModel = viewModel(factory = SearchViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val open: (Title) -> Unit = { vm.commit(); onOpenTitle(it) }
    Column(Modifier.fillMaxSize().testTag("search")) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("search:field"),
            placeholder = { Text("Search titles, genres, cast") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }, modifier = Modifier.testTag("search:clear")) {
                        Icon(Icons.Filled.Close, "Clear search")
                    }
                }
            },
            singleLine = true,
            // Search terms are names and genres: no autocorrect rewriting what the user typed.
            keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.commit(); focus.clearFocus() }),
        )
        when {
            state.loading -> Unit
            state.query.isBlank() -> PosterGrid {
                if (state.recent.isNotEmpty()) {
                    header("recent-header") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("Recent searches", Modifier.weight(1f))
                            TextButton(onClick = vm::clearRecent, modifier = Modifier.testTag("search:clearrecent")) { Text("Clear") }
                        }
                    }
                    state.recent.forEach { q ->
                        header("recent:$q") {
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.onQueryChange(q) }.padding(vertical = 10.dp).testTag("recent:$q"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(q, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
                header("top-header") { SectionTitle("Top searches") }
                items(state.topSearches, key = { "top:${it.id}" }) { t -> PosterCard(t, onClick = { open(t) }, width = 104.dp) }
            }
            state.results.isEmpty() -> Column(Modifier.testTag("search:noresults")) {
                CenteredMessage("No results for “${state.query.trim()}”. Try a title, genre or cast member.")
            }
            else -> PosterGrid(Modifier.testTag("search:results")) {
                items(state.results, key = { it.id }) { t -> PosterCard(t, onClick = { open(t) }, width = 104.dp) }
            }
        }
    }
}

@Composable
private fun PosterGrid(modifier: Modifier = Modifier, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

private fun LazyGridScope.header(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = modifier.padding(top = 4.dp))
}
