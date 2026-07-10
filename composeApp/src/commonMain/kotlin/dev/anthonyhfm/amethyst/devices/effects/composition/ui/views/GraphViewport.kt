package dev.anthonyhfm.amethyst.devices.effects.composition.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Flip
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material.icons.twotone.RotateLeft
import androidx.compose.material.icons.twotone.SettingsEthernet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.GraphViewportState
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.NodePosition
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.withConnection
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.withNode
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.withViewport
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.withoutConnection
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.withoutNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.MirrorNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.SymmetryNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.NodeRegistry
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.OutputNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.RotateNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.ScannerNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.CableCurve
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.CableSimulator
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.CableTarget
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.DataCableGeometry
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.GRAPH_NODE_PORT_RADIUS
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.GRAPH_NODE_TITLE_HEIGHT
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.GRAPH_NODE_WIDTH
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.GraphNodeShell
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.drawDataCable
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuContent
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItemVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import kotlin.math.roundToInt

private const val GRID_STEP_DP = 40f
private const val PORT_HIT_SLOP_DP = 18f
private const val DRAG_CABLE_ID = "__drag__"

/**
 * An in-progress cable drag. Represents either a brand-new cable pulled out of an output port
 * ([grabbedConnectionId] == null) or an existing cable detached at one end.
 *
 * One end is *anchored* to [anchorNodeId] and the other endpoint follows the cursor.
 * [grabbedInput] == true means the input end is in hand; false means the output end is in hand.
 * [start]/[end] are the output-/input-end screen
 * positions; only the free one moves while dragging.
 */
private data class CableDrag(
    val anchorNodeId: String,
    val grabbedInput: Boolean,
    val grabbedConnectionId: String?,
    val start: Offset,
    val end: Offset,
)

