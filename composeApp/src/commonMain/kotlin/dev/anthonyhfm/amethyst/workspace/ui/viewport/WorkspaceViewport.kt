package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun WorkspaceViewport(modifier: Modifier = Modifier, elements: List<ViewportElement>) {
    val color = MaterialTheme.colorScheme.onSurface.copy(0.2f)
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    val step = (20.dp.value * density).toInt()
    var selectedElement by remember { mutableStateOf<ViewportElement?>(null) }
    val viewportSize = remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewportSize.value = Size(size.width.toFloat(), size.height.toFloat())
                offset = Offset(viewportSize.value.width / 2f, viewportSize.value.height / 2f)
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val scrollDelta = event.changes.firstOrNull()?.scrollDelta
                        if (scrollDelta != null) {
                            offset += Offset(scrollDelta.x, scrollDelta.y)
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { pos ->
                    selectedElement = elements.find {
                        (it.position - offset).getDistance() < 50f
                    }
                }, onDrag = { change, dragAmount ->
                    change.consume()
                    selectedElement?.let {
                        it.position += dragAmount
                    } ?: run {
                        offset += dragAmount
                    }
                })
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (x in ((offset.x % step).toInt() - step) until size.width.toInt() step step) {
                for (y in ((offset.y % step).toInt() - step) until size.height.toInt() step step) {
                    drawCircle(
                        color = color,
                        radius = 2f,
                        center = Offset(x.toFloat(), y.toFloat())
                    )
                }
            }
        }
        elements.forEach { element ->
            var elementHeight: Dp = 0.dp
            var elementWidth: Dp = 0.dp

            BoxWithConstraints(
                modifier = Modifier
                    .offset {
                        val xOffset = if (selectedElement == element) {
                            offset.x - (5 * density)
                        } else offset.x

                        val yOffset = if (selectedElement == element) {
                            offset.y - (5 * density)
                        } else offset.y

                        IntOffset(
                            (element.position.x + xOffset - (element.size.width / 2 * density)).toInt(),
                            (element.position.y + yOffset - (element.size.height / 2 * density)).toInt()
                        )
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        selectedElement = element
                    }
                    .then(
                        other = if (selectedElement == element) {
                            Modifier
                                .border(2.dp, MaterialTheme.colorScheme.primary, element.shape)
                                .padding(5.dp)
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (element is ViewportElement.ViewportLaunchpad && element == selectedElement) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = -56.dp),
                    ) {
                        element.actions(this)
                    }
                }

                element.content()
            }
        }
    }
}