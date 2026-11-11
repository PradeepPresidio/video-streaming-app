package com.example.reelbox

import android.content.Context
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.reelbox.data.MyListRepository
import com.example.reelbox.data.ProfileRepository
import com.example.reelbox.data.ProgressRepository
import com.example.reelbox.data.catalog.CatalogRepository
import com.example.reelbox.data.db.ReelBoxDatabase
import com.example.reelbox.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; one per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Outlives screens: used for writes that must finish after a ViewModel is cleared (e.g. last progress save). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: ReelBoxDatabase by lazy { ReelBoxDatabase.create(appContext) }
    val settings = SettingsStore(appContext)
    val catalogRepository = CatalogRepository(appContext)
    val profileRepository by lazy { ProfileRepository(database.profiles(), settings) }
    val progressRepository by lazy { ProgressRepository(database.progress(), profileRepository, catalogRepository) }
    val myListRepository by lazy { MyListRepository(database.myList(), profileRepository) }

    init {
        appScope.launch { profileRepository.ensureDefaultProfile() }
    }
}

/** Access the container from a ViewModel factory initializer. */
val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as ReelBoxApp).container