@Composable
fun GraphViewport(
    device: CompositionChainDevice,
    modifier: Modifier = Modifier,
) {
    val deviceState by device.state.collectAsState()
    val graph = deviceState.graph
    var viewportSize by remember { mutableStateOf(Size.Zero) }
    var selectedNodeId by remember(graph.outputNodeId) { mutableStateOf(graph.outputNodeId) }
    var cableDrag by remember { mutableStateOf<CableDrag?>(null) }
    var contextMenuVisible by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var contextMenuWorldPosition by remember { mutableStateOf(NodePosition(96f, 96f)) }
    val externalViewport = graph.viewport.normalized()
    var viewport by remember { mutableStateOf(externalViewport) }

    LaunchedEffect(externalViewport) {
        viewport = externalViewport
    }

    val density = LocalDensity.current
    val densityScale = density.density

    fun screenToWorld(screen: Offset): Offset =
        Offset(
            x = (screen.x - viewport.offsetX) / (densityScale * viewport.zoom),
            y = (screen.y - viewport.offsetY) / (densityScale * viewport.zoom),
        )

    fun worldToScreen(world: Offset): Offset =
        Offset(
            x = world.x * densityScale * viewport.zoom + viewport.offsetX,
            y = world.y * densityScale * viewport.zoom + viewport.offsetY,
        )

    fun updateViewport(next: GraphViewportState) {
        val normalized = next.normalized()
        viewport = normalized
        device.updateGraph(undoable = false) { it.withViewport(normalized) }
    }

    fun addNode(type: String) {
        val node = CompositionNode(type = type, position = contextMenuWorldPosition)
        selectedNodeId = node.id
        device.updateGraph { current -> current.copy(nodes = current.nodes + node) }
        contextMenuVisible = false
    }

    fun inputPortWorld(node: CompositionNode): Offset =
        Offset(
            node.position.x + DataCableGeometry.PORT_CENTER_INSET_DP,
            node.position.y + GRAPH_NODE_TITLE_HEIGHT / 2f,
        )

    fun outputPortWorld(node: CompositionNode): Offset =
        Offset(
            node.position.x + GRAPH_NODE_WIDTH - DataCableGeometry.PORT_CENTER_INSET_DP,
            node.position.y + GRAPH_NODE_TITLE_HEIGHT / 2f,
        )

    fun inputPortScreenPosition(node: CompositionNode): Offset = worldToScreen(inputPortWorld(node))

    fun outputPortScreenPosition(node: CompositionNode): Offset = worldToScreen(outputPortWorld(node))

    fun portHitRadiusPx(): Float = maxOf(
        (GRAPH_NODE_PORT_RADIUS + PORT_HIT_SLOP_DP) * densityScale * viewport.zoom,
        DataCableGeometry.END_HANDLE_DIAMETER_DP * densityScale / 2f,
    )

    fun inputNodeAt(screen: Offset): CompositionNode? =
        graph.nodes
            .filter { NodeRegistry.definitionFor(it)?.hasInput == true }
            .firstOrNull { node -> (inputPortScreenPosition(node) - screen).getDistance() <= portHitRadiusPx() }

    fun outputNodeAt(screen: Offset): CompositionNode? =
        graph.nodes
            .filter { NodeRegistry.definitionFor(it)?.hasOutput == true }
            .firstOrNull { node -> (outputPortScreenPosition(node) - screen).getDistance() <= portHitRadiusPx() }

    // Commits a finished cable drag as a single undoable graph edit: detach the grabbed cable (if
    // any) and, when released over a compatible port, reconnect it. Releasing over empty space
    // leaves the cable disconnected. Because we never mutate the graph mid-drag, the undo snapshot
    // captures the original attached state, so one undo restores it exactly.
    fun commitDrag(drag: CableDrag, targetNodeId: String?) {
        device.updateGraph { current ->
            val base = drag.grabbedConnectionId?.let { current.withoutConnection(it) } ?: current
            when {
                targetNodeId == null -> base
                drag.grabbedInput -> base.withConnection(drag.anchorNodeId, targetNodeId)
                else -> base.withConnection(targetNodeId, drag.anchorNodeId)
            }
        }
    }

    val simulator = remember { CableSimulator() }
    var cableCurves by remember { mutableStateOf<List<CableCurve>>(emptyList()) }
    val graphState = rememberUpdatedState(graph)
    val dragState = rememberUpdatedState(cableDrag)
    // Physics stays in world space, so viewport transforms never disturb a settled cable.
    val cableThicknessPx = 2.25f * densityScale * viewport.zoom

    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastFrame == 0L) 0f else (now - lastFrame) / 1_000_000_000f
                lastFrame = now

                val current = graphState.value
                val activeDrag = dragState.value
                val suppressedConnectionId = activeDrag?.grabbedConnectionId
                val targets = current.connections
                    .filterNot { it.id == suppressedConnectionId }
                    .mapNotNull { connection ->
                        val from = current.nodes.firstOrNull { it.id == connection.fromNodeId } ?: return@mapNotNull null
                        val to = current.nodes.firstOrNull { it.id == connection.toNodeId } ?: return@mapNotNull null
                        CableTarget(
                            id = connection.id,
                            start = outputPortWorld(from),
                            end = inputPortWorld(to),
                            color = DataCableGeometry.DATA_COLOR,
                        )
                    }.toMutableList()

                // In-progress cables use the same restrained physics as committed connections.
                activeDrag?.let { drag ->
                    val anchor = current.nodes.firstOrNull { it.id == drag.anchorNodeId }
                    val startWorld = if (drag.grabbedInput) {
                        anchor?.let { outputPortWorld(it) } ?: screenToWorld(drag.start)
                    } else {
                        screenToWorld(drag.start)
                    }
                    val endWorld = if (drag.grabbedInput) {
                        screenToWorld(drag.end)
                    } else {
                        anchor?.let { inputPortWorld(it) } ?: screenToWorld(drag.end)
                    }
                    targets += CableTarget(
                        id = DRAG_CABLE_ID,
                        start = startWorld,
                        end = endWorld,
                        color = DataCableGeometry.DRAG_COLOR,
                    )
                }

                val next = simulator.step(targets, dt)
                // Only trigger a redraw while cables are actually in motion or the set changed.
                if (!simulator.settled || next.size != cableCurves.size) {
                    cableCurves = next
                }
            }
        }
    }

    val highlightedInputNodeId = cableDrag
        ?.takeIf { it.grabbedInput }
        ?.let { inputNodeAt(it.end)?.id }
    val highlightedOutputNodeId = cableDrag
        ?.takeIf { !it.grabbedInput }
        ?.let { outputNodeAt(it.start)?.id }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(DefaultShape)
            .clipToBounds()
            .background(Color(0xFF151820), DefaultShape)
            .border(1.dp, Theme[colors][border], DefaultShape)
            .onSizeChanged { viewportSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .rightClickable { position ->
                val world = screenToWorld(position)
                contextMenuWorldPosition = NodePosition(world.x, world.y)
                contextMenuOffset = IntOffset(position.x.roundToInt(), position.y.roundToInt())
                contextMenuVisible = true
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        selectedNodeId = ""
                        cableDrag = null
                        contextMenuVisible = false
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = true) { _, pan, _, _ ->
                    if (pan != Offset.Zero) {
                        selectedNodeId = ""
                        contextMenuVisible = false
                        updateViewport(
                            viewport.copy(
                                offsetX = viewport.offsetX + pan.x,
                                offsetY = viewport.offsetY + pan.y,
                            )
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        val scroll = change?.scrollDelta ?: Offset.Zero
                        if (scroll != Offset.Zero) {
                            val pointerChange = change ?: continue
                            updateViewport(
                                viewport.copy(
                                    offsetX = viewport.offsetX - scroll.x * 15f,
                                    offsetY = viewport.offsetY - scroll.y * 15f,
                                )
                            )
                            pointerChange.consume()
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val gridColor = Color(0xFF5C6370).copy(alpha = 0.34f)
            val scaledStep = GRID_STEP_DP * densityScale * viewport.zoom
            if (scaledStep >= 8f) {
                var x = (viewport.offsetX % scaledStep) - scaledStep
                while (x < size.width + scaledStep) {
                    var y = (viewport.offsetY % scaledStep) - scaledStep
                    while (y < size.height + scaledStep) {
                        drawCircle(color = gridColor, radius = 2f, center = Offset(x, y))
                        y += scaledStep
                    }
                    x += scaledStep
                }
            }

        }

        graph.nodes.forEach { node ->
            val screen = worldToScreen(Offset(node.position.x, node.position.y))
            val connectedInput = graph.connections.any { it.toNodeId == node.id }
            val connectedOutput = graph.connections.any { it.fromNodeId == node.id }
            GraphNodeShell(
                node = node,
                selected = node.id == selectedNodeId,
                connectedInput = connectedInput,
                connectedOutput = connectedOutput,
                modifier = Modifier.offset {
                    IntOffset(screen.x.roundToInt(), screen.y.roundToInt())
                }
                    .graphicsLayer {
                        scaleX = viewport.zoom
                        scaleY = viewport.zoom
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
                onSelect = { selectedNodeId = node.id },
                onDragBy = { dx, dy ->
                    device.updateGraph(undoable = false) { current ->
                        val currentNode = current.nodes.firstOrNull { it.id == node.id } ?: return@updateGraph current
                        current.withNode(
                            currentNode.copy(
                                position = NodePosition(
                                    x = currentNode.position.x + dx / (densityScale * viewport.zoom),
                                    y = currentNode.position.y + dy / (densityScale * viewport.zoom),
                                )
                            )
                        )
                    }
                },
                onInputPortClick = {
                    val drag = cableDrag ?: return@GraphNodeShell
                    if (drag.grabbedInput) {
                        commitDrag(drag, node.id)
                        cableDrag = null
                    }
                },
                onInputPortDragStart = {
                    // Pull a new cable from an input: the free output endpoint follows the cursor.
                    selectedNodeId = node.id
                    val anchor = inputPortScreenPosition(node)
                    cableDrag = CableDrag(
                        anchorNodeId = node.id,
                        grabbedInput = false,
                        grabbedConnectionId = null,
                        start = anchor,
                        end = anchor,
                    )
                },
                onInputPortDragBy = { dx, dy ->
                    // Deltas are reported inside the node's zoom graphicsLayer; scale to screen
                    // space. The free (output) end is `start` when the input is anchored.
                    val zoom = viewport.zoom
                    cableDrag = cableDrag?.let { it.copy(start = it.start + Offset(dx * zoom, dy * zoom)) }
                },
                onInputPortDragEnd = {
                    val drag = cableDrag
                    if (drag != null) {
                        commitDrag(drag, outputNodeAt(drag.start)?.id)
                    }
                    cableDrag = null
                },
                onOutputPortDragStart = {
                    selectedNodeId = node.id
                    val start = outputPortScreenPosition(node)
                    cableDrag = CableDrag(
                        anchorNodeId = node.id,
                        grabbedInput = true,
                        grabbedConnectionId = null,
                        start = start,
                        end = start,
                    )
                },
                onOutputPortDragBy = { dx, dy ->
                    // Deltas are reported inside the node's zoom graphicsLayer, so scale back
                    // to screen space to keep the cable end under the cursor.
                    val zoom = viewport.zoom
                    cableDrag = cableDrag?.let { it.copy(end = it.end + Offset(dx * zoom, dy * zoom)) }
                },
                onOutputPortDragEnd = {
                    val drag = cableDrag
                    if (drag != null) {
                        commitDrag(drag, inputNodeAt(drag.end)?.id)
                    }
                    cableDrag = null
                },
                inputPortHighlighted = node.id == highlightedInputNodeId,
                outputPortHighlighted = node.id == highlightedOutputNodeId,
                onNodeChange = { updated ->
                    device.updateGraph { current -> current.withNode(updated) }
                },
            )
        }

        // Foreground connection layer: cables remain visible across node surfaces and all the way
        // into their abstract port centres. This canvas has no pointer input, so node interaction
        // and the endpoint handles above it keep their existing priority.
        Canvas(modifier = Modifier.fillMaxSize()) {
            cableCurves.forEach { world ->
                val screenCurve = CableCurve(
                    start = worldToScreen(world.start),
                    mid = worldToScreen(world.mid),
                    end = worldToScreen(world.end),
                    color = world.color,
                )
                drawDataCable(
                    screenCurve,
                    thicknessPx = cableThicknessPx,
                    dpToPx = densityScale * viewport.zoom,
                )
            }
        }

        // Invisible screen-space handles keep the abstract endpoints easy to rewire at every zoom.
        val handleDiameterDp = DataCableGeometry.END_HANDLE_DIAMETER_DP
        graph.connections.forEach { connection ->
            val from = graph.nodes.firstOrNull { it.id == connection.fromNodeId } ?: return@forEach
            val to = graph.nodes.firstOrNull { it.id == connection.toNodeId } ?: return@forEach
            val outScreen = outputPortScreenPosition(from)
            val inScreen = inputPortScreenPosition(to)

            // Input endpoint: keep the output anchored and rewire the destination.
            CableEndHandle(
                centerScreen = inScreen,
                diameterDp = handleDiameterDp,
                density = densityScale,
                key = "in:${connection.id}",
                onDragStart = {
                    selectedNodeId = ""
                    cableDrag = CableDrag(
                        anchorNodeId = from.id,
                        grabbedInput = true,
                        grabbedConnectionId = connection.id,
                        start = outputPortScreenPosition(from),
                        end = inputPortScreenPosition(to),
                    )
                },
                onDrag = { amount -> cableDrag = cableDrag?.let { it.copy(end = it.end + amount) } },
                onDragEnd = {
                    cableDrag?.let { commitDrag(it, inputNodeAt(it.end)?.id) }
                    cableDrag = null
                },
                onDragCancel = { cableDrag = null },
            )

            // Output endpoint: keep the input anchored and rewire the source.
            CableEndHandle(
                centerScreen = outScreen,
                diameterDp = handleDiameterDp,
                density = densityScale,
                key = "out:${connection.id}",
                onDragStart = {
                    selectedNodeId = ""
                    cableDrag = CableDrag(
                        anchorNodeId = to.id,
                        grabbedInput = false,
                        grabbedConnectionId = connection.id,
                        start = outputPortScreenPosition(from),
                        end = inputPortScreenPosition(to),
                    )
                },
                onDrag = { amount -> cableDrag = cableDrag?.let { it.copy(start = it.start + amount) } },
                onDragEnd = {
                    cableDrag?.let { commitDrag(it, outputNodeAt(it.start)?.id) }
                    cableDrag = null
                },
                onDragCancel = { cableDrag = null },
            )
        }

        if (contextMenuVisible) {
            Popup(
                popupPositionProvider = GraphContextMenuPositionProvider(contextMenuOffset),
                onDismissRequest = { contextMenuVisible = false },
                properties = PopupProperties(focusable = true),
            ) {
                ContextMenuContent {
                    AddNodeMenuContent(
                        selectedNode = graph.nodes.firstOrNull { it.id == selectedNodeId },
                        onAddScanner = { addNode(ScannerNode.type) },
                        onAddRotate = { addNode(RotateNode.type) },
                        onAddMirror = { addNode(MirrorNode.type) },
                        onAddSymmetry = { addNode(SymmetryNode.type) },
                        onDeleteSelected = {
                            val selected = graph.nodes.firstOrNull { it.id == selectedNodeId } ?: return@AddNodeMenuContent
                            selectedNodeId = graph.outputNodeId
                            device.updateGraph { current -> current.withoutNode(selected.id) }
                            contextMenuVisible = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CableEndHandle(
    centerScreen: Offset,
    diameterDp: Float,
    density: Float,
    key: Any,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val halfPx = diameterDp * density / 2f
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerScreen.x - halfPx).roundToInt(),
                    (centerScreen.y - halfPx).roundToInt(),
                )
            }
            .size(diameterDp.dp)
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            },
    )
}

@Composable
private fun ColumnScope.AddNodeMenuContent(
    selectedNode: CompositionNode?,
    onAddScanner: () -> Unit,
    onAddRotate: () -> Unit,
    onAddMirror: () -> Unit,
    onAddSymmetry: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    GraphContextMenuItem("Add Scanner", Icons.TwoTone.SettingsEthernet, onAddScanner)
    GraphContextMenuItem("Add Rotate", Icons.TwoTone.RotateLeft, onAddRotate)
    GraphContextMenuItem("Add Mirror", Icons.TwoTone.Flip, onAddMirror)
    GraphContextMenuItem("Add Symmetry", Icons.TwoTone.GridView, onAddSymmetry)
    if (selectedNode != null && selectedNode.type != OutputNode.type) {
        ContextMenuSeparator()
        GraphContextMenuItem(
            label = "Delete ${selectedNode.label}",
            icon = Icons.TwoTone.Delete,
            onClick = onDeleteSelected,
            variant = ContextMenuItemVariant.Destructive,
        )
    }
}

@Composable
private fun GraphContextMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    variant: ContextMenuItemVariant = ContextMenuItemVariant.Default,
) {
    val contentColor = if (variant == ContextMenuItemVariant.Destructive) {
        Theme[colors][destructive]
    } else {
        Theme[colors][popoverForeground]
    }

    ContextMenuItem(onClick = onClick, variant = variant) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
            Text(text = label, color = contentColor)
        }
    }
}

private class GraphContextMenuPositionProvider(
    private val offset: IntOffset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + offset.x
        val y = anchorBounds.top + offset.y
        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

private fun GraphViewportState.normalized(): GraphViewportState =
    copy(zoom = 1f)
