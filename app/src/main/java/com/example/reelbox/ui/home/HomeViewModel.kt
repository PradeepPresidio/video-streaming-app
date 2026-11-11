package com.example.reelbox.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reelbox.container
import com.example.reelbox.data.MyListRepository
import com.example.reelbox.data.ProgressRepository
import com.example.reelbox.data.catalog.CatalogRepository
import com.example.reelbox.domain.ContinueItem
import com.example.reelbox.domain.HomeContent
import com.example.reelbox.domain.HomeRowsBuilder
import com.example.reelbox.domain.ProgressRules
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(val content: HomeContent, val continueItems: Map<String, ContinueItem>, val myListIds: Set<String>)

class HomeViewModel(
    catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
    private val myListRepository: MyListRepository,
) : ViewModel() {
    val state: StateFlow<HomeUiState?> = combine(
        flow { emit(catalogRepository.catalog()) },
        progressRepository.entries.onStart { emit(emptyList()) },
        myListRepository.titleIds.onStart { emit(emptyList()) },
    ) { catalog, entries, myListIds ->
        val cw = ProgressRules.continueWatching(catalog, entries)
        HomeUiState(
            HomeRowsBuilder.build(
                catalog,
                continueWatching = cw.map { it.title to it.fraction },
                myList = myListIds.mapNotNull(catalog::title),
            ),
            cw.associateBy { it.title.id },
            myListIds.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleMyList(titleId: String) {
        viewModelScope.launch { myListRepository.toggle(titleId) }
    }

    fun removeFromContinueWatching(titleId: String) {
        viewModelScope.launch { progressRepository.removeTitle(titleId) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(container.catalogRepository, container.progressRepository, container.myListRepository) }
        }
    }
}
