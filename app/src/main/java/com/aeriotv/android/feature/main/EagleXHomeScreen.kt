package com.aeriotv.android.feature.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.util.*

// ─── Colour tokens ───────────────────────────────────────────────────────────
private val BgDeep        = Color(0xFF060C18)
private val BgMid         = Color(0xFF0B1628)
private val GlassPanel    = Color(0x1AFFFFFF)
private val GlassBorder   = Color(0x33FFFFFF)
private val GlassBorderFx = Color(0x66A0C8FF)
private val AccentBlue    = Color(0xFF4A9EFF)
private val AccentTeal    = Color(0xFF00C8A0)
private val AccentCopper  = Color(0xFFB87333)
private val TextPrimary   = Color(0xFFEEF4FF)
private val TextSecondary = Color(0xFF8899BB)
private val FocusGlow     = Color(0x994A9EFF)
private val WaveColor1    = Color(0x1200C8A0)
private val WaveColor2    = Color(0x12B87333)

// ─── Data class for nav items ─────────────────────────────────────────────────
data class HomeNavItem(
    val labelAr: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

// ─── Main Screen ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EagleXHomeScreen(
    onSelectTab: (AppTab) -> Unit,
    onPlayMovie: (String) -> Unit,
    onPlaySeries: (Int) -> Unit,
    username: String?,
    expiresAt: String?,
    viewModel: com.aeriotv.android.feature.ondemand.OnDemandViewModel,
) {
    val appName = "Eagle X"
    val safeUsername = username.orEmpty()
    val safeExpirationDate = expiresAt.orEmpty()
    val context = LocalContext.current
    val isTV = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    // Clock state
    var timeString by remember { mutableStateOf("") }
    var amPmString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            val h = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            val m = "%02d".format(cal.get(Calendar.MINUTE))
            amPmString = if (cal.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"
            timeString = "$h:$m"
            delay(30_000)
        }
    }

    // Wave animation
    val waveAnim = rememberInfiniteTransition(label = "wave")
    val waveOffset by waveAnim.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "waveOff"
    )

    // Nav items
    val navItems = listOf(
        HomeNavItem("أفلام",    Icons.Rounded.Movie,        AccentBlue)   { onSelectTab(AppTab.Movies) },
        HomeNavItem("مسلسلات", Icons.Rounded.VideoLibrary, AccentTeal)   { onSelectTab(AppTab.TVShows) },
        HomeNavItem("قنوات",   Icons.Rounded.LiveTv,       AccentCopper) { onSelectTab(AppTab.LiveTV) }
    )

    // Toolbar focusers
    val toolbarFocusers = remember { List(5) { FocusRequester() } }
    val navFocusers     = remember { List(3) { FocusRequester() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(BgDeep, BgMid, Color(0xFF081020)))
            )
    ) {
        // ── Background abstract waves ──────────────────────────────────────
        AmbientWaves(waveOffset, isTV)

        if (isTV) {
            // ────────── TV LANDSCAPE LAYOUT ──────────────────────────────
            TvLayout(
                appName       = appName,
                timeString    = timeString,
                amPmString    = amPmString,
                navItems      = navItems,
                username       = safeUsername,
                expirationDate = safeExpirationDate,
                accountId      = "",
                toolbarFocusers = toolbarFocusers,
                navFocusers   = navFocusers,
                onPowerClick   = { },
                onRefreshClick = { },
                onUserClick    = { },
                onSettingsClick= { onSelectTab(AppTab.Settings) },
                onSearchClick  = { onSelectTab(AppTab.Search) }
            )
        } else {
            // ────────── PHONE PORTRAIT LAYOUT ────────────────────────────
            PhoneLayout(
                appName        = appName,
                timeString     = timeString,
                amPmString     = amPmString,
                navItems       = navItems,
                username       = safeUsername,
                expirationDate = safeExpirationDate,
                accountId      = "",
                toolbarFocusers= toolbarFocusers,
                navFocusers    = navFocusers,
                onPowerClick   = { },
                onRefreshClick = { },
                onUserClick    = { },
                onSettingsClick= { onSelectTab(AppTab.Settings) },
                onSearchClick  = { onSelectTab(AppTab.Search) }
            )
        }
    }
}

