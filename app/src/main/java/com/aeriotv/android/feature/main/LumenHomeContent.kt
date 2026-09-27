package com.aeriotv.android.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aeriotv.android.R
import com.aeriotv.android.core.network.DispatcharrVODMovie
import com.aeriotv.android.core.network.DispatcharrVODSeries
import com.aeriotv.android.feature.ondemand.OnDemandViewModel

private val LumenBg = Color(0xFF07090D)
private val LumenPanel = Color(0xFF10141C)
private val LumenPanel2 = Color(0xFF151A23)
private val LumenText = Color(0xFFF7F8FB)
private val LumenMuted = Color(0xFF929BAD)
private val LumenLine = Color(0x17FFFFFF)

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
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

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
    val hero = movies.firstOrNull()

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF07090D), Color(0xFF090C12), Color(0xFF07090D))
            )
        )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 0.dp, bottom = 105.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item { LumenTopBar(onSearch = onOpenSettings) }
            item {
                LumenHero(
                    item = hero,
                    onPlay = { hero?.let { onMovieClick(it.uuid) } },
                )
            }
            if (movies.isNotEmpty()) {
                item {
                    LumenContinueSection(
                        movies = movies.take(2),
                        onMovieClick = onMovieClick,
                    )
                }
                item {
                    LumenCategorySection(
                        onOpenLiveTv = onOpenLiveTv,
                        onOpenMovies = onOpenMovies,
                        onOpenSeries = onOpenSeries,
                    )
                }
                item {
                    LumenMovieSection(
                        movies = movies,
                        onMovieClick = onMovieClick,
                    )
                }
            } else if (series.isNotEmpty()) {
                item {
                    LumenMovieSection(
                        title = "مسلسلات مختارة",
                        movies = emptyList(),
                        series = series,
                        onMovieClick = onMovieClick,
                        onSeriesClick = onSeriesClick,
                    )
                }
            }
        }

        LumenBottomBar(
            onHome = {},
            onLive = onOpenLiveTv,
            onMovies = onOpenMovies,
            onSeries = onOpenSeries,
            onFavorites = onOpenSettings,
        )
    }
}

@Composable
private fun LumenTopBar(onSearch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.eagle_x_logo),
                contentDescription = "Eagle X",
                modifier = Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Eagle X", color = LumenText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Streaming Experience", color = LumenMuted, fontSize = 10.sp)
            }
        }
        LumenCircleButton(Icons.Filled.Search, onSearch)
    }
}

@Composable
private fun LumenCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val focused = remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x12FFFFFF))
            .border(1.dp, if (focused.value) Color.White else LumenLine, CircleShape)
            .onFocusChanged { focused.value = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = LumenText, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun LumenHero(
    item: DispatcharrVODMovie?,
    onPlay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(365.dp)
            .clip(RoundedCornerShape(30.dp))
            .border(1.dp, LumenLine, RoundedCornerShape(30.dp))
    ) {
        if (!item?.posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = item?.posterUrl,
                contentDescription = item?.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(LumenPanel2))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xFA07090D), Color(0xB807090D), Color(0x1A07090D))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x2207090D), Color.Transparent, Color(0xF207090D))
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp).width(320.dp)
        ) {
            Text(
                "Featured tonight",
                color = Color(0xFFC9D4E8),
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "سينما في مكان واحد",
                color = LumenText,
                fontSize = 31.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "اكتشف الأفلام والمسلسلات والقنوات في تجربة مشاهدة هادئة وسريعة.",
                color = Color(0xFFB7BFCE),
                fontSize = 12.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.padding(top = 17.dp)
            ) {
                LumenHeroButton("تشغيل", Icons.Filled.PlayArrow, false, onPlay)
                LumenHeroButton("قائمتي", Icons.Filled.FavoriteBorder, true, {})
            }
        }
    }
}

@Composable
private fun LumenHeroButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    secondary: Boolean,
    onClick: () -> Unit,
) {
    val focused = remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (secondary) Color(0x1AFFFFFF) else Color(0xFFF4F6FA))
            .border(1.dp, if (focused.value) Color.White else LumenLine, RoundedCornerShape(13.dp))
            .onFocusChanged { focused.value = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (secondary) Color.White else Color(0xFF0A0D13), modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (secondary) Color.White else Color(0xFF0A0D13), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LumenContinueSection(
    movies: List<DispatcharrVODMovie>,
    onMovieClick: (String) -> Unit,
) {
    LumenSectionHead("متابعة المشاهدة", "عرض الكل")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 3.dp),
    ) {
        items(movies, key = { it.uuid }) { movie ->
            LumenContinueCard(movie, onMovieClick)
        }
    }
    Spacer(Modifier.height(1.dp))
}

