package com.aeriotv.android.feature.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppTab(
    val id: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
) {
    LiveTV("livetv", "Live TV", Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
    Favorites("favorites", "Favorites", Icons.Filled.Favorite, Icons.Outlined.Favorite),
    DVR("dvr", "DVR", Icons.Filled.RadioButtonChecked, Icons.Outlined.RadioButtonChecked),
    OnDemand("ondemand", "On Demand", Icons.Filled.OndemandVideo, Icons.Outlined.OndemandVideo),
    Movies("movies", "Movies", Icons.Filled.Movie, Icons.Outlined.Movie),
    TVShows("tvshows", "TV Shows", Icons.Filled.Tv, Icons.Outlined.Tv),
    /** Persistent global audio library configured independently from the IPTV account. */
    Audio("audio", "Audio", Icons.Filled.MusicNote, Icons.Outlined.MusicNote),
    Settings("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    Search("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
}