// ─── Phone Portrait Layout ────────────────────────────────────────────────────
@Composable
private fun PhoneLayout(
    appName: String,
    timeString: String,
    amPmString: String,
    navItems: List<HomeNavItem>,
    username: String,
    expirationDate: String,
    accountId: String,
    toolbarFocusers: List<FocusRequester>,
    navFocusers: List<FocusRequester>,
    onPowerClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onUserClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header: Logo + Clock ──────────────────────────────────────────
        AppLogo(appName)
        Spacer(Modifier.height(12.dp))
        DigitalClock(timeString, amPmString)
        Spacer(Modifier.height(20.dp))

        // ── Toolbar ───────────────────────────────────────────────────────
        ToolbarRow(
            focusers       = toolbarFocusers,
            navFocuserDown = navFocusers,
            onPowerClick   = onPowerClick,
            onRefreshClick = onRefreshClick,
            onUserClick    = onUserClick,
            onSettingsClick = onSettingsClick,
            onSearchClick  = onSearchClick
        )
        Spacer(Modifier.height(24.dp))

        // ── Main Glass Panel ──────────────────────────────────────────────
        GlassNavPanel(navItems, navFocusers)
        Spacer(Modifier.height(16.dp))

        // ── Subscription Card ─────────────────────────────────────────────
        if (username.isNotBlank() || expirationDate.isNotBlank()) {
            EagleXSubscriptionCard(username, expirationDate, accountId)
        }
    }
}

// ─── TV Landscape Layout ──────────────────────────────────────────────────────
@Composable
private fun TvLayout(
    appName: String,
    timeString: String,
    amPmString: String,
    navItems: List<HomeNavItem>,
    username: String,
    expirationDate: String,
    accountId: String,
    toolbarFocusers: List<FocusRequester>,
    navFocusers: List<FocusRequester>,
    onPowerClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onUserClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left: header + toolbar + sub card ────────────────────────────
        Column(
            modifier = Modifier.weight(0.45f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppLogo(appName, logoSize = 56.dp, textSize = 22.sp)
            Spacer(Modifier.height(10.dp))
            DigitalClock(timeString, amPmString, fontSize = 48.sp)
            Spacer(Modifier.height(20.dp))
            ToolbarRow(
                focusers        = toolbarFocusers,
                navFocuserDown  = navFocusers,
                onPowerClick   = onPowerClick,
                onRefreshClick = onRefreshClick,
                onUserClick    = onUserClick,
                onSettingsClick = onSettingsClick,
                onSearchClick  = onSearchClick,
                iconSize        = 52.dp
            )
            Spacer(Modifier.height(32.dp))
            if (username.isNotBlank() || expirationDate.isNotBlank()) {
                EagleXSubscriptionCard(username, expirationDate, accountId)
            }
        }

        Spacer(Modifier.width(40.dp))

        // ── Right: nav panel ──────────────────────────────────────────────
        Box(modifier = Modifier.weight(0.55f), contentAlignment = Alignment.Center) {
            GlassNavPanel(navItems, navFocusers, itemHeight = 160.dp)
        }
    }
}

