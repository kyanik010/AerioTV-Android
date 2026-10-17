package com.aeriotv.android.feature.main

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxHeight as composeFillMaxHeight

/**
 * Compatibility bridge for MainScaffold's TV/mobile shells.
 * Keeps the existing MainScaffold source unchanged while exposing
 * fillMaxHeight in this package.
 */
fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier =
    composeFillMaxHeight(fraction)
