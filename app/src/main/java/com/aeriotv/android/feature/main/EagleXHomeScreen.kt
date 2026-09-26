package com.aeriotv.android.feature.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aeriotv.android.feature.movies.MediaItem
import com.aeriotv.android.feature.ondemand.OnDemandViewModel
import com.aeriotv.android.core.preferences.AppLanguage
import com.aeriotv.android.core.preferences.LocalAppLanguage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val HomeBackground = Color(0xFF080A0F)
private val HomeSurface = Color(0xCC10141C)
private val HomeCard = Color(0xD9151A23)
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
    val movies by viewModel.library(true).collectAsState()
    val series by viewModel.library(false).collectAsState()

    val media = remember(movies.items, series.items) {
        (movies.items + series.items)
            .filter { it.posterUrl?.isNotBlank() == true }
            .distinctBy { it.key }
            .take(5)
    }

    var selectedIndex by remember { mutableIntStateOf(2.coerceAtMost((media.size - 1).coerceAtLeast(0))) }
    val hero = media.getOrNull(selectedIndex)
    LaunchedEffect(media.size) {
        if (media.isNotEmpty()) selectedIndex = 2.coerceAtMost(media.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
    ) {
        CinematicHomeBackground(selectedIndex = selectedIndex, itemCount = media.size)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 34.dp, end = 42.dp, top = 30.dp, bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeClock()
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeNavigationRail(
                    language = language,
                    onSelectTab = onSelectTab,
                    modifier = Modifier.width(196.dp),
                )

                Spacer(Modifier.width(38.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (media.isNotEmpty()) {
                        HomeCarousel(
                            items = media,
                            selectedIndex = selectedIndex,
                            onFocused = { selectedIndex = it },
                            onClick = { item ->
                                item.movieUuid?.let(onPlayMovie)
                                    ?: item.seriesId?.let(onPlaySeries)
                            },
                        )

                        Spacer(Modifier.height(18.dp))

                        hero?.let {
                            Text(
                                text = it.title,
                                color = HomeText,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 560.dp),
                            )
                            val meta = listOfNotNull(it.year?.toString(), it.rating?.takeIf { r -> r.isNotBlank() }).joinToString("  •  ")
                            if (meta.isNotBlank()) {
                                Text(
                                    text = meta,
                                    color = HomeMuted,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (language == AppLanguage.ARABIC) "جاري تحميل مكتبة الأفلام والمسلسلات" else "Loading your Movies & Series library",
                            color = HomeMuted,
                            fontSize = 18.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
        }

        AccountInfoCard(
            language = language,
            username = username,
            expiresAt = expiresAt,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 34.dp, bottom = 28.dp)
                .width(196.dp),
        )
    }
}

@Composable
private fun HomeNavigationRail(
    language: AppLanguage,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = if (language == AppLanguage.ARABIC) {
        listOf("القنوات", "الأفلام", "المسلسلات", "المفضلة", "الإعدادات")
    } else {
        listOf("Channels", "Movies", "TV Shows", "Favorites", "Settings")
    }
    val icons = listOf(
        Icons.Filled.LiveTv,
        Icons.Filled.Movie,
        Icons.Filled.Tv,
        Icons.Filled.Favorite,
        Icons.Filled.Settings,
    )
    val tabs = listOf(
        AppTab.LiveTV,
        AppTab.Movies,
        AppTab.TVShows,
        AppTab.Favorites,
        AppTab.Settings,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        labels.forEachIndexed { index, label ->
            HomeNavButton(
                icon = icons[index],
                label = label,
                onClick = { onSelectTab(tabs[index]) },
            )
        }
    }
}

@Composable
private fun HomeNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = tween(120),
        label = "homeNavScale",
    )
    val borderColor = if (focused) HomeAccent.copy(alpha = 0.70f) else Color.Transparent
    val background = if (focused) HomeAccent.copy(alpha = 0.11f) else HomeSurface.copy(alpha = 0.78f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    if (focused) {
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                listOf(HomeAccent.copy(alpha = 0.12f), Color.Transparent)
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
            }
            .then(Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused) HomeAccent else HomeMuted,
            modifier = Modifier
                .padding(start = 18.dp)
                .size(22.dp),
        )
        Text(
            text = label,
            color = HomeText,
            fontSize = 16.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

@Composable
private fun HomeCarousel(
    items: List<MediaItem>,
    selectedIndex: Int,
    onFocused: (Int) -> Unit,
    onClick: (MediaItem) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val width = if (selected) 232.dp else if (kotlin.math.abs(index - selectedIndex) == 1) 184.dp else 150.dp
            val height = if (selected) 332.dp else if (kotlin.math.abs(index - selectedIndex) == 1) 276.dp else 225.dp
            val alpha = if (selected) 1f else if (kotlin.math.abs(index - selectedIndex) == 1) 0.72f else 0.45f
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.985f,
                animationSpec = tween(180),
                label = "homePosterScale",
            )
            val requester = remember(index) { FocusRequester() }

            Column(
                modifier = Modifier
                    .width(width)
                    .onFocusChanged { if (it.isFocused) onFocused(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width, height)
                        .graphicsLayer {
                            this.alpha = alpha
                            this.scaleX = scale
                            this.scaleY = scale
                            this.rotationY = if (index < selectedIndex) 4f else if (index > selectedIndex) -4f else 0f
                            this.cameraDistance = 18f
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (selected) 1.5.dp else 0.dp,
                            color = if (selected) HomeAccent.copy(alpha = 0.55f) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp),
                        )
                        .focusRequester(requester)
                        .focusable()
                        .clickable { onClick(item) },
                ) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            HomeAccent.copy(alpha = 0.08f),
                                            Color.Transparent,
                                        ),
                                        center = Offset(116.dp.toPx(), 166.dp.toPx()),
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountInfoCard(
    language: AppLanguage,
    username: String?,
    expiresAt: String?,
    modifier: Modifier = Modifier,
) {
    val arabic = language == AppLanguage.ARABIC
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HomeCard.copy(alpha = 0.82f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = if (arabic) "معلومات الحساب" else "Account & Info",
            color = HomeText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = username?.takeIf { it.isNotBlank() } ?: "Guest_User_A1B2",
            color = HomeText,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (arabic) "تاريخ انتهاء الصلاحية" else "Expiration",
            color = HomeMuted,
            fontSize = 11.sp,
        )
        Text(
            text = expiresAt?.takeIf { it.isNotBlank() } ?: "—",
            color = HomeText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeClock() {
    var time by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30_000L)
        }
    }
    Text(
        text = time,
        color = HomeText,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun CinematicHomeBackground(
    selectedIndex: Int,
    itemCount: Int,
) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val min = size.minDimension

        // 1. Dark cinematic base: never a flat surface.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF111821),
                0.20f to Color(0xFF0C1118),
                0.52f to Color(0xFF080B11),
                0.78f to Color(0xFF06080D),
                1f to Color(0xFF030509),
            )
        )

        // 2. Very soft ambient cyan in the upper-right corner.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(HomeAccent.copy(alpha = 0.070f), HomeAccent.copy(alpha = 0.026f), Color.Transparent),
                center = Offset(w * 0.91f, h * 0.06f),
                radius = min * 0.52f,
            ),
        )

        // 3. Hero ambient light. Its horizontal position follows the active
        // carousel item rather than being a permanent neon spot.
        val heroX = when {
            itemCount <= 1 -> w * 0.55f
            else -> {
                val first = w * 0.48f
                val last = w * 0.77f
                first + (last - first) * (selectedIndex.coerceIn(0, itemCount - 1).toFloat() / (itemCount - 1).toFloat())
            }
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    HomeAccent.copy(alpha = 0.055f),
                    HomeAccent.copy(alpha = 0.020f),
                    Color.Transparent,
                ),
                center = Offset(heroX, h * 0.47f),
                radius = min * 0.43f,
            ),
        )

        // 4. Additional neutral depth behind the content boundary.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.06f),
                    Color.Black.copy(alpha = 0.16f),
                ),
                startY = h * 0.55f,
                endY = h,
            )
        )

        // 5. Extremely subtle dust / particles. Deterministic positions avoid
        // visual movement and keep the background calm.
        val particleCount = 52
        repeat(particleCount) { i ->
            val x = (((i * 193 + 47) % 997) / 997f) * w
            val y = (((i * 317 + 113) % 991) / 991f) * h
            val radius = when {
                i % 17 == 0 -> 1.25f
                i % 7 == 0 -> 0.95f
                else -> 0.60f
            }
            val alpha = when {
                i % 17 == 0 -> 0.040f
                i % 7 == 0 -> 0.030f
                else -> 0.018f
            }
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y),
            )
        }

        // 6. Nearly invisible curved light trails. Multiple wide curves make
        // the surface feel dimensional without becoming decorative neon.
        val curveOne = Path().apply {
            moveTo(-w * 0.04f, h * 0.78f)
            cubicTo(
                w * 0.20f, h * 0.54f,
                w * 0.53f, h * 0.93f,
                w * 1.05f, h * 0.50f,
            )
        }
        drawPath(
            path = curveOne,
            color = HomeAccent.copy(alpha = 0.018f),
            style = Stroke(width = 1.1f, cap = StrokeCap.Round),
        )

        val curveTwo = Path().apply {
            moveTo(w * 0.18f, h * 1.04f)
            cubicTo(
                w * 0.42f, h * 0.72f,
                w * 0.76f, h * 0.84f,
                w * 1.02f, h * 0.64f,
            )
        }
        drawPath(
            path = curveTwo,
            color = Color.White.copy(alpha = 0.010f),
            style = Stroke(width = 0.9f, cap = StrokeCap.Round),
        )

        // 7. Vignette: darken all four edges progressively, preserving the
        // center as the visual focus.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.20f),
                    Color.Black.copy(alpha = 0.62f),
                ),
                center = Offset(w * 0.50f, h * 0.47f),
                radius = maxOf(w, h) * 0.72f,
            )
        )

        // 8. Final top/bottom edge falloff. This prevents the UI from looking
        // like cards floating over a single flat background.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.20f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.28f),
                ),
                startY = 0f,
                endY = h,
            )
        )
    }
}
