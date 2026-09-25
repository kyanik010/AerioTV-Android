package com.aeriotv.android.feature.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeriotv.android.R
import com.aeriotv.android.ui.settings.rememberIsTvDevice

/**
 * Cold-launch splash. Mirrors the CURRENT iOS/tvOS SplashView
 * (Aerio/App/SplashView.swift), which is a STATIC layout, not the legacy
 * AerioSplash.mp4 clip: black background, the rounded-square AerioLogo,
 * bold "AerioTV", and the cyan "Live TV · Movies · Series" subtitle.
 * Fades in 0.4s, holds to 2.3s, fades out 0.4s, dismisses at 2.8s.
 *
 * The Android port used to crop-fill the legacy portrait mp4, which on a
 * 16:9 TV scaled the 720px-wide clip across the whole panel and kept only
 * the middle third of the frame -- a gigantic, cropped "Aerio" (user
 * report). The video is gone entirely; every form factor now renders the
 * same layout iOS/tvOS draw, with the per-platform metrics from
 * SplashView.swift (tvOS 160/72/28, iPhone 100/50/18, iPad 130/64/24).
 *
 * Gated on `appBehaviorsSkipLoadingScreen` (Phase 8b pref). When the user
 * has flipped that toggle on, the splash dismisses immediately so the
 * Welcome / Main screen lands without the delay.
 */
@Composable
fun SplashGate(
    content: @Composable () -> Unit,
) {
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // The system splash now covers cold-start process initialization.
        // Do not add another artificial 1.2s delay before the activation UI.
        finished = true
    }
    if (finished) content() else SplashContent()
}

@Composable
private fun SplashContent(modifier: Modifier = Modifier) {
    val isTv = rememberIsTvDevice()
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "eagleSplashAlpha",
    )
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black).alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.eagle_x_launcher),
                contentDescription = "Eagle X",
                modifier = Modifier.size(if (isTv) 110.dp else 120.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Eagle X",
                fontSize = if (isTv) 30.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
