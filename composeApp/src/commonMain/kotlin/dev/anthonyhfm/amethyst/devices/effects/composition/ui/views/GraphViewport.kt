package dev.anthonyhfm.amethyst.devices.effects.composition.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.NodeRegistry
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.CableCurve
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.CableSimulator
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.CableTarget
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.DataCableGeometry
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.GRAPH_NODE_PORT_RADIUS
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.GRAPH_NODE_TITLE_HEIGHT
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.DEFAULT_GRAPH_NODE_BODY_WIDTH
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.GraphNodeShell
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.CompositionNodePicker
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.drawDataCable
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
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
    var contextMenuOffset by remember { mutableStateOf(androidx.compose.ui.unit.DpOffset.Zero) }
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
            node.position.x + (NodeRegistry.definitionFor(node)?.bodyWidth?.value
                ?: DEFAULT_GRAPH_NODE_BODY_WIDTH) - DataCableGeometry.PORT_CENTER_INSET_DP,
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
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rightClickable { position ->
                    val world = screenToWorld(position)
                    contextMenuWorldPosition = NodePosition(world.x, world.y)
                    contextMenuOffset = androidx.compose.ui.unit.DpOffset(
                        x = (position.x / densityScale).dp,
                        y = (position.y / densityScale).dp,
                    )
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
                },
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
                    device.updateGraph { current ->
                        val currentNode = current.nodes.firstOrNull { it.id == updated.id }
                            ?: return@updateGraph current
                        // Controls may change only their serialised node state. Position and
                        // graph identity always belong to the viewport's graph mutation path.
                        current.withNode(currentNode.copy(state = updated.state))
                    }
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

        CompositionNodePicker(
            visible = contextMenuVisible,
            offset = contextMenuOffset,
            selectedNode = graph.nodes.firstOrNull { it.id == selectedNodeId },
            onDismiss = { contextMenuVisible = false },
            onPickNode = ::addNode,
            onDeleteSelected = {
                val selected = graph.nodes.firstOrNull { it.id == selectedNodeId } ?: return@CompositionNodePicker
                selectedNodeId = graph.outputNodeId
                device.updateGraph { current -> current.withoutNode(selected.id) }
                contextMenuVisible = false
            },
        )
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

private fun GraphViewportState.normalized(): GraphViewportState =
    copy(zoom = 1f)
