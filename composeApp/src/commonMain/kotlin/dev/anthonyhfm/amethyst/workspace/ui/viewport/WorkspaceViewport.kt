package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
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
    val selections by SelectionManager.selections.collectAsState()

    LaunchedEffect(elements.size, viewportSize.value) {
        if (viewportSize.value.width <= 0f || viewportSize.value.height <= 0f) return@LaunchedEffect

        val launchpads = elements.filterIsInstance<LaunchpadViewportElement>()
        if (launchpads.isNotEmpty()) {
            ViewportController.centerLaunchpads(
                viewportOffset = viewportState.offset,
                viewportZoom = viewportState.zoom,
                elements = launchpads,
                viewportSize = viewportSize.value,
                gridSize = gridSize,
                onEvent = onEvent
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewportSize.value = Size(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(Unit) {
                detectTransformGestures(
                    onGesture = { centroid, _, zoom, _ ->
                        val zoomDelta = (zoom - 1f) * 0.1f
                        onEvent(WorkspaceContract.Event.OnZoomViewport(zoomDelta, centroid))
                    }
                )
            }
            .pointerInput("scroll") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val scrollDelta = event.changes.firstOrNull()?.scrollDelta
                        if (scrollDelta != null && scrollDelta.y != 0f) {
                            val zoomDelta = -scrollDelta.y * 0.05f
                            val mousePosition = event.changes.first().position
                            onEvent(WorkspaceContract.Event.OnZoomViewport(zoomDelta, mousePosition))
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput("pan") {
                    detectDragGestures(
                        onDrag = { _, offset ->
                            onEvent(WorkspaceContract.Event.OnPanViewport(offset))
                        }
                    )
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.Layout) {
                        if (SelectionManager.selections.value.any { it is Selectable.VirtualViewportDevice }) {
                            SelectionManager.clear()
                        }
                    }
                }
        ) {
            val scaledGridSize = gridSize * viewportState.zoom
            val startX = (viewportState.offset.x % scaledGridSize) - scaledGridSize
            val startY = (viewportState.offset.y % scaledGridSize) - scaledGridSize

            var x = startX
            while (x < size.width) {
                var y = startY
                while (y < size.height) {
                    drawCircle(
                        color = color,
                        radius = 2f * density * viewportState.zoom,
                        center = Offset(x, y)
                    )
                    y += scaledGridSize
                }
                x += scaledGridSize
            }
        }

        elements.forEach { element ->
            Box(
                modifier = Modifier
                    .size(
                        width = element.size.width.dp * gridSize / density,
                        height = element.size.height.dp * gridSize / density
                    )
                    .offset {
                        val scaledGridSize = gridSize * viewportState.zoom
                        val xOffset = (element.position.value.x * scaledGridSize + viewportState.offset.x).roundToInt()
                        val yOffset = (element.position.value.y * scaledGridSize + viewportState.offset.y).roundToInt()
                        IntOffset(xOffset, yOffset)
                    }
                    .graphicsLayer {
                        scaleX = viewportState.zoom
                        scaleY = viewportState.zoom
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                    .dropShadow(
                        element.shape,
                        androidx.compose.ui.graphics.shadow.Shadow(
                            radius = 8.dp,
                            color = Color.Black.copy(0.3f)
                        )
                    )
            )
        }

        elements.forEachIndexed { index, element ->
            var draggingOffset by remember { mutableStateOf(Offset.Zero) }
            val selected = selections.any { it.selectionUUID == element.selectionUUID }

            BoxWithConstraints(
                modifier = Modifier
                    .offset {
                        val scaledGridSize = gridSize * viewportState.zoom
                        val xOffset = (element.position.value.x * scaledGridSize + viewportState.offset.x).roundToInt()
                        val yOffset = (element.position.value.y * scaledGridSize + viewportState.offset.y).roundToInt()
                        IntOffset(xOffset, yOffset)
                    }
                    .graphicsLayer {
                        scaleX = viewportState.zoom
                        scaleY = viewportState.zoom
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
                    .then(
                        other = if (selected) {
                            Modifier
                                .border((2 / viewportState.zoom).dp, MaterialTheme.colorScheme.primary, element.shape)
                        } else Modifier
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                if (WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.Layout) {
                                    SelectionManager.select(
                                        Selectable.VirtualViewportDevice(
                                            element = element as LaunchpadViewportElement
                                        )
                                    )
                                }
                            },
                            onDrag = { input, offset ->
                                input.consume()

                                draggingOffset += offset

                                val scaledGridSize = gridSize * viewportState.zoom
                                val accumulatedGridX = draggingOffset.x / scaledGridSize
                                val accumulatedGridY = draggingOffset.y / scaledGridSize

                                if (abs(accumulatedGridX) >= 1f || abs(accumulatedGridY) >= 1f) {
                                    val gridMoveX = accumulatedGridX.toInt()
                                    val gridMoveY = accumulatedGridY.toInt()

                                    if (gridMoveX != 0 || gridMoveY != 0) {
                                        val newX = element.position.value.x + gridMoveX
                                        val newY = element.position.value.y + gridMoveY

                                        draggingOffset = Offset(
                                            draggingOffset.x - (gridMoveX * scaledGridSize),
                                            draggingOffset.y - (gridMoveY * scaledGridSize)
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
                        if (WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.Layout) {
                            SelectionManager.select(
                                Selectable.VirtualViewportDevice(
                                    element = element as LaunchpadViewportElement
                                )
                            )
                        }
                    }
            ) {
                if (selected) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-56 / viewportState.zoom).dp)
                            .zIndex(1000f),
                    ) {
                        element.Actions(this)
                    }
                }

                element.Content()
            }
        }

        val originSizeDp = 48.dp
        // center pixel of grid (0,0) in viewport coordinates
        val centerPxX = viewportState.offset.x
        val centerPxY = viewportState.offset.y

        // Convert dp -> px using the same density used for grid (computed earlier)
        val originSizePx = (originSizeDp.value * density)
        val offsetX = (centerPxX - originSizePx / 2f).roundToInt()
        val offsetY = (centerPxY - originSizePx / 2f).roundToInt()

        AnimatedVisibility(
            visible = WorkspaceRepository.mode.collectAsState().value is WorkspaceContract.WorkspaceMode.Layout,
            modifier = Modifier
                .offset { IntOffset(offsetX, offsetY) }
                .size(originSizeDp)
                .zIndex(10000f),

            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .size(originSizeDp)
            ) {
                Text(
                    text = "0,0",
                    modifier = Modifier
                        .align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.5f)
                )
            }
        }
    }
}
