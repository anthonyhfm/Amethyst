package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.network.presence.CollaborationPresence
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.workspace.ui.components.CursorOverlay
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.selectionBorder
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
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
    val gridColor = Color(0xFF5C6370).copy(alpha = 0.38f)
    val viewportBackground = Color(0xFF1C1C23)
    val viewportBorder = if (platform is Platform.Desktop) Color(0xFF3E4451) else Color.Transparent
    val selectionColor = Theme[colors][selectionBorder]
    val originBackground = Color(0xFF282C34)
    val originForeground = Color(0xFFABB2BF).copy(alpha = 0.82f)
    val viewportSize = remember { mutableStateOf(Size.Zero) }
    val selections by SelectionManager.selections.collectAsState()
    val remoteFocuses by CollaborationPresence.remoteFocuses.collectAsState()
    val remoteCursors by CollaborationPresence.remoteCursors.collectAsState()
    val workspaceMode by WorkspaceRepository.mode.collectAsState()

    val virtualLaunchpads = elements.filterIsInstance<LaunchpadViewportElement>()
    val isSingleVirtualDeviceMode = (platform is Platform.Android || platform is Platform.iOS) && virtualLaunchpads.size == 1

    val effectiveOffset = if (isSingleVirtualDeviceMode && viewportSize.value.width > 0f) {
        val launchpad = virtualLaunchpads.first()
        val targetCenterX = launchpad.position.value.x * gridSize + (launchpad.size.width * gridSize) / 2f
        val targetCenterY = launchpad.position.value.y * gridSize + (launchpad.size.height * gridSize) / 2f
        Offset(
            x = viewportSize.value.width / 2f - targetCenterX * viewportState.zoom,
            y = viewportSize.value.height / 2f - targetCenterY * viewportState.zoom
        )
    } else {
        viewportState.offset
    }

    LaunchedEffect(elements.size, viewportSize.value) {
        if (viewportSize.value.width <= 0f || viewportSize.value.height <= 0f) return@LaunchedEffect

        val launchpads = elements.filterIsInstance<LaunchpadViewportElement>()
        if (launchpads.isNotEmpty()) {
            if (isSingleVirtualDeviceMode) {
                val launchpad = launchpads.first()
                val targetZoom = (viewportSize.value.width - 2 * 16 * density) / (launchpad.size.width * gridSize)
                val zoomDelta = targetZoom - viewportState.zoom
                onEvent(WorkspaceContract.Event.OnZoomViewport(zoomDelta, Offset.Zero))
            } else {
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
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(viewportBackground, DefaultShape)
            .border(1.dp, viewportBorder, DefaultShape)
            .onSizeChanged { size ->
                viewportSize.value = Size(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(isSingleVirtualDeviceMode) {
                detectTransformGestures(
                    panZoomLock = false,
                    onGesture = { centroid, pan, gestureZoom, _ ->
                        if (pan != Offset.Zero && !isSingleVirtualDeviceMode) {
                            onEvent(WorkspaceContract.Event.OnPanViewport(pan))
                        }

                        if (gestureZoom != 1f) {
                            val zoomDelta = (gestureZoom - 1f)

                            onEvent(WorkspaceContract.Event.OnZoomViewport(zoomDelta, centroid))
                        }
                    }
                )
            }
            .pointerInput(viewportState.zoom, isSingleVirtualDeviceMode) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        val scrollDelta = change?.scrollDelta
                        if (scrollDelta != null && (scrollDelta.x != 0f || scrollDelta.y != 0f)) {
                            val isZoomModifier = event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed
                            if (isZoomModifier && scrollDelta.y != 0f) {
                                // Zooming: smooth exponential zoom for trackpad and scroll-wheel
                                val factor = kotlin.math.exp(-scrollDelta.y * 0.05f)
                                val zoomDelta = viewportState.zoom * (factor - 1f)
                                val mousePosition = change.position
                                onEvent(WorkspaceContract.Event.OnZoomViewport(zoomDelta, mousePosition))
                            } else if (!isZoomModifier && !isSingleVirtualDeviceMode) {
                                // Panning: allow buttery smooth two-finger panning on trackpads and mouse-wheel scrolling
                                val isHorizontalModifier = event.keyboardModifiers.isShiftPressed
                                val panX = if (isHorizontalModifier && scrollDelta.x == 0f) scrollDelta.y else scrollDelta.x
                                val panY = if (isHorizontalModifier) 0f else scrollDelta.y
                                val panOffset = Offset(-panX * 15f, -panY * 15f)
                                onEvent(WorkspaceContract.Event.OnPanViewport(panOffset))
                            }
                            change.consume()
                        }
                    }
                }
            }
            .pointerInput("collaboration-cursor", effectiveOffset, viewportState.zoom) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position ?: continue
                        val isMove = event.type == PointerEventType.Move || event.buttons.isPrimaryPressed
                        if (isMove) {
                            CollaborationPresence.sendCursorMoved(
                                x = (position.x - effectiveOffset.x) / viewportState.zoom,
                                y = (position.y - effectiveOffset.y) / viewportState.zoom
                            )
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
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
            val startX = (effectiveOffset.x % scaledGridSize) - scaledGridSize
            val startY = (effectiveOffset.y % scaledGridSize) - scaledGridSize

            var x = startX
            while (x < size.width) {
                var y = startY
                while (y < size.height) {
                    drawCircle(
                        color = gridColor,
                        radius = 2f * density * viewportState.zoom,
                        center = Offset(x, y)
                    )
                    y += scaledGridSize
                }
                x += scaledGridSize
            }
        }

        elements.forEach { element ->
            val launchpadElement = element as? LaunchpadViewportElement
            val animatedRotation by animateFloatAsState(
                targetValue = launchpadElement?.rotationDegrees?.floatValue ?: 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "launchpad-shadow-rotation",
            )

            Box(
                modifier = Modifier
                    .size(
                        width = element.size.width.dp * gridSize / density,
                        height = element.size.height.dp * gridSize / density
                    )
                    .offset {
                        val scaledGridSize = gridSize * viewportState.zoom
                        val xOffset = (element.position.value.x * scaledGridSize + effectiveOffset.x).roundToInt()
                        val yOffset = (element.position.value.y * scaledGridSize + effectiveOffset.y).roundToInt()
                        IntOffset(xOffset, yOffset)
                    }
                    .graphicsLayer {
                        scaleX = viewportState.zoom
                        scaleY = viewportState.zoom
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = animatedRotation
                        }
                        .dropShadow(
                            element.shape,
                            androidx.compose.ui.graphics.shadow.Shadow(
                                radius = 8.dp,
                                spread = 0.dp,
                                offset = androidx.compose.ui.unit.DpOffset(0.dp, 0.dp),
                                color = Color.Black.copy(alpha = 0.2f),
                            )
                        )
                        .dropShadow(
                            element.shape,
                            androidx.compose.ui.graphics.shadow.Shadow(
                                radius = 16.dp,
                                spread = -2.dp,
                                offset = androidx.compose.ui.unit.DpOffset(0.dp, 10.dp),
                                color = Color.Black.copy(alpha = 0.40f),
                            )
                        )
                )
            }
        }

        elements.forEachIndexed { index, element ->
            val selected = selections.any { it.selectionUUID == element.selectionUUID }
            val focusingUser = remoteFocuses.entries
                .firstOrNull { it.value == element.selectionUUID }
                ?.key
                ?.let { userId -> remoteCursors[userId]?.user }
            val focusColor = focusingUser?.let { Color(it.color) }

            var draggingOffset by remember { mutableStateOf(Offset.Zero) }

            BoxWithConstraints(
                modifier = Modifier
                    .offset {
                        val scaledGridSize = gridSize * viewportState.zoom
                        val xOffset = (element.position.value.x * scaledGridSize + effectiveOffset.x).roundToInt()
                        val yOffset = (element.position.value.y * scaledGridSize + effectiveOffset.y).roundToInt()
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
                                .border((2 / viewportState.zoom).dp, selectionColor, element.shape)
                        } else if (focusColor != null) {
                            Modifier.border((2 / viewportState.zoom).dp, focusColor, element.shape)
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

                                val accumulatedGridX = draggingOffset.x / gridSize
                                val accumulatedGridY = draggingOffset.y / gridSize

                                if (abs(accumulatedGridX) >= 1f || abs(accumulatedGridY) >= 1f) {
                                    val gridMoveX = accumulatedGridX.toInt()
                                    val gridMoveY = accumulatedGridY.toInt()

                                    if (gridMoveX != 0 || gridMoveY != 0) {
                                        val newX = element.position.value.x.roundToInt() + gridMoveX
                                        val newY = element.position.value.y.roundToInt() + gridMoveY

                                        draggingOffset = Offset(
                                            draggingOffset.x - (gridMoveX * gridSize),
                                            draggingOffset.y - (gridMoveY * gridSize)
                                        )

                                        onEvent(
                                            WorkspaceContract.Event.ChangeViewportElementPosition(
                                                index = index,
                                                offset = Offset(newX.toFloat(), newY.toFloat())
                                            )
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingOffset = Offset.Zero
                                onEvent(WorkspaceContract.Event.OnViewportElementMoveFinished(element.selectionUUID))
                            },
                            onDragCancel = {
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
                val launchpadElement = element as? LaunchpadViewportElement
                val animatedRotation by animateFloatAsState(
                    targetValue = launchpadElement?.rotationDegrees?.floatValue ?: 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    label = "launchpad-rotation",
                )

                Box(modifier = Modifier.graphicsLayer { rotationZ = animatedRotation }) {
                    element.Content()
                }
            }
        }

        // Action tray overlay — rendered outside the zoomed element layer so it
        // stays at a fixed screen-space size regardless of viewport zoom.
        elements.forEach { element ->
            val selected = selections.any { it.selectionUUID == element.selectionUUID }
            if (!selected) return@forEach

            var traySize by remember { mutableStateOf(Size(164f * density, 44f * density)) }

            val scaledGridSize = gridSize * viewportState.zoom
            val trayScreenCenterX = element.position.value.x * scaledGridSize + effectiveOffset.x + element.size.width * scaledGridSize / 2
            val trayScreenTopY = element.position.value.y * scaledGridSize + effectiveOffset.y

            Row(
                modifier = Modifier
                    .zIndex(2000f)
                    .offset {
                        IntOffset(
                            x = (trayScreenCenterX - traySize.width / 2).roundToInt(),
                            y = (trayScreenTopY - traySize.height - 8.dp.toPx()).roundToInt(),
                        )
                    }
                    .onSizeChanged { size ->
                        traySize = Size(size.width.toFloat(), size.height.toFloat())
                    },
            ) {
                element.Actions(this)
            }
        }

        val originSizeDp = 48.dp
        val centerPxX = effectiveOffset.x
        val centerPxY = effectiveOffset.y

        val originSizePx = (originSizeDp.value * density)
        val offsetX = (centerPxX - originSizePx / 2f).roundToInt()
        val offsetY = (centerPxY - originSizePx / 2f).roundToInt()

        AnimatedVisibility(
            visible = workspaceMode is WorkspaceContract.WorkspaceMode.Layout,
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
                    .background(originBackground)
                    .border(1.dp, viewportBorder, CircleShape)
                    .size(originSizeDp)
            ) {
                Text(
                    text = "0,0",
                    modifier = Modifier
                        .align(Alignment.Center),
                    style = Theme[typography][small],
                    color = originForeground,
                )
            }
        }

        CursorOverlay(
            cursors = remoteCursors.mapValues { (_, cursor) ->
                cursor.copy(
                    x = cursor.x * viewportState.zoom + effectiveOffset.x,
                    y = cursor.y * viewportState.zoom + effectiveOffset.y
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(20_000f),
        )
    }
}
