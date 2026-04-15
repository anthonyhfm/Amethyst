package dev.anthonyhfm.amethyst.gem.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemConnection
import dev.anthonyhfm.amethyst.gem.GemNodeDescriptor
import dev.anthonyhfm.amethyst.gem.GemNodeInstance
import dev.anthonyhfm.amethyst.gem.GemNodePosition
import dev.anthonyhfm.amethyst.gem.GemNodeRegistry
import dev.anthonyhfm.amethyst.gem.GemPin
import dev.anthonyhfm.amethyst.gem.GemPinDirection
import dev.anthonyhfm.amethyst.gem.GemPinType
import dev.anthonyhfm.amethyst.gem.GemPinRef
import dev.anthonyhfm.amethyst.gem.GemValidationErrorCode
import dev.anthonyhfm.amethyst.gem.GemValidationError
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.GemValueType
import dev.anthonyhfm.amethyst.gem.data.GemJsonPersistence
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Card
import dev.anthonyhfm.amethyst.ui.components.primitives.CardContent
import dev.anthonyhfm.amethyst.ui.components.primitives.Command
import dev.anthonyhfm.amethyst.ui.components.primitives.CommandEmpty
import dev.anthonyhfm.amethyst.ui.components.primitives.CommandGroup
import dev.anthonyhfm.amethyst.ui.components.primitives.CommandInput
import dev.anthonyhfm.amethyst.ui.components.primitives.CommandItem
import dev.anthonyhfm.amethyst.ui.components.primitives.CommandList
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Dialog
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogContent
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyMuted
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographySmall
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary
import kotlin.math.roundToInt

