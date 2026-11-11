package com.example.reelbox.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.reelbox.container
import com.example.reelbox.data.catalog.CatalogRepository
import com.example.reelbox.domain.HomeContent
import com.example.reelbox.domain.HomeRowsBuilder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(catalogRepository: CatalogRepository) : ViewModel() {
    val content: StateFlow<HomeContent?> = flow {
        emit(HomeRowsBuilder.build(catalogRepository.catalog()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory = viewModelFactory { initializer { HomeViewModel(container.catalogRepository) } }
    }
}