// ─── App Logo ─────────────────────────────────────────────────────────────────
@Composable
private fun AppLogo(
    name: String,
    logoSize: Dp = 48.dp,
    textSize: TextUnit = 18.sp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Glowing circle logo mark
        Box(
            modifier = Modifier
                .size(logoSize)
                .background(
                    Brush.radialGradient(
                        listOf(AccentBlue.copy(alpha = 0.9f), AccentTeal.copy(alpha = 0.6f), Color.Transparent)
                    ),
                    CircleShape
                )
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.LiveTv,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(logoSize * 0.55f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Digital Clock ────────────────────────────────────────────────────────────
@Composable
private fun DigitalClock(
    timeString: String,
    amPmString: String,
    fontSize: TextUnit = 40.sp
) {
    // Arabic digits mapping
    fun toArabicNumerals(s: String): String {
        val arabicDigits = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')
        return s.map { if (it.isDigit()) arabicDigits[it - '0'] else it }.joinToString("")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // AM/PM on right side (RTL: rendered first in LTR compose = right)
        Text(
            text = amPmString,
            fontSize = fontSize * 0.45f,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = toArabicNumerals(timeString),
            fontSize = fontSize,
            fontWeight = FontWeight.Light,
            color = TextPrimary,
            letterSpacing = 2.sp
        )
    }
}

// ─── Toolbar Row ─────────────────────────────────────────────────────────────
@Composable
private fun ToolbarRow(
    focusers: List<FocusRequester>,
    navFocuserDown: List<FocusRequester>,
    onPowerClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onUserClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    iconSize: Dp = 44.dp
) {
    data class ToolItem(val icon: ImageVector, val tint: Color, val onClick: () -> Unit, val cd: String)

    val tools = listOf(
        ToolItem(Icons.Rounded.PowerSettingsNew, Color(0xFFFF5555), onPowerClick,   "إغلاق"),
        ToolItem(Icons.Rounded.Refresh,          AccentBlue,        onRefreshClick, "تحديث"),
        ToolItem(Icons.Rounded.Person,           AccentTeal,        onUserClick,    "المستخدم"),
        ToolItem(Icons.Rounded.Settings,         TextSecondary,     onSettingsClick,"إعدادات"),
        ToolItem(Icons.Rounded.Search,           AccentBlue,        onSearchClick,  "بحث")
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEachIndexed { i, tool ->
            GlassIconButton(
                icon      = tool.icon,
                tint      = tool.tint,
                cd        = tool.cd,
                size      = iconSize,
                focuser   = focusers[i],
                nextDown  = if (i < navFocuserDown.size) navFocuserDown[0] else null,
                nextRight = if (i < tools.lastIndex) focusers[i + 1] else null,
                nextLeft  = if (i > 0) focusers[i - 1] else null,
                onClick   = tool.onClick
            )
        }
    }
}

// ─── Glass Icon Button ────────────────────────────────────────────────────────
@Composable
private fun GlassIconButton(
    icon: ImageVector,
    tint: Color,
    cd: String,
    size: Dp,
    focuser: FocusRequester,
    nextDown: FocusRequester?,
    nextRight: FocusRequester?,
    nextLeft: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "btnScale")
    val borderAlpha by animateFloatAsState(if (focused) 1f else 0.3f, label = "btnBorder")

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .scale(scale)
            .focusRequester(focuser)
            .focusProperties {
                down  = nextDown  ?: FocusRequester.Default
                right = nextRight ?: FocusRequester.Default
                left  = nextLeft  ?: FocusRequester.Default
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.radialGradient(
                    if (focused)
                        listOf(tint.copy(alpha = 0.25f), GlassPanel)
                    else
                        listOf(GlassPanel, Color(0x0AFFFFFF))
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = borderAlpha),
                        GlassBorder.copy(alpha = borderAlpha * 0.5f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        if (focused) {
            // Glow halo behind icon
            Box(
                modifier = Modifier
                    .size(size * 0.7f)
                    .blur(12.dp)
                    .background(tint.copy(alpha = 0.4f), CircleShape)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = cd,
            tint = if (focused) tint else tint.copy(alpha = 0.75f),
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

// ─── Glass Navigation Panel ───────────────────────────────────────────────────
@Composable
private fun GlassNavPanel(
    items: List<HomeNavItem>,
    focusers: List<FocusRequester>,
    itemHeight: Dp = 140.dp
) {
    // Panel glow animation
    val glowAnim = rememberInfiniteTransition(label = "panelGlow")
    val glowAlpha by glowAnim.animateFloat(
        initialValue = 0.03f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0D1F3C).copy(alpha = 0.92f),
                        Color(0xFF071628).copy(alpha = 0.92f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(GlassBorder, GlassBorderFx.copy(alpha = 0.15f), GlassBorder)
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        // Subtle inner glow top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, AccentBlue.copy(alpha = glowAlpha * 4f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEachIndexed { i, item ->
                NavPanelItem(
                    item     = item,
                    height   = itemHeight,
                    focuser  = focusers[i],
                    nextUp   = if (i > 0) focusers[i - 1] else null,
                    nextDown = if (i < items.lastIndex) focusers[i + 1] else null
                )
                if (i < items.lastIndex) {
                    HorizontalDivider(
                        color = GlassBorder.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}

// ─── Nav Panel Item ───────────────────────────────────────────────────────────
@Composable
private fun NavPanelItem(
    item: HomeNavItem,
    height: Dp,
    focuser: FocusRequester,
    nextUp: FocusRequester?,
    nextDown: FocusRequester?
) {
    var focused by remember { mutableStateOf(false) }
    val scale       by animateFloatAsState(if (focused) 1.04f else 1f,    label = "navScale")
    val iconScale   by animateFloatAsState(if (focused) 1.15f else 1f,    label = "iconScale")
    val bgAlpha     by animateFloatAsState(if (focused) 0.18f else 0f,    label = "navBg")
    val borderAlpha by animateFloatAsState(if (focused) 0.8f else 0f,     label = "navBorder")
    val textAlpha   by animateFloatAsState(if (focused) 1f else 0.85f,    label = "navText")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .scale(scale)
            .focusRequester(focuser)
            .focusProperties {
                up   = nextUp   ?: FocusRequester.Default
                down = nextDown ?: FocusRequester.Default
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.radialGradient(
                    listOf(
                        item.tint.copy(alpha = bgAlpha),
                        item.tint.copy(alpha = bgAlpha * 0.3f),
                        Color.Transparent
                    )
                )
            )
            .then(
                if (focused) Modifier.border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(item.tint.copy(alpha = borderAlpha), item.tint.copy(alpha = borderAlpha * 0.3f))
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .clickable(onClick = item.onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 3D-style icon box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.scale(iconScale)
            ) {
                // Shadow/glow layer
                if (focused) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .blur(20.dp)
                            .background(item.tint.copy(alpha = 0.5f), CircleShape)
                    )
                }
                // Icon container with gradient bg
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    item.tint.copy(alpha = if (focused) 0.3f else 0.15f),
                                    Color(0xFF0A1525).copy(alpha = 0.8f)
                                )
                            ),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    item.tint.copy(alpha = if (focused) 0.8f else 0.4f),
                                    item.tint.copy(alpha = if (focused) 0.2f else 0.1f)
                                )
                            ),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.labelAr,
                        tint = if (focused) item.tint else item.tint.copy(alpha = 0.8f),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text       = item.labelAr,
                fontSize   = 20.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                color      = TextPrimary.copy(alpha = textAlpha),
                textAlign  = TextAlign.Center,
                letterSpacing = 1.sp
            )
        }
    }
}

// ─── Subscription Card ────────────────────────────────────────────────────────
@Composable
fun EagleXSubscriptionCard(
    username: String,
    expirationDate: String,
    accountId: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D1F3C).copy(alpha = 0.9f), Color(0xFF071222).copy(alpha = 0.9f))
                )
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Header labels row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (username.isNotBlank()) {
                    Text(
                        text = "اسم المستخدم",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Username value
            if (username.isNotBlank()) {
                Text(
                    text = username,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
            }

            if (expirationDate.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "تاريخ انتهاء الصلاحية",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
            }

            Spacer(Modifier.height(8.dp))

            HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

            Spacer(Modifier.height(10.dp))

            // Bottom row: accountId + expiry date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (accountId.isNotBlank()) {
                    Text(
                        text = accountId,
                        fontSize = 13.sp,
                        color = AccentBlue.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                }
                if (expirationDate.isNotBlank()) {
                    Text(
                        text = expirationDate,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ─── Ambient Background Waves ─────────────────────────────────────────────────
@Composable
private fun AmbientWaves(offset: Float, isTV: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(-1f)
    ) {
        val w = size.width
        val h = size.height

        // Teal wave (bottom-right)
        val path1 = Path().apply {
            moveTo(w * 0.4f, h)
            cubicTo(
                w * (0.5f + 0.2f * offset), h * 0.6f,
                w * (0.9f - 0.1f * offset), h * 0.7f,
                w, h * 0.5f
            )
            lineTo(w, h); close()
        }
        drawPath(path1, Brush.linearGradient(
            listOf(WaveColor1, Color(0x0800C8A0), Color.Transparent)
        ))

        // Copper wave (left)
        val path2 = Path().apply {
            moveTo(0f, h * 0.5f)
            cubicTo(
                w * (0.15f + 0.1f * offset), h * 0.65f,
                w * (0.3f - 0.1f * offset), h * 0.8f,
                w * 0.5f, h
            )
            lineTo(0f, h); close()
        }
        drawPath(path2, Brush.linearGradient(
            listOf(WaveColor2, Color(0x08B87333), Color.Transparent)
        ))

        // Subtle radial glow center-right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AccentBlue.copy(alpha = 0.04f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.3f),
                radius = w * 0.5f
            ),
            radius = w * 0.5f,
            center = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.3f)
        )
    }
}
