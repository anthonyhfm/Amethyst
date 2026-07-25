package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer

fun Modifier.chainDeviceMuteEffect(isMuted: Boolean): Modifier = if (isMuted) {
    this.graphicsLayer {
        alpha = 0.75f
        colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
} else this
