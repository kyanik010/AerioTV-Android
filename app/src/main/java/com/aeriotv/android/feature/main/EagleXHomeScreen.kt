package com.aeriotv.android.feature.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aeriotv.android.core.preferences.AppLanguage
import com.aeriotv.android.core.preferences.LocalAppLanguage
import com.aeriotv.android.feature.movies.MediaItem
import com.aeriotv.android.feature.ondemand.OnDemandViewModel

private val HomeBackground = Color(0xFF080A0F)
private val HomeSurface = Color(0xD910141C)
private val HomeCard = Color(0xE0151A23)
private val HomeText = Color(0xFFF7F9FC)
private val HomeMuted = Color(0xFFA7AFBF)
private val HomeAccent = Color(0xFF4FC8E8)

@Composable
fun EagleXHomeScreen(
    onSelectTab: (AppTab) -> Unit,
    onPlayMovie: (String) -> Unit,
    onPlaySeries: (Int) -> Unit,
    username: String?,
    expiresAt: String?,
    viewModel: OnDemandViewModel,
) {
    val language = LocalAppLanguage.current
    val moviesState by viewModel.library(true).collectAsState()
    val seriesState by viewModel.library(false).collectAsState()

    val movies = remember(moviesState.items) {
        moviesState.items.filter { !it.posterUrl.isNullOrBlank() }.distinctBy { it.key }
    }
    val series = remember(seriesState.items) {
        seriesState.items.filter { !it.posterUrl.isNullOrBlank() }.distinctBy { it.key }
    }
    val featured = remember(movies, series) {
        (movies + series).take(8)
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(featured.size) {
        selectedIndex = selectedIndex.coerceIn(0, (featured.size - 1).coerceAtLeast(0))
    }
    val hero = featured.getOrNull(selectedIndex)

    Box(Modifier.fillMaxSize().background(HomeBackground)) {
        hero?.posterUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Full-bleed cinematic treatment: artwork reaches the screen edges.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to HomeBackground.copy(alpha = 0.98f),
                    0.38f to HomeBackground.copy(alpha = 0.90f),
                    0.68f to HomeBackground.copy(alpha = 0.52f),
                    1f to HomeBackground.copy(alpha = 0.22f),
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to HomeBackground.copy(alpha = 0.58f),
                    0.32f to Color.Transparent,
                    0.70f to HomeBackground.copy(alpha = 0.28f),
                    1f to HomeBackground.copy(alpha = 0.98f),
                )
            )
        )

        Column(
            Modifier.fillMaxSize().padding(horizontal = 52.dp, vertical = 28.dp)
        ) {
            HomeTopBar(language = language, onSelectTab = onSelectTab)

            Spacer(Modifier.height(18.dp))

            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                hero?.let {
                    Text(
                        text = if (language == AppLanguage.ARABIC) "مميز" else "FEATURED",
                        color = HomeAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = it.title,
                        color = HomeText,
                        fontSize = 42.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 680.dp),
                    )
                    val meta = listOfNotNull(
                        it.year?.toString(),
                        it.rating?.takeIf { rating -> rating.isNotBlank() }
                    ).joinToString("  •  ")
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(9.dp))
                        Text(meta, color = HomeMuted, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeActionButton(
                            label = if (language == AppLanguage.ARABIC) "تشغيل الآن" else "Play now",
                            accent = true,
                            onClick = {
                                it.movieUuid?.let(onPlayMovie)
                                    ?: it.seriesId?.let(onPlaySeries)
                            },
                        )
                        HomeActionButton(
                            label = if (language == AppLanguage.ARABIC) "المزيد" else "Browse library",
                            accent = false,
                            onClick = {
                                if (it.movieUuid != null) onSelectTab(AppTab.Movies)
                                else if (it.seriesId != null) onSelectTab(AppTab.TVShows)
                            },
                        )
                    }
                }
            }

            if (movies.isNotEmpty()) {
                HomeMediaRail(
                    title = if (language == AppLanguage.ARABIC) "أفلام" else "Movies",
                    items = movies.take(12),
                    onFocus = { item ->
                        val index = featured.indexOfFirst { it.key == item.key }
                        if (index >= 0) selectedIndex = index
                    },
                    onClick = { item -> item.movieUuid?.let(onPlayMovie) },
                )
                Spacer(Modifier.height(20.dp))
            }
            if (series.isNotEmpty()) {
                HomeMediaRail(
                    title = if (language == AppLanguage.ARABIC) "مسلسلات" else "Series",
                    items = series.take(12),
                    onFocus = { item ->
                        val index = featured.indexOfFirst { it.key == item.key }
                        if (index >= 0) selectedIndex = index
                    },
                    onClick = { item -> item.seriesId?.let(onPlaySeries) },
                )
            }

            Spacer(Modifier.height(8.dp))
            AccountFooter(language = language, username = username, expiresAt = expiresAt)
        }
    }
}

