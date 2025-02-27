package dev.anthonyhfm.amethyst.devices.effects.gradient.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDeviceState

@Composable
fun GradientEditorBar(
    selectedColor: Int?,
    onSelectionChange: (Int?) -> Unit,
    colors: List<GradientChainDeviceState.GradientColor>,
    onGradientDataEmit: (List<GradientChainDeviceState.GradientColor>) -> Unit,
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
    ) {
        Canvas(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(4.dp))
                .fillMaxWidth()
                .height(28.dp)
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = colors.sortedBy { it.position }
                        .map { it.position to Color(it.r, it.g, it.b) }
                        .toTypedArray(),
                    startX = 0f,
                    endX = size.width
                ),
                size = size
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
        ) {
            colors.forEachIndexed { index, color ->
                var pos: Float by remember { mutableStateOf(color.position) }

                LaunchedEffect(pos) {
                    onGradientDataEmit(
                        colors.mapIndexed { i, it ->
                            if (i == index) it.copy(position = pos) else it
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .offset(x = -6.dp, y = -5.dp)
                        .offset(
                            x = maxWidth * pos
                        )
                        .scale(if (selectedColor == index) 1.1f else 1f)
                        .shadow(
                            elevation = if (selectedColor == index) 16.dp else 6.dp,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .height(38.dp)
                        .width(12.dp)
                        .background(Color(color.r, color.g, color.b))
                        .border(
                            width = 2.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .clickable {
                            if (selectedColor == index) {
                                onSelectionChange(null)
                            } else {
                                onSelectionChange(index)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { input, offset ->
                                    input.consume()

                                    val pct = (offset.x / density.density).dp / maxWidth
                                    val newPos = (pos + pct).coerceIn(0f, 1f)

                                    pos = newPos
                                }
                            )
                        }
                )
            }
        }
    }
}