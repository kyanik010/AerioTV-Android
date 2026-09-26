package com.aeriotv.android.feature.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aeriotv.android.core.data.db.entity.PlaylistEntity
import com.aeriotv.android.core.preferences.AppLanguage
import com.aeriotv.android.core.preferences.LocalAppLanguage
import com.aeriotv.android.feature.ondemand.OnDemandViewModel
import com.aeriotv.android.feature.movies.MediaItem
import com.aeriotv.android.feature.playlist.PlaylistViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

private val HomeBackgroundTop = Color(0xFF1C2433)
private val HomeBackgroundBottom = Color(0xFF0E1520)
private val HomeAccountBackground = Color(0xFF141E2D)
private val HomeAccent = Color(0xFF4FC8E8)

@Composable
fun AerioHomeScreen(
    playlistViewModel: PlaylistViewModel,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlistState by playlistViewModel.state.collectAsState()
    val onDemandVm: OnDemandViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val movies by onDemandVm.library(true).collectAsState()
    val series by onDemandVm.library(false).collectAsState()
    val language = LocalAppLanguage.current

    val candidates = remember(movies.items.size, series.items.size) {
        (movies.items + series.items)
            .filter { !it.posterUrl.isNullOrBlank() }
            .distinctBy { it.key }
            .shuffled(Random(System.nanoTime()))
            .take(18)
    }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var clock by remember { mutableStateOf(SimpleDateFormat("hh:mm a", Locale.US).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
            delay(1_000L)
        }
    }

    val posterWindow = remember(candidates, selectedIndex) {
        if (candidates.isEmpty()) emptyList()
        else (-2..2).map { offset -> candidates[(selectedIndex + offset).floorMod(candidates.size)] to offset }
    }
    val centerPosterFocus = remember { FocusRequester() }
    val navFocus = remember { List(4) { FocusRequester() } }

    Box(modifier = modifier.fillMaxSize()) {
        CinematicHomeBackground()
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.68f)
                    .padding(horizontal = 56.dp, vertical = 36.dp),
            ) {
                HomeNavigation(
                    language = language,
                    requesters = navFocus,
                    onSelectTab = onSelectTab,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .focusGroup(),
                    downTarget = centerPosterFocus,
                )
                PosterCarousel(
                    items = posterWindow,
                    selectedIndex = selectedIndex,
                    onSelected = { selectedIndex = it },
                    centerFocus = centerPosterFocus,
                    navFocus = navFocus[0],
                    onPlay = { item ->
                        when {
                            item.movieUuid != null -> onSelectTab(AppTab.Movies)
                            item.seriesId != null -> onSelectTab(AppTab.TVShows)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(start = 250.dp, end = 30.dp),
                    itemCount = candidates.size,
                )
            }
            HomeAccountSection(
                playlist = playlistState.playlist,
                clock = clock,
                language = language,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.32f),
            )
        }
    }
}

