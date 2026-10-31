package com.johnreyes.fitbod.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * How the Fold is being held.
 *
 * - [Flat]: folded (cover screen) or fully open; layout follows window width.
 * - [Tabletop]: half open with a horizontal hinge (Flex mode, standing on a bench).
 *   [hingeFraction] is the hinge's distance from the top of the window, as a fraction.
 * - [Book]: half open with a vertical hinge; panes split at the hinge.
 */
sealed interface Posture {
    data object Flat : Posture
    data class Tabletop(val hingeFraction: Float) : Posture
    data class Book(val hingeFraction: Float) : Posture
}

@Composable
fun rememberPosture(activity: Activity): Posture {
    var posture by remember { mutableStateOf<Posture>(Posture.Flat) }
    LaunchedEffect(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { info ->
            val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            val view = activity.window.decorView
            posture = when {
                fold == null || fold.state != FoldingFeature.State.HALF_OPENED -> Posture.Flat
                fold.orientation == FoldingFeature.Orientation.HORIZONTAL ->
                    Posture.Tabletop((fold.bounds.exactCenterY() / view.height.coerceAtLeast(1)).coerceIn(0.2f, 0.8f))
                else -> Posture.Book((fold.bounds.exactCenterX() / view.width.coerceAtLeast(1)).coerceIn(0.2f, 0.8f))
            }
        }
    }
    return posture
}
