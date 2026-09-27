package com.aeriotv.android.feature.main

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeriotv.android.core.preferences.AppLanguage
import com.aeriotv.android.core.preferences.LocalAppLanguage

@Composable
internal fun HomeSectionMenu(
    availableTabs: List<AppTab>,
    onSelect: (AppTab) -> Unit,
) {
    val language = LocalAppLanguage.current
    val isArabic = language != AppLanguage.ENGLISH

    val cards = listOf(
        HomeSectionCard(AppTab.LiveTV, if (isArabic) "القنوات" else "Live TV"),
        HomeSectionCard(AppTab.Movies, if (isArabic) "الأفلام" else "Movies"),
        HomeSectionCard(AppTab.TVShows, if (isArabic) "المسلسلات" else "TV Shows"),
        HomeSectionCard(AppTab.Favorites, if (isArabic) "المفضلة" else "Favorites"),
        HomeSectionCard(AppTab.Settings, if (isArabic) "الإعدادات" else "Settings"),
    )

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isArabic) "اختر القسم" else "Choose a section",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(24.dp))
            cards.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    row.forEach { card ->
                        HomeSectionCardView(
                            card = card,
                            enabled = card.tab in availableTabs || card.tab == AppTab.Settings,
                            onClick = { onSelect(card.tab) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                if (rowIndex < 2) Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private data class HomeSectionCard(
    val tab: AppTab,
    val label: String,
)

@Composable
private fun HomeSectionCardView(
    card: HomeSectionCard,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focused = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val language = LocalAppLanguage.current
    val label = if (language == AppLanguage.ENGLISH) card.label else when (card.tab) {
        AppTab.LiveTV -> "القنوات"
        AppTab.Movies -> "الأفلام"
        AppTab.TVShows -> "المسلسلات"
        AppTab.Favorites -> "المفضلة"
        AppTab.Settings -> "الإعدادات"
        else -> card.label
    }

    Box(
        modifier = modifier
            .height(128.dp)
            .focusable(enabled)
            .onFocusChanged { focused.value = it.hasFocus }
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.45f
                scaleX = if (focused.value) 1.025f else 1f
                scaleY = if (focused.value) 1.025f else 1f
            }
            .border(
                width = if (focused.value) 2.dp else 1.dp,
                color = if (focused.value) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(22.dp),
            )
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                RoundedCornerShape(22.dp),
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = card.tab.iconSelected,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
