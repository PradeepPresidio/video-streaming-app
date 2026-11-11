package com.example.reelbox

import android.content.Context
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.example.reelbox.data.catalog.CatalogRepository

/** Manual dependency container; one per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val catalogRepository = CatalogRepository(appContext)
}

/** Access the container from a ViewModel factory initializer. */
val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as ReelBoxApp).container
