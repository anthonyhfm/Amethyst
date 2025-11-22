package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun PlayheadCursor(
    positionMs: Long,
    zoomLevel: Float,
    scrollState: ScrollState
) {
    Box(
        modifier = Modifier
            .offset {
                val cursorX = (positionMs.toDouble() * zoomLevel.toDouble() - scrollState.value.toDouble()).roundToInt()
                IntOffset(cursorX, 0)
            }
            .width(2.dp)
            .fillMaxHeight()
            .background(Color(0xff93ff93), RectangleShape)
            .dropShadow(
                shape = RectangleShape,
                shadow = Shadow(
                    color = Color(0xff93ff93).copy(alpha = 0.6f),
                    radius = 4.dp
                )
            )
    )
}
