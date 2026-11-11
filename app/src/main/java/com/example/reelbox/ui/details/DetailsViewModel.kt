package com.example.reelbox.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reelbox.container
import com.example.reelbox.data.catalog.CatalogRepository
import com.example.reelbox.data.catalog.Episode
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.domain.Recommendations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Play button does: start/resume [episode] (null for a movie) at [positionMs]. */
data class PlayTarget(val episode: Episode?, val positionMs: Long, val remainingMs: Long?)

data class DetailsUiState(
    val title: Title? = null,
    val notFound: Boolean = false,
    val selectedSeason: Int = 1,
    val moreLikeThis: List<Title> = emptyList(),
    /** episodeId (or titleId for a movie) -> watched fraction 0..1. */
    val progress: Map<String, Float> = emptyMap(),
    val playTarget: PlayTarget? = null,
    val inMyList: Boolean = false,
) {
    val isResume: Boolean get() = playTarget?.remainingMs != null
}

class DetailsViewModel(
    private val catalogRepository: CatalogRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val titleId: String = requireNotNull(savedState["titleId"])
    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val catalog = catalogRepository.catalog()
            val title = catalog.title(titleId)
            if (title == null) {
                _state.value = DetailsUiState(notFound = true)
                return@launch
            }
            _state.value = DetailsUiState(
                title = title,
                selectedSeason = title.seasons.firstOrNull() ?: 1,
                moreLikeThis = Recommendations.moreLikeThis(catalog, title),
                playTarget = PlayTarget(title.episodes.firstOrNull(), 0L, null),
            )
        }
    }

    fun selectSeason(season: Int) = _state.update { it.copy(selectedSeason = season) }

    companion object {
        val Factory = viewModelFactory {
            initializer { DetailsViewModel(container.catalogRepository, createSavedStateHandle()) }
        }
    }
}