@Composable
private fun HomeNavigation(
    language: AppLanguage,
    requesters: List<FocusRequester>,
    downTarget: FocusRequester,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        Triple(Icons.Outlined.Tv, if (language == AppLanguage.ENGLISH) "Channels" else "القنوات", AppTab.LiveTV),
        Triple(Icons.Outlined.Movie, if (language == AppLanguage.ENGLISH) "Movies" else "الأفلام", AppTab.Movies),
        Triple(Icons.Outlined.PlayCircleOutline, if (language == AppLanguage.ENGLISH) "Series" else "المسلسلات", AppTab.TVShows),
        Triple(Icons.Outlined.FavoriteBorder, if (language == AppLanguage.ENGLISH) "Favorites" else "المفضلة", AppTab.Favorites),
        Triple(Icons.Outlined.Settings, if (language == AppLanguage.ENGLISH) "Settings" else "الإعدادات", AppTab.Settings),
    )
    Column(
        modifier = modifier.width(196.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEachIndexed { index, (icon, label, tab) ->
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .focusRequester(requesters[index])
                    .focusProperties { down = downTarget }
                    .clickable { onSelectTab(tab) }
                    .focusable()
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = if (focused) 0.16f else 0.075f))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = if (focused) 0.32f else 0.13f),
                        RoundedCornerShape(12.dp),
                    )
                    .graphicsLayer {
                        scaleX = if (focused) 1.03f else 1f
                        scaleY = if (focused) 1.03f else 1f
                    }
                    .height(56.dp)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                if (focused) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .width(3.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(HomeAccent.copy(alpha = .90f))
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterCarousel(
    items: List<Pair<MediaItem, Int>>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    centerFocus: FocusRequester,
    navFocus: FocusRequester,
    onPlay: (MediaItem) -> Unit,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("", color = Color.Transparent)
        }
        return
    }
    val requesters = remember(items.map { it.first.key }) { items.map { FocusRequester() } }
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HomeAccent.copy(alpha = .10f), Color.Transparent),
                    center = Offset(size.width * .50f, size.height * .50f),
                    radius = size.height * .52f,
                ),
                radius = size.height * .52f,
                center = Offset(size.width * .50f, size.height * .50f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy((-18).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { visibleIndex, (item, offset) ->
                val distance = abs(offset)
                val scale = when (distance) { 0 -> 1f; 1 -> .78f; else -> .65f }
                val alpha = when (distance) { 0 -> 1f; 1 -> .61f; else -> .42f }
                val rotation = when {
                    offset < 0 -> -7f * distance
                    offset > 0 -> 7f * distance
                    else -> 0f
                }
                val requester = requesters[visibleIndex]
                Box(
                    modifier = Modifier
                        .width(232.dp)
                        .height(332.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            rotationZ = rotation
                            this.alpha = alpha
                            shadowElevation = if (distance == 0) 28.dp.toPx() else 8.dp.toPx()
                            shape = RoundedCornerShape(14.dp)
                            clip = true
                        }
                        .focusRequester(requester)
                        .focusProperties {
                            if (visibleIndex > 0) left = requesters[visibleIndex - 1]
                            if (visibleIndex < items.lastIndex) right = requesters[visibleIndex + 1]
                            up = navFocus
                        }
                        .onFocusChanged {
                            if (it.isFocused) onSelected((selectedIndex + offset).floorMod(itemCount))
                        }
                        .clickable { onPlay(item) }
                        .focusable()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF172131))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = if (distance == 0) .20f else .06f),
                            RoundedCornerShape(14.dp),
                        )
                        .then(if (distance == 0) Modifier.focusRequester(centerFocus) else Modifier),
                ) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = .24f),
                                    Color.Black.copy(alpha = .64f)
                                )
                            )
                        )
                    )
                    if (distance == 0) {
                        Text(
                            item.title,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 18.dp),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAccountSection(
    playlist: PlaylistEntity?,
    clock: String,
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    val parts = clock.split(' ')
    val time = parts.firstOrNull().orEmpty()
    val amPm = parts.getOrNull(1).orEmpty()
    Column(
        modifier = modifier
            .background(HomeAccountBackground)
            .border(1.dp, Color.White.copy(alpha = .08f))
            .padding(horizontal = 48.dp, vertical = 24.dp),
    ) {
        Text(
            if (language == AppLanguage.ENGLISH) "Account & Info" else "الحساب والمعلومات",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = .035f))
                    .border(2.dp, HomeAccent.copy(alpha = .72f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = HomeAccent, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (language == AppLanguage.ENGLISH) "Username" else "اسم المستخدم", color = Color.White.copy(alpha = .55f), fontSize = 14.sp)
                Text(
                    playlist?.username?.takeIf { it.isNotBlank() } ?: "Guest_User",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (language == AppLanguage.ENGLISH) "Expiry date : —" else "تاريخ انتهاء الصلاحية : —",
                    color = Color.White.copy(alpha = .55f),
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(amPm, color = Color.White.copy(alpha = .45f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

@Composable
private fun CinematicHomeBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(HomeBackgroundTop, HomeBackgroundBottom)))

        val upperGlow = Brush.radialGradient(
            colors = listOf(HomeAccent.copy(alpha = .095f), Color.Transparent),
            center = Offset(size.width * .83f, size.height * .14f),
            radius = size.width * .34f,
        )
        drawRect(upperGlow)

        val centerGlow = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = .035f), Color.Transparent),
            center = Offset(size.width * .55f, size.height * .39f),
            radius = size.width * .30f,
        )
        drawRect(centerGlow)

        val random = Random(2209)
        repeat(135) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height * .69f
            val r = random.nextFloat() * 1.35f + .25f
            drawCircle(Color.White.copy(alpha = random.nextFloat() * .095f), r, Offset(x, y))
        }

        val path = Path().apply {
            moveTo(size.width * .62f, size.height * .015f)
            cubicTo(
                size.width * .78f, size.height * .09f,
                size.width * .89f, size.height * .22f,
                size.width * 1.01f, size.height * .40f
            )
        }
        drawPath(path, Color.White.copy(alpha = .055f), style = Stroke(width = 1.2f))

        val path2 = Path().apply {
            moveTo(size.width * .66f, size.height * .02f)
            cubicTo(
                size.width * .83f, size.height * .14f,
                size.width * .93f, size.height * .29f,
                size.width * 1.03f, size.height * .49f
            )
        }
        drawPath(path2, HomeAccent.copy(alpha = .075f), style = Stroke(width = 1.6f))

        val vignette = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = .22f)),
            center = Offset(size.width * .50f, size.height * .39f),
            radius = size.width * .75f,
        )
        drawRect(vignette)
    }
}

private fun Int.floorMod(size: Int): Int {
    if (size <= 0) return 0
    val r = this % size
    return if (r < 0) r + size else r
}