@Composable
private fun HomeTopBar(
    language: AppLanguage,
    onSelectTab: (AppTab) -> Unit,
) {
    val arabic = language == AppLanguage.ARABIC
    val items = listOf(
        Triple(if (arabic) "القنوات" else "Live TV", Icons.Filled.LiveTv, AppTab.LiveTV),
        Triple(if (arabic) "الأفلام" else "Movies", Icons.Filled.Movie, AppTab.Movies),
        Triple(if (arabic) "المسلسلات" else "Series", Icons.Filled.Tv, AppTab.TVShows),
        Triple(if (arabic) "المفضلة" else "Favorites", Icons.Filled.Favorite, AppTab.Favorites),
        Triple(if (arabic) "الإعدادات" else "Settings", Icons.Filled.Settings, AppTab.Settings),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(150.dp)) {
            Text("EAGLE", color = HomeText, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("X", color = HomeAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (label, icon, tab) ->
                HomeTopNavItem(icon = icon, label = label, onClick = { onSelectTab(tab) })
            }
        }
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = if (arabic) "بحث" else "Search",
            tint = HomeMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun HomeTopNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(120),
        label = "homeTopNavScale",
    )
    Row(
        Modifier
            .padding(horizontal = 5.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (focused) HomeAccent.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                1.dp,
                if (focused) HomeAccent.copy(alpha = 0.48f) else Color.Transparent,
                RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = if (focused) HomeAccent else HomeMuted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = if (focused) HomeText else HomeMuted, fontSize = 13.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun HomeActionButton(label: String, accent: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (accent) HomeAccent else HomeSurface)
            .border(1.dp, if (focused) HomeText.copy(alpha = 0.65f) else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 19.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (accent) Color(0xFF061015) else HomeText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HomeMediaRail(
    title: String,
    items: List<MediaItem>,
    onFocus: (MediaItem) -> Unit,
    onClick: (MediaItem) -> Unit,
) {
    Column {
        Text(title, color = HomeText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(9.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            itemsIndexed(items, key = { _, item -> item.key }) { _, item ->
                HomePosterCard(item = item, onFocus = { onFocus(item) }, onClick = { onClick(item) })
            }
        }
    }
}

@Composable
private fun HomePosterCard(
    item: MediaItem,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        animationSpec = tween(120),
        label = "homePosterFocus",
    )
    Box(
        Modifier
            .size(width = 118.dp, height = 174.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, if (focused) HomeAccent.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .focusable()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (focused) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, HomeAccent.copy(alpha = 0.10f)))))
        }
    }
}

@Composable
private fun AccountFooter(
    language: AppLanguage,
    username: String?,
    expiresAt: String?,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = username?.takeIf { it.isNotBlank() } ?: "Guest",
            color = HomeMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 240.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text("•", color = HomeMuted.copy(alpha = 0.45f), fontSize = 11.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (language == AppLanguage.ARABIC) "الصلاحية" else "Expires",
            color = HomeMuted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = expiresAt?.takeIf { it.isNotBlank() } ?: "—",
            color = HomeText,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
