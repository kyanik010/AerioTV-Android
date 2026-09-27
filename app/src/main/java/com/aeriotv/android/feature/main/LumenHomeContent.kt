package com.aeriotv.android.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aeriotv.android.core.network.DispatcharrVODMovie
import com.aeriotv.android.core.network.DispatcharrVODSeries
import com.aeriotv.android.feature.ondemand.OnDemandViewModel

/**
 * Cinematic Home based on Lumen's Android TV Home structure:
 * full-bleed spotlight hero followed by horizontal media shelves.
 *
 * This is intentionally a UI layer. AerioTV's existing Xtream/Dispatcharr
 * catalog, player, activation and detail routes remain the source of truth.
 */
@Composable
fun LumenHomeContent(
    onMovieClick: (String) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onOpenLiveTv: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: OnDemandViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.ensureLoaded()
    }

    val movies = remember(state.catalogMovies) {
        state.catalogMovies.values
            .filter { it.displayName.isNotBlank() && !it.posterUrl.isNullOrBlank() }
            .sortedByDescending { it.year ?: 0 }
            .take(24)
    }
    val series = remember(state.catalogSeries) {
        state.catalogSeries.values
            .filter { it.displayName.isNotBlank() && !it.posterUrl.isNullOrBlank() }
            .sortedByDescending { it.year ?: 0 }
            .take(24)
    }
    val heroItems = remember(movies, series) {
        buildList {
            addAll(movies.take(6).map { HeroItem.Movie(it) })
            if (isEmpty()) addAll(series.take(6).map { HeroItem.Series(it) })
        }
    }
    val hero = heroItems.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080A0F)),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                LumenHero(
                    item = hero,
                    onMovieClick = onMovieClick,
                    onSeriesClick = onSeriesClick,
                )
            }

            if (movies.isNotEmpty()) {
                item {
                    LumenShelf(
                        title = "Movies",
                        items = movies,
                        onClick = onMovieClick,
                    )
                }
            }

            if (series.isNotEmpty()) {
                item {
                    LumenSeriesShelf(
                        title = "TV Shows",
                        items = series,
                        onClick = onSeriesClick,
                    )
                }
            }
        }

        LumenHomeNavigation(
            onOpenLiveTv = onOpenLiveTv,
            onOpenMovies = onOpenMovies,
            onOpenSeries = onOpenSeries,
            onOpenSettings = onOpenSettings,
        )
    }
}

private sealed interface HeroItem {
    data class Movie(val value: DispatcharrVODMovie) : HeroItem
    data class Series(val value: DispatcharrVODSeries) : HeroItem
}

@Composable
private fun LumenHero(
    item: HeroItem?,
    onMovieClick: (String) -> Unit,
    onSeriesClick: (Int) -> Unit,
) {
    val title = when (item) {
        is HeroItem.Movie -> item.value.displayName
        is HeroItem.Series -> item.value.displayName
        null -> "AerioTV"
    }
    val image = when (item) {
        is HeroItem.Movie -> item.value.posterUrl
        is HeroItem.Series -> item.value.posterUrl
        null -> null
    }
    val plot = when (item) {
        is HeroItem.Movie -> item.value.plot
        is HeroItem.Series -> item.value.plot
        null -> "Your entertainment, in one cinematic home."
    }
    val year = when (item) {
        is HeroItem.Movie -> item.value.year
        is HeroItem.Series -> item.value.year
        null -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(470.dp)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
    ) {
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF151A23)))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF080A0F),
                            Color(0xC9080A0F),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xE6080A0F)),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(560.dp)
                .padding(start = 42.dp, end = 24.dp, bottom = 34.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (year != null) {
                Text(
                    text = year.toString(),
                    color = Color(0xFFA7AFBF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            if (!plot.isNullOrBlank()) {
                Text(
                    text = plot,
                    color = Color(0xFFA7AFBF),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }

            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LumenAction(
                    icon = Icons.Filled.PlayArrow,
                    text = "Play",
                    onClick = {
                        when (item) {
                            is HeroItem.Movie -> onMovieClick(item.value.uuid)
                            is HeroItem.Series -> onSeriesClick(item.value.id)
                            null -> Unit
                        }
                    },
                )
                LumenAction(
                    icon = Icons.Filled.FavoriteBorder,
                    text = "My List",
                    onClick = {},
                    secondary = true,
                )
            }
        }
    }
}

@Composable
private fun LumenAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    secondary: Boolean = false,
) {
    val focused = remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (secondary) Color(0xCC151A23) else Color.White)
            .border(
                1.dp,
                if (focused.value) Color(0xFF4FC8E8) else Color.Transparent,
                RoundedCornerShape(13.dp),
            )
            .clickable(onClick = onClick)
            .onFocusChanged { focused.value = it.isFocused }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = text,
                tint = if (secondary) Color.White else Color.Black,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = text,
                color = if (secondary) Color.White else Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LumenShelf(
    title: String,
    items: List<DispatcharrVODMovie>,
    onClick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { it.uuid }) { movie ->
                LumenPosterCard(
                    title = movie.displayName,
                    image = movie.posterUrl,
                    onClick = { onClick(movie.uuid) },
                )
            }
        }
    }
}

@Composable
private fun LumenSeriesShelf(
    title: String,
    items: List<DispatcharrVODSeries>,
    onClick: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items, key = { it.id }) { series ->
                LumenPosterCard(
                    title = series.displayName,
                    image = series.posterUrl,
                    onClick = { onClick(series.id) },
                )
            }
        }
    }
}

@Composable
private fun LumenPosterCard(
    title: String,
    image: String?,
    onClick: () -> Unit,
) {
    var focused = false
    Column(
        modifier = Modifier
            .width(132.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .width(132.dp)
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    2.dp,
                    if (focused) Color(0xFF4FC8E8) else Color.Transparent,
                    RoundedCornerShape(12.dp),
                ),
        ) {
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF151A23)))
            }
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
private fun BoxScope.LumenHomeNavigation(
    onOpenLiveTv: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 22.dp, top = 18.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xB010141C))
            .border(1.dp, Color(0x2AFFFFFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 7.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LumenNavButton("Live TV", Icons.Filled.LiveTv, onOpenLiveTv)
        LumenNavButton("Movies", Icons.Outlined.Movie, onOpenMovies)
        LumenNavButton("Series", Icons.Outlined.Tv, onOpenSeries)
        LumenNavButton("Settings", Icons.Filled.Settings, onOpenSettings)
    }
}

@Composable
private fun LumenNavButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    var focused = false
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused.value) Color(0x334FC8E8) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(19.dp))
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
