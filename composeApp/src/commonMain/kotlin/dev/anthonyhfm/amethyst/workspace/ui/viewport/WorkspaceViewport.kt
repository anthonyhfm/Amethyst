package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlin.math.min
import kotlin.math.max

@Composable
fun WorkspaceViewport(
    modifier: Modifier = Modifier,
    elements: List<ViewportElement>,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    val color = MaterialTheme.colorScheme.onSurface.copy(0.2f)
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    val step = (20.dp.value * density * scale).toInt()
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
                detectTapGestures(
                    onTap = {
                        selectedElement = null
                    }
                )

                detectDragGestures(onDrag = { change, dragAmount ->
                    change.consume()
                    offset += dragAmount
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) {
                        scale = max(0.5f, min(1.5f, scale * zoom))
                    } else {
                        offset += pan
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (x in (((offset.x * scale) % step).toInt() - step) until size.width.toInt() step step) {
                for (y in (((offset.y * scale) % step).toInt() - step) until size.height.toInt() step step) {
                    drawCircle(
                        color = color,
                        radius = 2f,
                        center = Offset(x.toFloat(), y.toFloat())
                    )
                }
            }
        }
        elements.forEachIndexed { index, element ->
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
                            ((element.position.value.x + xOffset) * scale - (element.size.width / 2 * density) * scale).toInt(),
                            ((element.position.value.y + yOffset) * scale - (element.size.height / 2 * density) * scale).toInt()
                        )
                    }
                    .scale(scale)
                    .then(
                        other = if (selectedElement == element) {
                            Modifier
                                .border(2.dp, MaterialTheme.colorScheme.primary, element.shape)
                                .padding(5.dp)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        selectedElement = element
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                selectedElement = element
                            },
                            onDrag = { input, offset ->
                                input.consume()

                                onEvent(
                                    WorkspaceContract.Event.ChangeViewportElementPosition(
                                        index = index,
                                        offset = element.position.value.copy(
                                            x = element.position.value.x + offset.x * scale,
                                            y = element.position.value.y + offset.y * scale,
                                        )
                                    )
                                )
                            }
                        )
                    }
            ) {
                if (element is LaunchpadViewportElement && element == selectedElement) {
                    LaunchedEffect(index) {
                        element.indexInViewport = index
                    }

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