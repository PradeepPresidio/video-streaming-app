package com.example.reelbox.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reelbox.container
import com.example.reelbox.data.ProfileRepository
import com.example.reelbox.data.SearchRepository
import com.example.reelbox.data.catalog.CatalogRepository
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.domain.SearchEngine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Title> = emptyList(),
    val recent: List<String> = emptyList(),
    val topSearches: List<Title> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    catalogRepository: CatalogRepository,
    private val searchRepository: SearchRepository,
    profileRepository: ProfileRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val query = savedState.getStateFlow(KEY_QUERY, "")

    val state: StateFlow<SearchUiState> = combine(
        flow { emit(catalogRepository.catalog()) },
        query,
        searchRepository.recent.onStart { emit(emptyList()) },
        profileRepository.visibility,
    ) { catalog, q, recent, visible ->
        SearchUiState(q, SearchEngine.search(catalog, q, visible), recent, SearchEngine.topSearches(catalog, visible), loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        // A query the user has stopped typing for 1.5 s counts as a search for "recent searches".
        viewModelScope.launch {
            query.drop(1).debounce(SETTLE_MS).filter { it.isNotBlank() }.distinctUntilChanged()
                .collect { searchRepository.record(it) }
        }
    }

    fun onQueryChange(q: String) { savedState[KEY_QUERY] = q }

    /** Keyboard search action or opening a result records the query immediately. */
    fun commit() {
        val q = query.value
        if (q.isNotBlank()) viewModelScope.launch { searchRepository.record(q) }
    }

    fun clearRecent() { viewModelScope.launch { searchRepository.clear() } }

    companion object {
        private const val KEY_QUERY = "query"
        private const val SETTLE_MS = 1_500L
        val Factory = viewModelFactory {
            initializer { SearchViewModel(container.catalogRepository, container.searchRepository, container.profileRepository, createSavedStateHandle()) }
        }
    }
}