@Composable
private fun LumenContinueCard(movie: DispatcharrVODMovie, onMovieClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .width(245.dp)
            .height(116.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, LumenLine, RoundedCornerShape(20.dp))
            .clickable { onMovieClick(movie.uuid) }
            .focusable()
    ) {
        AsyncImage(
            model = movie.posterUrl,
            contentDescription = movie.displayName,
            modifier = Modifier.fillMaxSize().alpha(0.64f),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xEF080B10), Color(0x80080B10), Color.Transparent))
            )
        )
        Column(Modifier.align(Alignment.CenterStart).padding(15.dp)) {
            Text(movie.displayName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("فيلم · متابعة المشاهدة", color = Color(0xFFAEB7C7), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
            Box(
                Modifier.padding(top = 13.dp).width(205.dp).height(3.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x29FFFFFF))
            ) {
                Box(Modifier.fillMaxWidth(0.62f).fillMaxSize().background(Color(0xFFF2F4F8)))
            }
        }
    }
}

@Composable
private fun LumenCategorySection(
    onOpenLiveTv: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
) {
    LumenSectionHead("اختَر ما تريد", "المكتبة")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        LumenCategory("القنوات", Icons.Filled.LiveTv, Modifier.weight(1f), onOpenLiveTv)
        LumenCategory("الأفلام", Icons.Outlined.Movie, Modifier.weight(1f), onOpenMovies)
        LumenCategory("المسلسلات", Icons.Outlined.Tv, Modifier.weight(1f), onOpenSeries)
    }
}

@Composable
private fun LumenCategory(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val focused = remember { mutableStateOf(false) }
    Column(
        modifier = modifier.height(74.dp).clip(RoundedCornerShape(17.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF161C26), Color(0xFF10141B))))
            .border(1.dp, if (focused.value) Color.White else LumenLine, RoundedCornerShape(17.dp))
            .onFocusChanged { focused.value = it.isFocused }
            .focusable().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LumenMovieSection(
    title: String = "أفلام مختارة",
    movies: List<DispatcharrVODMovie>,
    series: List<DispatcharrVODSeries> = emptyList(),
    onMovieClick: (String) -> Unit,
    onSeriesClick: (Int) -> Unit = {},
) {
    LumenSectionHead(title, "المزيد")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 4.dp)) {
        if (movies.isNotEmpty()) {
            items(movies, key = { it.uuid }) { movie ->
                LumenPosterCard(movie.displayName, movie.posterUrl) { onMovieClick(movie.uuid) }
            }
        } else {
            items(series, key = { it.id }) { item ->
                LumenPosterCard(item.displayName, item.posterUrl) { onSeriesClick(item.id) }
            }
        }
    }
}

@Composable
private fun LumenSectionHead(title: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 29.dp, bottom = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, color = LumenText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(action, color = LumenMuted, fontSize = 11.sp)
    }
}

@Composable
private fun LumenPosterCard(title: String, image: String?, onClick: () -> Unit) {
    val focused = remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.width(132.dp).height(190.dp).clip(RoundedCornerShape(18.dp))
            .background(LumenPanel2)
            .border(1.dp, if (focused.value) Color.White else LumenLine, RoundedCornerShape(18.dp))
            .onFocusChanged { focused.value = it.isFocused }
            .focusable().clickable(onClick = onClick)
    ) {
        if (!image.isNullOrBlank()) {
            AsyncImage(model = image, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE0040609)))))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("فيلم", color = Color(0xFFAEB8C9), fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}
 
@Composable
private fun LumenBottomBar(
    onHome: () -> Unit,
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onFavorites: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(65.dp).clip(RoundedCornerShape(22.dp))
                .background(Color(0xE00F131B))
                .border(1.dp, LumenLine, RoundedCornerShape(22.dp))
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LumenNavItem("الرئيسية", Icons.Filled.Home, true, onHome)
            LumenNavItem("القنوات", Icons.Filled.LiveTv, false, onLive)
            LumenNavItem("الأفلام", Icons.Outlined.Movie, false, onMovies)
            LumenNavItem("المسلسلات", Icons.Outlined.Tv, false, onSeries)
            LumenNavItem("المفضلة", Icons.Filled.FavoriteBorder, false, onFavorites)
        }
    }
}

@Composable
private fun LumenNavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    val focused = remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.width(54.dp).onFocusChanged { focused.value = it.isFocused }.focusable().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(38.dp, 27.dp).clip(RoundedCornerShape(10.dp))
                .background(if (active || focused.value) Color(0x17FFFFFF) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (active || focused.value) Color.White else Color(0xFF7F899B), modifier = Modifier.size(18.dp))
        }
        Text(label, color = if (active || focused.value) Color.White else Color(0xFF7F899B), fontSize = 9.sp)
    }
}
