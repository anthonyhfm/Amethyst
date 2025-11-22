package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

@Composable
fun SelectionCursor(
    selectedTimeMs: Long?,
    zoomLevel: Float,
    scrollState: ScrollState,
    laneHeight: Dp = 120.dp,
    viewportRelative: Boolean = false
) {
    if (selectedTimeMs == null) return
    val cursorXPositionPx by remember(selectedTimeMs, zoomLevel, viewportRelative, scrollState.value) {
        derivedStateOf { selectedTimeMs * zoomLevel }
    }
    Box(
        modifier = Modifier
            .offset(x = -1.5.dp)
            .offset { IntOffset(cursorXPositionPx.roundToInt(), 0) }
            .width(3.dp)
            .height(laneHeight)
            .background(color = Color.White)
    )
}
