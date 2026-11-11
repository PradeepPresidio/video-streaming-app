package com.example.reelbox.ui.mylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reelbox.container
import com.example.reelbox.data.MyListRepository
import com.example.reelbox.data.ProfileRepository
import com.example.reelbox.data.catalog.CatalogRepository
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.ui.components.CenteredMessage
import com.example.reelbox.ui.components.PosterCard
import com.example.reelbox.ui.components.SimpleTopBar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class MyListViewModel(
    catalogRepository: CatalogRepository,
    myListRepository: MyListRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {
    /** Null while loading; newest first. */
    val titles: StateFlow<List<Title>?> = combine(
        flow { emit(catalogRepository.catalog()) }, myListRepository.titleIds, profileRepository.visibility,
    ) { catalog, ids, visible ->
        ids.mapNotNull(catalog::title).filter(visible)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory { initializer { MyListViewModel(container.catalogRepository, container.myListRepository, container.profileRepository) } }
    }
}

@Composable
fun MyListScreen(onBack: () -> Unit, onOpenTitle: (Title) -> Unit, vm: MyListViewModel = viewModel(factory = MyListViewModel.Factory)) {
    val titles by vm.titles.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().testTag("mylist")) {
        SimpleTopBar("My List", onBack)
        val list = titles
        when {
            list == null -> Unit
            list.isEmpty() -> CenteredMessage("Your list is empty. Tap + My List on any title to add it here.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { t -> PosterCard(t, onClick = { onOpenTitle(t) }, width = 104.dp) }
            }
        }
    }
}
