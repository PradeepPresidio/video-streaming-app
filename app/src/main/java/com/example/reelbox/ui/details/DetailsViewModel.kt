package com.example.reelbox.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reelbox.container
import com.example.reelbox.data.MyListRepository
import com.example.reelbox.data.ProfileRepository
import com.example.reelbox.data.ProgressRepository
import com.example.reelbox.data.catalog.CatalogRepository
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.domain.PlayTarget
import com.example.reelbox.domain.ProgressRules
import com.example.reelbox.domain.Recommendations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailsUiState(
    val title: Title? = null,
    val notFound: Boolean = false,
    val loading: Boolean = true,
    val selectedSeason: Int = 1,
    val moreLikeThis: List<Title> = emptyList(),
    /** episodeId (or titleId for a movie) -> watched fraction 0..1. */
    val progress: Map<String, Float> = emptyMap(),
    val playTarget: PlayTarget? = null,
    val inMyList: Boolean = false,
)

class DetailsViewModel(
    catalogRepository: CatalogRepository,
    progressRepository: ProgressRepository,
    private val myListRepository: MyListRepository,
    profileRepository: ProfileRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val titleId: String = requireNotNull(savedState["titleId"])
    private val selectedSeason = MutableStateFlow<Int?>(null)

    val state: StateFlow<DetailsUiState> = combine(
        flow { emit(catalogRepository.catalog()) },
        progressRepository.entries.onStart { emit(emptyList()) },
        selectedSeason,
        myListRepository.titleIds.onStart { emit(emptyList()) },
        profileRepository.visibility,
    ) { catalog, entries, season, myList, visible ->
        // A title the profile may not see (Kids) behaves as if it didn't exist.
        val title = catalog.title(titleId)?.takeIf(visible) ?: return@combine DetailsUiState(notFound = true, loading = false)
        val mine = entries.filter { it.titleId == titleId }
        DetailsUiState(
            title = title,
            loading = false,
            selectedSeason = season ?: title.seasons.firstOrNull() ?: 1,
            moreLikeThis = Recommendations.moreLikeThis(catalog, title, visible),
            progress = mine.filter { !it.finished || it.positionMs > 0 }.associate { it.mediaId to it.fraction },
            playTarget = ProgressRules.playTarget(catalog, title, mine),
            inMyList = titleId in myList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsUiState())

    fun selectSeason(season: Int) { selectedSeason.value = season }

    fun toggleMyList() {
        viewModelScope.launch { myListRepository.toggle(titleId) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { DetailsViewModel(container.catalogRepository, container.progressRepository, container.myListRepository, container.profileRepository, createSavedStateHandle()) }
        }
    }
}
