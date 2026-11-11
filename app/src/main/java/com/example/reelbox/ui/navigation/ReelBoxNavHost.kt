package com.example.reelbox.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.reelbox.data.catalog.Title
import com.example.reelbox.player.PlayerScreen
import com.example.reelbox.ui.downloads.DownloadsScreen
import com.example.reelbox.ui.home.HomeScreen
import com.example.reelbox.ui.profile.ProfileScreen
import com.example.reelbox.ui.search.SearchScreen
import com.example.reelbox.ui.settings.CreditsScreen
import com.example.reelbox.ui.settings.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.SEARCH, "Search", Icons.Filled.Search),
    Tab(Routes.DOWNLOADS, "Downloads", Icons.Filled.Download),
    Tab(Routes.PROFILE, "Profile", Icons.Filled.AccountCircle),
)

@Composable
fun ReelBoxNavHost(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = { if (route in Routes.tabs) BottomBar(nav, route) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(if (route == Routes.PLAYER) androidx.compose.foundation.layout.PaddingValues() else padding)) {
            NavHost(nav, startDestination = Routes.HOME) {
                // Until the details page exists (R3), a poster starts playback directly.
                val play: (Title) -> Unit = { t -> nav.navigate(Routes.player(t.id)) }
                composable(Routes.HOME) { HomeScreen(onOpenTitle = play, onPlay = play) }
                composable(Routes.SEARCH) { SearchScreen() }
                composable(Routes.DOWNLOADS) { DownloadsScreen() }
                composable(Routes.PROFILE) { ProfileScreen(onSettings = { nav.navigate(Routes.SETTINGS) }) }
                composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onCredits = { nav.navigate(Routes.CREDITS) }) }
                composable(Routes.CREDITS) { CreditsScreen(onBack = { nav.popBackStack() }) }
                composable(
                    Routes.PLAYER,
                    arguments = listOf(
                        navArgument("titleId") { type = NavType.StringType },
                        navArgument("episodeId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("local") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { PlayerScreen() }
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, null) },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("tab:${tab.route}"),
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            )
        }
    }
}