@Composable
internal fun GemCanvasEditor(
    session: GemEditorSession,
    registry: GemNodeRegistry,
    nodePaletteVisible: Boolean,
    onNodePaletteVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    fitToContentTrigger: Int = 0
) {
    val sessionState by session.state.collectAsState()
    val asset = sessionState.editedAsset
    val graph = asset.graph(sessionState.selection.graphId) ?: asset.definition.rootGraph
    val density = LocalDensity.current.density
    var canvasSize by remember(graph.id, asset.metadata.id) { mutableStateOf(IntSize.Zero) }
    var viewport by remember(graph.id, asset.metadata.id) { mutableStateOf(GemCanvasViewportState()) }

    LaunchedEffect(fitToContentTrigger) {
        if (fitToContentTrigger > 0) {
            viewport = fitToContent(graph.nodes, canvasSize) ?: viewport
        }
    }
    var boxSelection by remember(graph.id, asset.metadata.id) { mutableStateOf<GemCanvasBoxSelection?>(null) }
    var nodeDrag by remember(graph.id, asset.metadata.id) { mutableStateOf<GemCanvasNodeDrag?>(null) }
    var connectionDrag by remember(graph.id, asset.metadata.id) { mutableStateOf<GemCanvasConnectionDrag?>(null) }
    var nodeCreationAnchorWorld by remember(graph.id, asset.metadata.id) { mutableStateOf<Offset?>(null) }
    var lastEmptyCanvasTap by remember(graph.id, asset.metadata.id) { mutableStateOf<GemCanvasTapState?>(null) }
    // Positions are world-space: viewport does not affect them, so omit it from the key to
    // avoid reallocating this map on every pan/zoom gesture.
    val renderedNodePositions = remember(graph, nodeDrag) {
        graph.nodes.associate { node -> node.id to node.layout.position.toOffset() }
    }
    val previewTarget = remember(asset, graph, registry, connectionDrag, renderedNodePositions, viewport) {
        connectionDrag?.let { drag ->
            findPreviewTarget(
                asset = asset,
                graph = graph,
                registry = registry,
                source = drag.source,
                currentWorld = viewport.screenToWorld(drag.currentScreen),
                previewTolerance = GEM_CANVAS_CONNECTION_PREVIEW_TOLERANCE / viewport.zoom,
                renderedNodePositions = renderedNodePositions
            )
        }
    }
    val accentColor = Theme[colors][accent]
    val destructiveColor = Theme[colors][destructive]
    val selectedNodeIds = sessionState.selection.nodeIds
    val selectedConnectionIds = sessionState.selection.connectionIds
    val previewStatusText = previewTarget?.evaluation?.takeIf { !it.isValid }?.message
    // Pre-index connections by target nodeId so per-node lookup is O(1) instead of O(M).
    val connectedInputPinIdsByNodeId = remember(graph.connections) {
        graph.connections
            .groupBy { it.to.nodeId }
            .mapValues { (_, conns) -> conns.mapTo(linkedSetOf()) { it.to.pinId } }
    }

    fun completeNodeDrag() {
        if (nodeDrag != null) {
            session.commitTransientChanges()
        }
        nodeDrag = null
    }

    fun completeConnectionDrag(releaseScreen: Offset? = connectionDrag?.currentScreen) {
        val drag = connectionDrag
        val evaluation = if (drag != null && releaseScreen != null) {
            findPreviewTarget(
                asset = asset,
                graph = graph,
                registry = registry,
                source = drag.source,
                currentWorld = viewport.screenToWorld(releaseScreen),
                previewTolerance = GEM_CANVAS_CONNECTION_PREVIEW_TOLERANCE / viewport.zoom,
                renderedNodePositions = renderedNodePositions
            )?.evaluation
        } else {
            null
        }
        if (evaluation?.isValid == true) {
            session.connect(
                connection = GemConnection(
                    id = UUID.randomUUID(),
                    from = evaluation.normalizedConnection!!.from,
                    to = evaluation.normalizedConnection.to
                ),
                graphId = graph.id
            )
            session.setSelection(
                session.state.value.selection.copy(
                    graphId = graph.id,
                    connectionIds = emptySet()
                )
            )
        }
        connectionDrag = null
    }

    LaunchedEffect(graph.id, nodePaletteVisible) {
        if (nodePaletteVisible) {
            connectionDrag = null
            boxSelection = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(DefaultShape)
            .border(1.dp, Theme[colors][border], DefaultShape)
            .background(Theme[colors][card])
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF111218), DefaultShape)
                .border(1.dp, Color(0xFF23242C), DefaultShape)
                .onSizeChanged { canvasSize = it }
                .pointerInput(graph.id, nodePaletteVisible) {
                    if (!nodePaletteVisible) {
                        detectTransformGestures(
                            panZoomLock = false,
                            onGesture = { centroid, pan, gestureZoom, _ ->
                                if (pan != Offset.Zero) {
                                    viewport = viewport.pan(pan)
                                }
                                if (gestureZoom != 1f) {
                                    viewport = viewport.zoomBy(gestureZoom - 1f, centroid)
                                }
                            }
                        )
                    }
                }
                .pointerInput(graph.id, nodePaletteVisible) {
                    if (!nodePaletteVisible) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val scrollDelta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                                if (scrollDelta.y != 0f) {
                                    viewport = viewport.zoomBy(
                                        delta = -scrollDelta.y * 0.05f,
                                        focus = event.changes.first().position
                                    )
                                }
                            }
                        }
                    }
                }
                .pointerInput(graph.id, nodePaletteVisible) {
                    if (!nodePaletteVisible) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Final)
                                val releasedChange = event.changes.firstOrNull { it.previousPressed && !it.pressed }
                                if (releasedChange != null) {
                                    if (connectionDrag != null) {
                                        completeConnectionDrag(releaseScreen = releasedChange.position)
                                    }
                                    if (nodeDrag != null) {
                                        completeNodeDrag()
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(graph, sessionState.selection, nodePaletteVisible) {
                        if (!nodePaletteVisible) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val additive = ModifierKeysState.isCtrlPressed || ModifierKeysState.isMetaPressed
                                val panCanvas = !ModifierKeysState.isShiftPressed
                                val startScreen = down.position
                                val startWorld = viewport.screenToWorld(startScreen)
                                var currentScreen = startScreen
                                var hasDragged = false
                                var releaseTimeMillis = down.uptimeMillis

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        releaseTimeMillis = change.uptimeMillis
                                        break
                                    }

                                    val deltaScreen = change.position - currentScreen
                                    currentScreen = change.position
                                    if (!hasDragged && deltaScreen.getDistance() > 1f) {
                                        hasDragged = true
                                    }

                                    if (panCanvas) {
                                        viewport = viewport.pan(deltaScreen)
                                    } else {
                                        boxSelection = GemCanvasBoxSelection(
                                            start = startWorld,
                                            end = viewport.screenToWorld(currentScreen),
                                            additive = additive
                                        )
                                    }
                                    change.consume()
                                }

                                if (panCanvas && hasDragged) {
                                    boxSelection = null
                                    lastEmptyCanvasTap = null
                                    return@awaitEachGesture
                                }

                                val selectionRect = boxSelection?.rect
                                if (hasDragged && selectionRect != null) {
                                    lastEmptyCanvasTap = null
                                    val boxSelectedNodeIds = selectGemCanvasNodes(
                                        graph = graph,
                                        selectionRect = selectionRect
                                    )
                                    val combinedNodeIds = if (additive) {
                                        (session.state.value.selection.nodeIds + boxSelectedNodeIds).toLinkedSet()
                                    } else {
                                        boxSelectedNodeIds.toLinkedSet()
                                    }
                                    session.setSelection(
                                        session.state.value.selection.copy(
                                            graphId = graph.id,
                                            nodeIds = combinedNodeIds,
                                            connectionIds = emptySet()
                                        )
                                    )
                                } else {
                                    val tappedConnection = findGemCanvasConnectionAt(
                                        graph = graph,
                                        worldPoint = startWorld,
                                        tolerance = GEM_CANVAS_CONNECTION_HIT_TOLERANCE / viewport.zoom,
                                        nodePositions = renderedNodePositions
                                    )
                                    if (tappedConnection != null) {
                                        nodeCreationAnchorWorld = startWorld
                                        lastEmptyCanvasTap = null
                                        val nextConnections = if (additive) {
                                            session.state.value.selection.connectionIds.toggle(tappedConnection.id)
                                        } else {
                                            linkedSetOf(tappedConnection.id)
                                        }
                                        session.setSelection(
                                            session.state.value.selection.copy(
                                                graphId = graph.id,
                                                connectionIds = nextConnections,
                                                nodeIds = if (additive) session.state.value.selection.nodeIds else emptySet()
                                            )
                                        )
                                    } else if (!additive) {
                                        nodeCreationAnchorWorld = startWorld
                                        val isDoubleTap = lastEmptyCanvasTap?.matches(
                                            worldPosition = startWorld,
                                            timeMillis = releaseTimeMillis
                                        ) == true
                                        lastEmptyCanvasTap = if (isDoubleTap) {
                                            null
                                        } else {
                                            GemCanvasTapState(
                                                worldPosition = startWorld,
                                                timeMillis = releaseTimeMillis
                                            )
                                        }
                                        session.setSelection(
                                            GemEditorSelection(graphId = graph.id)
                                        )
                                        if (isDoubleTap) {
                                            onNodePaletteVisibilityChange(true)
                                        }
                                    } else {
                                        lastEmptyCanvasTap = null
                                    }
                                }

                                boxSelection = null
                            }
                        }
                    }
            ) {
                            drawGrid(
                                size = size,
                                viewport = viewport
                            )

                            graph.connections.forEach { connection ->
                                val fromNode = graph.node(connection.from.nodeId) ?: return@forEach
                                val toNode = graph.node(connection.to.nodeId) ?: return@forEach
                                val from = gemCanvasPinPosition(
                                    node = fromNode,
                                    pinId = connection.from.pinId,
                                    position = renderedNodePositions.getValue(fromNode.id),
                                    density = density
                                ) ?: return@forEach
                                val to = gemCanvasPinPosition(
                                    node = toNode,
                                    pinId = connection.to.pinId,
                                    position = renderedNodePositions.getValue(toNode.id),
                                    density = density
                                ) ?: return@forEach
                                drawConnection(
                                    start = viewport.worldToScreen(from),
                                    end = viewport.worldToScreen(to),
                                     color = if (connection.id in selectedConnectionIds) {
                                        accentColor
                                     } else {
                                        Color(0xFF7C87A4)
                                     },
                                    width = if (connection.id in selectedConnectionIds) 3f else 2f
                                )
                            }

                            connectionDrag?.let { drag ->
                                val previewColor = when {
                                    previewTarget?.evaluation?.isValid == true -> Color(0xFF34D399)
                                    previewTarget != null -> destructiveColor
                                    else -> Color(0xFFA5B4FC)
                                }
                                val sourceNode = graph.node(drag.source.nodeId)
                                val source = sourceNode?.let {
                                    gemCanvasPinPosition(
                                        node = it,
                                        pinId = drag.source.pinId,
                                        position = renderedNodePositions.getValue(it.id),
                                        density = density
                                    )
                                }
                                if (source != null) {
                                    drawConnection(
                                        start = viewport.worldToScreen(source),
                                        end = drag.currentScreen,
                                        color = previewColor,
                                        width = 2.5f
                                    )
                                }
                            }

                            boxSelection?.let { selection ->
                                val rect = selection.rect
                                val topLeft = viewport.worldToScreen(rect.topLeft)
                                val bottomRight = viewport.worldToScreen(rect.bottomRight)
                                val drawRect = Rect(topLeft, bottomRight)
                                drawRect(
                                    color = accentColor.copy(alpha = 0.14f),
                                    topLeft = drawRect.topLeft,
                                    size = drawRect.size
                                )
                                drawRect(
                                    color = accentColor,
                                    topLeft = drawRect.topLeft,
                                    size = drawRect.size,
                                    style = Stroke(width = 1.5f)
                                )
                            }
            }

            if (graph.nodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TypographyMuted("The graph is empty. Double-click the canvas or press ⌘/Ctrl + K to place your first node.")
                }
            }

            graph.nodes.forEach { node ->
                val descriptor = registry.find(node.type)
                val connectedInputPinIds = connectedInputPinIdsByNodeId[node.id] ?: emptySet()

                // Viewport culling: skip nodes entirely outside the visible canvas area.
                // Using a generous margin so nodes near the edge are not culled while still
                // partially in view. This avoids composing hidden node cards for large graphs.
                val nodeWorldPos = renderedNodePositions.getValue(node.id)
                val nodeMetrics = measureGemCanvasNode(node, density)
                val nodeScreenTopLeft = viewport.worldToScreen(nodeWorldPos)
                val nodeScreenWidth = nodeMetrics.width * viewport.zoom
                val nodeScreenHeight = nodeMetrics.height * viewport.zoom
                val cullingMargin = 200f
                if (nodeScreenTopLeft.x + nodeScreenWidth < -cullingMargin ||
                    nodeScreenTopLeft.y + nodeScreenHeight < -cullingMargin ||
                    nodeScreenTopLeft.x > canvasSize.width + cullingMargin ||
                    nodeScreenTopLeft.y > canvasSize.height + cullingMargin
                ) {
                    return@forEach
                }

                GemCanvasNodeCard(
                    node = node,
                    descriptor = descriptor,
                    position = renderedNodePositions.getValue(node.id),
                    viewport = viewport,
                    selected = node.id in selectedNodeIds,
                    density = density,
                    connectedInputPinIds = connectedInputPinIds,
                    sourceConnectionPin = connectionDrag?.source,
                    previewTarget = previewTarget,
                    onClick = {
                        nodeCreationAnchorWorld = nodeCreationAnchor(node, renderedNodePositions.getValue(node.id))
                        val additive = ModifierKeysState.isCtrlPressed ||
                            ModifierKeysState.isMetaPressed ||
                            ModifierKeysState.isShiftPressed
                        val nextNodeIds = if (additive) {
                            session.state.value.selection.nodeIds.toggle(node.id)
                        } else {
                            linkedSetOf(node.id)
                        }
                        session.setSelection(
                            session.state.value.selection.copy(
                                graphId = graph.id,
                                nodeIds = nextNodeIds,
                                connectionIds = if (additive) session.state.value.selection.connectionIds else emptySet()
                            )
                        )
                    },
                    onStartDrag = {
                        if (nodeDrag != null) {
                            completeNodeDrag()
                        }
                        nodeCreationAnchorWorld = nodeCreationAnchor(node, renderedNodePositions.getValue(node.id))
                        lastEmptyCanvasTap = null
                        val activeNodeIds = if (node.id in session.state.value.selection.nodeIds) {
                            session.state.value.selection.nodeIds.toLinkedSet()
                        } else {
                            linkedSetOf(node.id)
                        }
                        if (activeNodeIds != session.state.value.selection.nodeIds) {
                            session.setSelection(
                                session.state.value.selection.copy(
                                    graphId = graph.id,
                                    nodeIds = activeNodeIds,
                                    connectionIds = emptySet()
                                )
                            )
                        }
                        nodeDrag = GemCanvasNodeDrag(
                            graphId = graph.id,
                            nodeIds = activeNodeIds,
                            startScreenPositions = activeNodeIds.associateWith { nodeId ->
                                viewport.worldToScreen(renderedNodePositions.getValue(nodeId))
                            },
                            screenDelta = Offset.Zero
                        )
                    },
                    onDrag = { dragAmount ->
                        val screenDragAmount = gemCanvasScaledDragDeltaToScreenSpace(
                            dragDelta = dragAmount,
                            viewport = viewport
                        )
                        nodeDrag?.let { dragState ->
                            val nextScreenDelta = dragState.screenDelta + screenDragAmount
                            nodeDrag = dragState.copy(screenDelta = nextScreenDelta)
                            session.moveNodesTransient(
                                positions = dragState.startScreenPositions.mapValues { (_, startScreen) ->
                                    viewport.screenToWorld(startScreen + nextScreenDelta).toGemNodePosition()
                                },
                                graphId = dragState.graphId
                            )
                        }
                    },
                    onEndDrag = {
                        completeNodeDrag()
                    },
                    onCancelDrag = {
                        completeNodeDrag()
                    },
                    onStartConnection = { pinRef, startWorld ->
                        nodeCreationAnchorWorld = startWorld
                        lastEmptyCanvasTap = null
                        connectionDrag = GemCanvasConnectionDrag(
                            source = pinRef,
                            startScreen = viewport.worldToScreen(startWorld),
                            screenDelta = Offset.Zero
                        )
                    },
                    onDragConnection = { delta ->
                        val screenDragAmount = gemCanvasScaledDragDeltaToScreenSpace(
                            dragDelta = delta,
                            viewport = viewport
                        )
                        connectionDrag = connectionDrag?.copy(
                            screenDelta = connectionDrag!!.screenDelta + screenDragAmount
                        )
                    },
                    onEndConnection = {
                        completeConnectionDrag()
                    },
                    onCancelConnection = {
                        Unit
                    },
                    onNodeStateChange = { key, value ->
                        session.updateNode(nodeId = node.id, graphId = graph.id) { instance ->
                            instance.copy(
                                serializedState = instance.serializedState +
                                    (key to GemJsonPersistence.json.encodeToJsonElement(GemValue.serializer(), value))
                            )
                        }
                    }
                )
            }

            previewStatusText?.let { status ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .widthIn(max = 420.dp)
                ) {
                    CardContent(
                        modifier = Modifier.fillMaxWidth(),
                        content = {
                            TypographySmall(status)
                        }
                    )
                }
            }
        }

        if (nodePaletteVisible) {
            GemNodeCreationDialog(
                registry = registry,
                onDismiss = { onNodePaletteVisibilityChange(false) },
                onCreateNode = { descriptor ->
                    val prototype = descriptor.instantiate(
                        nodeId = buildGemCanvasNodeId(descriptor = descriptor)
                    )
                    val metrics = measureGemCanvasNode(prototype, density)
                    val position = resolveGemCanvasNodePlacement(
                        metrics = metrics,
                        viewport = viewport,
                        canvasSize = canvasSize,
                        anchorWorld = nodeCreationAnchorWorld
                    )
                    val node = prototype.moveTo(position)
                    session.putNode(
                        node = node,
                        graphId = graph.id
                    )
                    session.setSelection(
                        GemEditorSelection(
                            graphId = graph.id,
                            nodeIds = linkedSetOf(node.id)
                        )
                    )
                    onNodePaletteVisibilityChange(false)
                }
            )
        }
    }
}

