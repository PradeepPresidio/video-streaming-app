package com.example.reelbox.ui.navigation

import android.net.Uri

object Routes {
    const val PROFILES = "profiles"
    const val PROFILE_EDIT = "profileEdit?id={id}"
    const val HOME = "home"
    const val SEARCH = "search"
    const val DOWNLOADS = "downloads"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val CREDITS = "credits"
    const val MY_LIST = "mylist"
    const val DETAILS = "details/{titleId}"
    const val PLAYER = "player/{titleId}?episodeId={episodeId}&local={local}"

    val tabs = listOf(HOME, SEARCH, DOWNLOADS, PROFILE)

    fun profileEdit(id: Long = 0) = "profileEdit?id=$id"
    fun details(titleId: String) = "details/${Uri.encode(titleId)}"
    fun player(titleId: String, episodeId: String? = null, local: Boolean = false) =
        "player/${Uri.encode(titleId)}?episodeId=${episodeId?.let(Uri::encode) ?: ""}&local=$local"
}
