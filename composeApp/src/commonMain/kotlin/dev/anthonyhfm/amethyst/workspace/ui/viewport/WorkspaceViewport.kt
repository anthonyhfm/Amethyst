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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WorkspaceViewport(
    modifier: Modifier = Modifier,
    viewportState: WorkspaceContract.ViewportState,
    elements: List<ViewportElement>,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    val density = LocalDensity.current.density
    val gridSize = (40 * density).toInt()
    val color = MaterialTheme.colorScheme.onSurface.copy(0.2f)
    val viewportSize = remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewportSize.value = Size(size.width.toFloat(), size.height.toFloat())
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { input, offset ->
                            onEvent(WorkspaceContract.Event.OnPanViewport(offset))
                        }
                    )
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onEvent(WorkspaceContract.Event.OnSelectDevice(null))
                }
        ) {
            for (x in ((viewportState.offset.x % gridSize).toInt() - gridSize) until size.width.toInt() step gridSize) {
                for (y in ((viewportState.offset.y % gridSize).toInt() - gridSize) until size.height.toInt() step gridSize) {
                    drawCircle(
                        color = color,
                        radius = 2f * density,
                        center = Offset(x.toFloat(), y.toFloat())
                    )
                }
            }
        }
        elements.forEachIndexed { index, element ->
            var draggingOffset by remember { mutableStateOf(Offset.Zero) }

            BoxWithConstraints(
                modifier = Modifier
                    .offset {
                        val xOffset = (element.position.value.x * gridSize + viewportState.offset.x).roundToInt()
                        val yOffset = (element.position.value.y * gridSize + viewportState.offset.y).roundToInt()
                        IntOffset(xOffset, yOffset)
                    }
                    .scale(1f)
                    .then(
                        other = if (viewportState.selectedElement == index) {
                            Modifier
                                .border(2.dp, MaterialTheme.colorScheme.primary, element.shape)
                        } else Modifier
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                onEvent(WorkspaceContract.Event.OnSelectDevice(index))
                            },
                            onDrag = { input, offset ->
                                input.consume()

                                draggingOffset += offset

                                // Calculate accumulated movement in grid units
                                val accumulatedGridX = draggingOffset.x / gridSize
                                val accumulatedGridY = draggingOffset.y / gridSize

                                // Check if we've moved at least one grid unit in any direction
                                if (abs(accumulatedGridX) >= 1f || abs(accumulatedGridY) >= 1f) {
                                    // Calculate grid movement (whole grid units only)
                                    val gridMoveX = accumulatedGridX.toInt()
                                    val gridMoveY = accumulatedGridY.toInt()

                                    // Only move if we have at least one whole grid unit of movement
                                    if (gridMoveX != 0 || gridMoveY != 0) {
                                        // Calculate new position in grid units
                                        val newX = element.position.value.x + gridMoveX
                                        val newY = element.position.value.y + gridMoveY

                                        // Subtract the applied movement from the accumulated offset
                                        draggingOffset = Offset(
                                            draggingOffset.x - (gridMoveX * gridSize),
                                            draggingOffset.y - (gridMoveY * gridSize)
                                        )

                                        onEvent(
                                            WorkspaceContract.Event.ChangeViewportElementPosition(
                                                index = index,
                                                offset = Offset(newX, newY)
                                            )
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingOffset = Offset.Zero
                            }
                        )
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onEvent(WorkspaceContract.Event.OnSelectDevice(index))
                    }
            ) {
                if (element is LaunchpadViewportElement) {
                    LaunchedEffect(index) {
                        element.indexInViewport = index
                    }

                    if (viewportState.selectedElement == index) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = -56.dp)
                                .zIndex(1000f),
                        ) {
                            element.actions(this)
                        }
                    }
                }
                element.content()
            }
        }
    }
}