@Composable
private fun GemCanvasNodeCard(
    node: GemNodeInstance,
    descriptor: GemNodeDescriptor?,
    position: Offset,
    viewport: GemCanvasViewportState,
    density: Float,
    selected: Boolean,
    connectedInputPinIds: Set<String>,
    sourceConnectionPin: GemPinRef?,
    previewTarget: GemCanvasPreviewTarget?,
    onClick: () -> Unit,
    onStartDrag: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEndDrag: () -> Unit,
    onCancelDrag: () -> Unit,
    onStartConnection: (GemPinRef, Offset) -> Unit,
    onDragConnection: (Offset) -> Unit,
    onEndConnection: () -> Unit,
    onCancelConnection: () -> Unit,
    onNodeStateChange: (key: String, value: GemValue) -> Unit,
) {
    val metrics = remember(node, density) { measureGemCanvasNode(node, density) }
    val isDetailView = remember(viewport.zoom >= 0.55f) { viewport.zoom >= 0.55f }
    val screenPosition = viewport.worldToScreen(position)
    val widthDp = remember(metrics.width, density) { (metrics.width / density).dp }
    val heightDp = remember(metrics.height, density) { (metrics.height / density).dp }
    val headerDp = remember(density) { (GEM_CANVAS_NODE_HEADER_HEIGHT / density).dp }
    val pinRowHeightDp = remember(density) { (GEM_CANVAS_NODE_PIN_ROW_HEIGHT / density).dp }
    val pinDiameterDp = remember(density) { ((GEM_CANVAS_NODE_PIN_RADIUS * 2f) / density).dp }
    val pinLabelOffsetDp = remember(density) { (GEM_CANVAS_NODE_PIN_LABEL_OFFSET / density).dp }
    val bodyPaddingDp = remember(density) { (GEM_CANVAS_NODE_BODY_PADDING / density).dp }
    val category = descriptor?.metadata?.category?.label ?: node.type.typeId
    val latestOnStartDrag by rememberUpdatedState(onStartDrag)
    val latestOnDrag by rememberUpdatedState(onDrag)
    val latestOnEndDrag by rememberUpdatedState(onEndDrag)
    val latestOnCancelDrag by rememberUpdatedState(onCancelDrag)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = screenPosition.x.roundToInt(),
                    y = screenPosition.y.roundToInt()
                )
            }
            .graphicsLayer {
                scaleX = viewport.zoom
                scaleY = viewport.zoom
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .size(width = widthDp, height = heightDp)
            .clip(DefaultShape)
            .background(Theme[colors][card], DefaultShape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Theme[colors][accent] else Theme[colors][border],
                shape = DefaultShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerDp)
                    .background(
                        if (selected) Theme[colors][accent].copy(alpha = 0.14f)
                        else Theme[colors][secondary].copy(alpha = 0.32f)
                    )
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDragStart = {
                                latestOnStartDrag()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                latestOnDrag(dragAmount)
                            },
                            onDragEnd = { latestOnEndDrag() },
                            onDragCancel = { latestOnCancelDrag() }
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TypographySmall(node.label.ifBlank { node.id })
                }
                Text(
                    text = category,
                    color = Theme[colors][mutedForeground]
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = bodyPaddingDp, vertical = bodyPaddingDp)
            ) {
                if (isDetailView) {
                    val inputPins = node.pins.filter { it.direction == GemPinDirection.INPUT }
                    val outputPins = node.pins.filter { it.direction == GemPinDirection.OUTPUT }
                    val rows = maxOf(inputPins.size, outputPins.size).coerceAtLeast(1)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        repeat(rows) { index ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(pinRowHeightDp)
                            ) {
                                inputPins.getOrNull(index)?.let { pin ->
                                    GemCanvasPinChip(
                                        pin = pin,
                                        pinRef = GemPinRef(nodeId = node.id, pinId = pin.id),
                                        worldPosition = gemCanvasPinPosition(node, pin.id, position, density) ?: position,
                                        viewport = viewport,
                                        density = density,
                                        pinDiameterDp = pinDiameterDp,
                                        pinLabelOffsetDp = pinLabelOffsetDp,
                                        labelAlignment = Alignment.CenterStart,
                                        displayLabel = gemCanvasPinLabel(
                                            pin = pin,
                                            hasConnection = pin.id in connectedInputPinIds
                                        ),
                                        fillColor = pinFillColor(
                                            pin = pin,
                                            pinRef = GemPinRef(node.id, pin.id),
                                            sourceConnectionPin = sourceConnectionPin,
                                            previewTarget = previewTarget
                                        ),
                                        emphasized = sourceConnectionPin == GemPinRef(node.id, pin.id) ||
                                            previewTarget?.pinRef == GemPinRef(node.id, pin.id),
                                        onStartConnection = onStartConnection,
                                        onDragConnection = onDragConnection,
                                        onEndConnection = onEndConnection,
                                        onCancelConnection = onCancelConnection
                                    )
                                }

                                outputPins.getOrNull(index)?.let { pin ->
                                    GemCanvasPinChip(
                                        pin = pin,
                                        pinRef = GemPinRef(nodeId = node.id, pinId = pin.id),
                                        worldPosition = gemCanvasPinPosition(node, pin.id, position, density) ?: position,
                                        viewport = viewport,
                                        density = density,
                                        pinDiameterDp = pinDiameterDp,
                                        pinLabelOffsetDp = pinLabelOffsetDp,
                                        labelAlignment = Alignment.CenterEnd,
                                        displayLabel = gemCanvasPinLabel(
                                            pin = pin,
                                            hasConnection = false
                                        ),
                                        fillColor = pinFillColor(
                                            pin = pin,
                                            pinRef = GemPinRef(node.id, pin.id),
                                            sourceConnectionPin = sourceConnectionPin,
                                            previewTarget = previewTarget
                                        ),
                                        emphasized = sourceConnectionPin == GemPinRef(node.id, pin.id) ||
                                            previewTarget?.pinRef == GemPinRef(node.id, pin.id),
                                        onStartConnection = onStartConnection,
                                        onDragConnection = onDragConnection,
                                        onEndConnection = onEndConnection,
                                        onCancelConnection = onCancelConnection
                                    )
                                }
                            }
                        }

                        when (node.type.typeId) {
                            GemBuiltInNodes.TypeIds.CONSTANT_NUMBER -> {
                                Spacer(Modifier.height(8.dp))
                                NumberConstantInlineEditor(
                                    node = node,
                                    density = density,
                                    onNodeStateChange = onNodeStateChange
                                )
                            }
                            GemBuiltInNodes.TypeIds.CONSTANT_BOOLEAN -> {
                                Spacer(Modifier.height(8.dp))
                                BooleanConstantInlineEditor(
                                    node = node,
                                    onNodeStateChange = onNodeStateChange
                                )
                            }
                            GemBuiltInNodes.TypeIds.CONSTANT_COLOR -> {
                                Spacer(Modifier.height(8.dp))
                                ColorConstantInlineEditor(
                                    node = node,
                                    onNodeStateChange = onNodeStateChange
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberConstantInlineEditor(
    node: GemNodeInstance,
    density: Float,
    onNodeStateChange: (key: String, value: GemValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentNumber = remember(node.serializedState["value"]) {
        val json = node.serializedState["value"]
        if (json != null) {
            try {
                (GemJsonPersistence.json.decodeFromJsonElement<GemValue>(json) as? GemValue.Number)?.value ?: 0.0
            } catch (_: Exception) { 0.0 }
        } else 0.0
    }
    var textValue by remember(node.id) { mutableStateOf(currentNumber.toCanvasNumberLabel()) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(currentNumber) {
        if (!isFocused) {
            textValue = currentNumber.toCanvasNumberLabel()
        }
    }

    val fieldShape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((32f / density).dp)
            .background(Color(0xFF1C1E26), fieldShape)
            .border(1.dp, Color(0xFF2D3043), fieldShape)
            .padding(horizontal = 8.dp)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = textValue,
            onValueChange = { newText ->
                textValue = newText
                val parsed = newText.replace(',', '.').toDoubleOrNull()
                if (parsed != null) {
                    onNodeStateChange("value", GemValue.Number(parsed))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = Theme[typography][small].copy(color = Color.White),
            cursorBrush = SolidColor(Color.White),
        )
    }
}

@Composable
private fun BooleanConstantInlineEditor(
    node: GemNodeInstance,
    onNodeStateChange: (key: String, value: GemValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentValue = remember(node.serializedState["value"]) {
        val json = node.serializedState["value"]
        if (json != null) {
            try {
                (GemJsonPersistence.json.decodeFromJsonElement<GemValue>(json) as? GemValue.Boolean)?.value ?: false
            } catch (_: Exception) { false }
        } else false
    }

    val fieldShape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(
                if (currentValue) Color(0xFF1A2E1A) else Color(0xFF1C1E26),
                fieldShape
            )
            .border(
                1.dp,
                if (currentValue) Color(0xFF3A6040) else Color(0xFF2D3043),
                fieldShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onNodeStateChange("value", GemValue.Boolean(!currentValue)) }
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (currentValue) Color(0xFF4CAF50) else Color(0xFF555570),
                    CircleShape
                )
        )
        Text(
            text = if (currentValue) "true" else "false",
            style = Theme[typography][small].copy(color = Color.White),
        )
    }
}

@Composable
private fun ColorConstantInlineEditor(
    node: GemNodeInstance,
    onNodeStateChange: (key: String, value: GemValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentGemColor = remember(node.serializedState["value"]) {
        val json = node.serializedState["value"]
        if (json != null) {
            try {
                (GemJsonPersistence.json.decodeFromJsonElement<GemValue>(json) as? GemValue.Color)?.value
            } catch (_: Exception) { null }
        } else null
    }
    val initialColor = if (currentGemColor != null) {
        Color(currentGemColor.red, currentGemColor.green, currentGemColor.blue)
    } else {
        Color.Red
    }

    val pickerState = remember(node.id) { dev.anthonyhfm.amethyst.ui.components.ColorPickerState(initialColor) }

    fun onColorPicked(color: Color) {
        onNodeStateChange(
            "value",
            GemValue.Color(dev.anthonyhfm.amethyst.gem.GemColor(red = color.red, green = color.green, blue = color.blue))
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dev.anthonyhfm.amethyst.ui.components.ColorPicker(
            modifier = Modifier.fillMaxWidth(),
            state = pickerState,
            onSelectionFinish = { onColorPicked(it) }
        )
        dev.anthonyhfm.amethyst.ui.components.HuePickerBar(
            modifier = Modifier.fillMaxWidth().height(24.dp),
            state = pickerState,
            onSelectionFinish = { onColorPicked(it) }
        )
    }
}

@Composable
private fun GemCanvasPinChip(
    pin: GemPin,
    pinRef: GemPinRef,
    worldPosition: Offset,
    viewport: GemCanvasViewportState,
    density: Float,
    pinDiameterDp: androidx.compose.ui.unit.Dp,
    pinLabelOffsetDp: androidx.compose.ui.unit.Dp,
    labelAlignment: Alignment,
    displayLabel: String,
    fillColor: Color,
    emphasized: Boolean,
    onStartConnection: (GemPinRef, Offset) -> Unit,
    onDragConnection: (Offset) -> Unit,
    onEndConnection: () -> Unit,
    onCancelConnection: () -> Unit
) {
    val latestOnStartConnection by rememberUpdatedState(onStartConnection)
    val latestOnDragConnection by rememberUpdatedState(onDragConnection)
    val latestOnEndConnection by rememberUpdatedState(onEndConnection)
    val latestOnCancelConnection by rememberUpdatedState(onCancelConnection)
    val latestWorldPosition by rememberUpdatedState(worldPosition)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pinRef) {
                detectDragGestures(
                    onDragStart = {
                        latestOnStartConnection(pinRef, latestWorldPosition)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        latestOnDragConnection(dragAmount)
                    },
                    onDragEnd = { latestOnEndConnection() },
                    onDragCancel = { latestOnCancelConnection() }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .align(labelAlignment)
                .then(
                    if (pin.direction == GemPinDirection.INPUT) {
                        Modifier.padding(start = pinLabelOffsetDp)
                    } else {
                        Modifier.padding(end = pinLabelOffsetDp)
                    }
                ),
            horizontalArrangement = if (pin.direction == GemPinDirection.INPUT) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pin.direction == GemPinDirection.OUTPUT) {
                Text(
                    text = displayLabel,
                    color = Theme[colors][mutedForeground]
                )
                Spacer(Modifier.width(8.dp))
            }

            Box(
                modifier = Modifier
                    .size(pinDiameterDp)
                    .clip(CircleShape)
                    .background(fillColor, CircleShape)
                    .border(
                        width = if (emphasized) 2.dp else 1.dp,
                        color = if (emphasized) fillColor.copy(alpha = 0.92f) else Theme[colors][border],
                        shape = CircleShape
                    )
            )

            if (pin.direction == GemPinDirection.INPUT) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = displayLabel,
                    color = Theme[colors][mutedForeground]
                )
            }
        }
    }
}

@Composable
private fun GemNodeCreationDialog(
    registry: GemNodeRegistry,
    onDismiss: () -> Unit,
    onCreateNode: (GemNodeDescriptor) -> Unit
) {
    val dialogState = rememberDialogState(initiallyVisible = true)
    var query by remember { mutableStateOf("") }
    val filteredDescriptors = remember(query, registry) {
        registry.all()
            .distinctBy { it.type }
            .filter { descriptor ->
                val haystack = listOf(
                    descriptor.metadata.label,
                    descriptor.metadata.category.label,
                    descriptor.type.typeId,
                    descriptor.metadata.description
                ).joinToString(" ").lowercase()
                query.isBlank() || query.lowercase() in haystack
            }
            .sortedWith(
                compareBy<GemNodeDescriptor>(
                    { it.metadata.category.label.lowercase() },
                    { it.metadata.label.lowercase() }
                )
            )
    }

    Dialog(
        state = dialogState,
        onDismiss = onDismiss
    ) {
        DialogContent(
            modifier = Modifier.widthIn(max = 680.dp),
            showCloseButton = false
        ) {
            DialogHeader {
                DialogTitle("Add Gem node")
                DialogDescription("Search the registered node catalog and place a new node onto the current canvas.")
            }

            Command {
                CommandInput(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search nodes by label, category, or type…"
                )
                CommandList(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (filteredDescriptors.isEmpty()) {
                        CommandEmpty {
                            Text("No node definitions match \"$query\".")
                        }
                    } else {
                        filteredDescriptors
                            .groupBy { it.metadata.category.label }
                            .forEach { (category, descriptors) ->
                                CommandGroup(heading = category) {
                                    descriptors.forEach { descriptor ->
                                        CommandItem(
                                            onClick = { onCreateNode(descriptor) }
                                        ) {
                                            Text(descriptor.metadata.label)
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}

private data class GemCanvasBoxSelection(
    val start: Offset,
    val end: Offset,
    val additive: Boolean
) {
    val rect: Rect
        get() = Rect(
            left = minOf(start.x, end.x),
            top = minOf(start.y, end.y),
            right = maxOf(start.x, end.x),
            bottom = maxOf(start.y, end.y)
        )
}

private data class GemCanvasNodeDrag(
    val graphId: String,
    val nodeIds: Set<String>,
    val startScreenPositions: Map<String, Offset>,
    val screenDelta: Offset
)

private data class GemCanvasConnectionDrag(
    val source: GemPinRef,
    val startScreen: Offset,
    val screenDelta: Offset
) {
    val currentScreen: Offset
        get() = startScreen + screenDelta
}

private data class GemCanvasPreviewTarget(
    val pinRef: GemPinRef,
    val evaluation: GemCanvasConnectionEvaluation
)

private fun findPreviewTarget(
    asset: dev.anthonyhfm.amethyst.gem.GemAsset,
    graph: dev.anthonyhfm.amethyst.gem.GemGraph,
    registry: GemNodeRegistry,
    source: GemPinRef,
    currentWorld: Offset,
    previewTolerance: Float,
    renderedNodePositions: Map<String, Offset>
): GemCanvasPreviewTarget? {
    val sourceNode = graph.node(source.nodeId) ?: return null
    val sourcePin = sourceNode.pins.firstOrNull { it.id == source.pinId } ?: return null
    val nearestPin = graph.nodes
        .flatMap { node ->
            node.pins.mapNotNull { pin ->
                if (pin.direction == sourcePin.direction) {
                    null
                } else {
                    val ref = GemPinRef(nodeId = node.id, pinId = pin.id)
                    val position = gemCanvasPinPosition(
                        node = node,
                        pinId = pin.id,
                        position = renderedNodePositions.getValue(node.id)
                    ) ?: return@mapNotNull null
                    GemCanvasPinDistance(ref = ref, distance = position.distanceTo(currentWorld))
                }
            }
        }
        .filter { it.distance <= previewTolerance }
        .minByOrNull { it.distance }
        ?: return null
    return GemCanvasPreviewTarget(
        pinRef = nearestPin.ref,
        evaluation = evaluateGemCanvasConnection(
            asset = asset,
            graphId = graph.id,
            first = source,
            second = nearestPin.ref,
            registry = registry
        )
    )
}

private data class GemCanvasPinDistance(
    val ref: GemPinRef,
    val distance: Float
)

private fun pinFillColor(
    pin: GemPin,
    pinRef: GemPinRef,
    sourceConnectionPin: GemPinRef?,
    previewTarget: GemCanvasPreviewTarget?
): Color {
    return when {
        pinRef == sourceConnectionPin -> Color(0xFFA5B4FC)
        previewTarget?.pinRef == pinRef && previewTarget.evaluation.isValid -> Color(0xFF34D399)
        previewTarget?.pinRef == pinRef -> Color(0xFFF87171)
        else -> pinBaseColor(pin)
    }
}

private fun pinBaseColor(pin: GemPin): Color = when (val type = pin.type) {
    is GemPinType.Signal -> when (type.domain) {
        GemSignalDomain.LED -> Color(0xFFA855F7)
        GemSignalDomain.MIDI -> Color(0xFFF59E0B)
    }

    is GemPinType.Value -> when (type.valueType) {
        GemValueType.Number -> Color(0xFF60A5FA)
        GemValueType.Boolean -> Color(0xFF34D399)
        is GemValueType.Enum -> Color(0xFFF472B6)
        GemValueType.Color -> Color(0xFFFB7185)
        GemValueType.Timing -> Color(0xFFFBBF24)
    }

    GemPinType.AnySignal -> Color(0xFFD1D5DB)
}

private fun GemValidationError.referencesNode(
    graphId: String,
    nodeId: String
): Boolean {
    if (this.graphId != null && this.graphId != graphId) {
        return false
    }
    return this.nodeId == nodeId ||
        relatedNodeId == nodeId ||
        nodeId in relatedNodeIds
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConnection(
    start: Offset,
    end: Offset,
    color: Color,
    width: Float
) {
    val curve = gemCanvasConnectionCurve(start, end)
    val path = Path().apply {
        moveTo(curve.start.x, curve.start.y)
        cubicTo(
            x1 = curve.control1.x,
            y1 = curve.control1.y,
            x2 = curve.control2.x,
            y2 = curve.control2.y,
            x3 = curve.end.x,
            y3 = curve.end.y
        )
    }
    // Soft ambient glow — adds depth without being distracting
    drawPath(
        path = path,
        color = color.copy(alpha = 0.18f),
        style = Stroke(width = width * 5f, cap = StrokeCap.Round)
    )
    // Main line
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    size: Size,
    viewport: GemCanvasViewportState
) {
    val gridStep = 40f * viewport.zoom
    if (gridStep <= 0f) {
        return
    }

    val pointColor = Color(0xFF3D4150)
    var x = (viewport.offset.x % gridStep) - gridStep
    while (x < size.width + gridStep) {
        var y = (viewport.offset.y % gridStep) - gridStep
        while (y < size.height + gridStep) {
            drawCircle(
                color = pointColor,
                radius = 1.8f,
                center = Offset(x, y)
            )
            y += gridStep
        }
        x += gridStep
    }
}

private fun buildGemCanvasNodeId(
    descriptor: GemNodeDescriptor
): String {
    val prefix = descriptor.type.typeId
        .substringAfterLast('.')
        .replace(Regex("[^a-zA-Z0-9]+"), "-")
        .trim('-')
        .ifBlank { "node" }
    return "$prefix-${UUID.randomUUID().take(8)}"
}

private fun nodeCreationAnchor(
    node: GemNodeInstance,
    position: Offset
): Offset {
    val metrics = measureGemCanvasNode(node)
    return position + Offset(
        x = metrics.width / 2f,
        y = metrics.height / 2f
    )
}

private fun Set<String>.toggle(item: String): Set<String> = if (item in this) {
    filterNot { it == item }.toLinkedSet()
} else {
    (this + item).toLinkedSet()
}

private fun Iterable<String>.toLinkedSet(): Set<String> = linkedSetOf<String>().also { set ->
    forEach(set::add)
}

private fun Offset.distanceTo(other: Offset): Float = (this - other).getDistance()

private operator fun Offset.plus(other: Offset): Offset = Offset(
    x = x + other.x,
    y = y + other.y
)

private operator fun Offset.minus(other: Offset): Offset = Offset(
    x = x - other.x,
    y = y - other.y
)

private operator fun Offset.div(value: Float): Offset = Offset(
    x = x / value,
    y = y / value
)

private data class GemCanvasTapState(
    val worldPosition: Offset,
    val timeMillis: Long
) {
    fun matches(
        worldPosition: Offset,
        timeMillis: Long
    ): Boolean {
        return timeMillis - this.timeMillis <= GEM_CANVAS_DOUBLE_TAP_TIMEOUT_MS &&
            this.worldPosition.distanceTo(worldPosition) <= GEM_CANVAS_DOUBLE_TAP_SLOP
    }
}

private const val GEM_CANVAS_DOUBLE_TAP_TIMEOUT_MS = 320L
private const val GEM_CANVAS_DOUBLE_TAP_SLOP = 24f
