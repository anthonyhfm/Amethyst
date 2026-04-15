package dev.anthonyhfm.amethyst.gem.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemConnection
import dev.anthonyhfm.amethyst.gem.GemGraph
import dev.anthonyhfm.amethyst.gem.GemNodeInstance
import dev.anthonyhfm.amethyst.gem.GemNodePosition
import dev.anthonyhfm.amethyst.gem.GemNodeRegistry
import dev.anthonyhfm.amethyst.gem.GemPin
import dev.anthonyhfm.amethyst.gem.GemPinDirection
import dev.anthonyhfm.amethyst.gem.GemPinRef
import dev.anthonyhfm.amethyst.gem.GemPinType
import dev.anthonyhfm.amethyst.gem.GemValidationErrorCode
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.GemValidator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val GEM_CANVAS_NODE_WIDTH = 240f
internal const val GEM_CANVAS_NODE_HEADER_HEIGHT = 72f
internal const val GEM_CANVAS_NODE_PIN_ROW_HEIGHT = 56f
internal const val GEM_CANVAS_NODE_BODY_PADDING = 12f
internal const val GEM_CANVAS_NODE_PIN_LABEL_OFFSET = 16f
internal const val GEM_CANVAS_NODE_FOOTER_HEIGHT = 10f
internal const val GEM_CANVAS_NODE_PIN_RADIUS = 10f
internal const val GEM_CANVAS_CONNECTION_HIT_TOLERANCE = 18f
internal const val GEM_CANVAS_CONNECTION_PREVIEW_TOLERANCE = 36f
internal const val GEM_CANVAS_DUPLICATE_OFFSET = 48f
// Extra body height reserved for inline value editors in constant nodes.
internal const val GEM_CANVAS_NODE_INLINE_NUMBER_EDITOR_HEIGHT = 48f
internal const val GEM_CANVAS_NODE_INLINE_BOOLEAN_EDITOR_HEIGHT = 48f
// ColorPicker (square, ~124dp) + HuePicker (24dp) + spacers (~20dp) = ~168dp = 336f at density 2.
internal const val GEM_CANVAS_NODE_INLINE_COLOR_EDITOR_HEIGHT = 336f

internal data class GemCanvasViewportState(
    val offset: Offset = Offset(120f, 120f),
    val zoom: Float = 1f
) {
    fun worldToScreen(point: Offset): Offset = Offset(
        x = point.x * zoom + offset.x,
        y = point.y * zoom + offset.y
    )

    fun screenToWorld(point: Offset): Offset = Offset(
        x = (point.x - offset.x) / zoom,
        y = (point.y - offset.y) / zoom
    )

    fun pan(delta: Offset): GemCanvasViewportState = copy(offset = offset + delta)

    fun zoomBy(delta: Float, focus: Offset): GemCanvasViewportState {
        val nextZoom = (zoom * (1f + delta)).coerceIn(0.35f, 2.5f)
        if (nextZoom == zoom) {
            return this
        }

        val worldFocus = screenToWorld(focus)
        return copy(
            zoom = nextZoom,
            offset = Offset(
                x = focus.x - worldFocus.x * nextZoom,
                y = focus.y - worldFocus.y * nextZoom
            )
        )
    }
}

internal fun gemCanvasScaledDragDeltaToScreenSpace(
    dragDelta: Offset,
    viewport: GemCanvasViewportState
): Offset = dragDelta * viewport.zoom

internal data class GemCanvasNodeMetrics(
    val width: Float = GEM_CANVAS_NODE_WIDTH,
    val height: Float
)

internal data class GemCanvasNormalizedConnection(
    val from: GemPinRef,
    val to: GemPinRef
)

internal data class GemCanvasConnectionEvaluation(
    val normalizedConnection: GemCanvasNormalizedConnection? = null,
    val errorCode: GemValidationErrorCode? = null,
    val message: String? = null
) {
    val isValid: Boolean
        get() = normalizedConnection != null && errorCode == null
}

internal data class GemCanvasDuplicationResult(
    val asset: GemAsset,
    val duplicatedNodeIds: Set<String>,
    val duplicatedConnectionIds: Set<String>
)

internal fun fitToContent(
    nodes: List<GemNodeInstance>,
    canvasSize: IntSize,
    padding: Float = 60f
): GemCanvasViewportState? {
    if (nodes.isEmpty()) return null
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return null

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE

    for (node in nodes) {
        val rect = gemCanvasNodeRect(node)
        if (rect.left < minX) minX = rect.left
        if (rect.top < minY) minY = rect.top
        if (rect.right > maxX) maxX = rect.right
        if (rect.bottom > maxY) maxY = rect.bottom
    }

    val contentWidth = maxX - minX
    val contentHeight = maxY - minY
    val availableWidth = canvasSize.width - padding * 2f
    val availableHeight = canvasSize.height - padding * 2f

    val zoom = if (contentWidth > 0f && contentHeight > 0f) {
        minOf(availableWidth / contentWidth, availableHeight / contentHeight)
    } else {
        1f
    }.coerceIn(0.15f, 1.2f)

    val offsetX = (canvasSize.width - contentWidth * zoom) / 2f - minX * zoom
    val offsetY = (canvasSize.height - contentHeight * zoom) / 2f - minY * zoom

    return GemCanvasViewportState(offset = Offset(offsetX, offsetY), zoom = zoom)
}

internal fun resolveGemCanvasNodePlacement(
    metrics: GemCanvasNodeMetrics,
    viewport: GemCanvasViewportState,
    canvasSize: IntSize,
    anchorWorld: Offset? = null
): GemNodePosition {
    val centerWorld = anchorWorld ?: viewport.screenToWorld(
        point = Offset(
            x = canvasSize.width / 2f,
            y = canvasSize.height / 2f
        )
    )
    return GemNodePosition(
        x = centerWorld.x - metrics.width / 2f,
        y = centerWorld.y - metrics.height / 2f
    )
}

internal fun gemCanvasPinLabel(
    pin: GemPin,
    hasConnection: Boolean
): String {
    if (pin.direction != GemPinDirection.INPUT || hasConnection || pin.defaultValue == null) {
        return pin.label
    }
    return "${pin.label} · ${gemCanvasValueLabel(pin.defaultValue)}"
}

internal fun measureGemCanvasNode(node: GemNodeInstance, density: Float = 2f): GemCanvasNodeMetrics {
    val inputPins = node.pins.filter { it.direction == GemPinDirection.INPUT }
    val outputPins = node.pins.filter { it.direction == GemPinDirection.OUTPUT }
    val rows = max(inputPins.size, outputPins.size).coerceAtLeast(1)
    val inlineEditorHeight = when (node.type.typeId) {
        GemBuiltInNodes.TypeIds.CONSTANT_NUMBER -> GEM_CANVAS_NODE_INLINE_NUMBER_EDITOR_HEIGHT
        GemBuiltInNodes.TypeIds.CONSTANT_BOOLEAN -> GEM_CANVAS_NODE_INLINE_BOOLEAN_EDITOR_HEIGHT
        GemBuiltInNodes.TypeIds.CONSTANT_COLOR -> GEM_CANVAS_NODE_INLINE_COLOR_EDITOR_HEIGHT
        else -> 0f
    }

    // Minimum width required to fit the node title without wrapping.
    // 14sp Medium font: ~8dp/char average. Header padding: 12dp on each side = 24dp total.
    val titleText = node.label.ifBlank { node.id }
    val titleRequiredWidth = titleText.length * 8f * density + 24f * density

    // Compute minimum node width so simultaneous input+output labels never overlap.
    // For rows with only one side, the default width is always sufficient.
    val requiredWidth = (0 until rows).maxOfOrNull { i ->
        val inputPin = inputPins.getOrNull(i)
        val outputPin = outputPins.getOrNull(i)
        if (inputPin == null || outputPin == null) {
            GEM_CANVAS_NODE_WIDTH
        } else {
            val inputLabel = gemCanvasPinLabel(inputPin, hasConnection = false)
            val outputLabel = gemCanvasPinLabel(outputPin, hasConnection = false)
            // ~7.5dp per character for the default sans-serif font; convert to world units.
            val labelWidth = (inputLabel.length + outputLabel.length) * 7.5f * density
            // Fixed structural overhead in world units (body padding × 2, label offset × 2, dot diameter × 2).
            val structuralOverhead = 2 * GEM_CANVAS_NODE_BODY_PADDING +
                2 * GEM_CANVAS_NODE_PIN_LABEL_OFFSET +
                4 * GEM_CANVAS_NODE_PIN_RADIUS
            // dp-based spacing overhead (2 × 8dp dot-label spacers + 8dp min gap) → world units.
            val spacingOverhead = 24f * density
            labelWidth + structuralOverhead + spacingOverhead
        }
    } ?: GEM_CANVAS_NODE_WIDTH

    return GemCanvasNodeMetrics(
        width = maxOf(GEM_CANVAS_NODE_WIDTH, requiredWidth, titleRequiredWidth),
        height = GEM_CANVAS_NODE_HEADER_HEIGHT +
            GEM_CANVAS_NODE_BODY_PADDING * 2f +
            rows * GEM_CANVAS_NODE_PIN_ROW_HEIGHT +
            inlineEditorHeight +
            GEM_CANVAS_NODE_FOOTER_HEIGHT
    )
}

internal fun gemCanvasValueLabel(value: GemValue): String = when (value) {
    is GemValue.Number -> value.value.toCanvasNumberLabel()
    is GemValue.Boolean -> if (value.value) "true" else "false"
    is GemValue.Enum -> value.optionId
    is GemValue.Color -> "rgba(${(value.value.red * 255).roundToInt()}, ${(value.value.green * 255).roundToInt()}, ${(value.value.blue * 255).roundToInt()}, ${value.value.alpha.toDouble().toCanvasNumberLabel()})"
    is GemValue.TimingValue -> value.value.toString()
}

internal fun gemCanvasNodeRect(
    node: GemNodeInstance,
    position: Offset = node.layout.position.toOffset(),
    density: Float = 2f
): Rect {
    val size = measureGemCanvasNode(node, density)
    return Rect(
        left = position.x,
        top = position.y,
        right = position.x + size.width,
        bottom = position.y + size.height
    )
}

internal fun selectGemCanvasNodes(
    graph: GemGraph,
    selectionRect: Rect
): Set<String> = graph.nodes
    .filter { node -> gemCanvasNodeRect(node).overlaps(selectionRect.normalized()) }
    .mapTo(linkedSetOf()) { it.id }

internal fun evaluateGemCanvasConnection(
    asset: GemAsset,
    graphId: String,
    first: GemPinRef,
    second: GemPinRef,
    registry: GemNodeRegistry = GemNodeRegistry.builtIns
): GemCanvasConnectionEvaluation {
    val graph = asset.graph(graphId)
        ?: return GemCanvasConnectionEvaluation(
            errorCode = GemValidationErrorCode.MISSING_CONNECTION_NODE,
            message = "Graph '$graphId' is missing."
        )
    val firstResolved = graph.resolvePin(first)
        ?: return missingPinEvaluation(graph, first)
    val secondResolved = graph.resolvePin(second)
        ?: return missingPinEvaluation(graph, second)

    val normalized = when {
        firstResolved.pin.direction == GemPinDirection.OUTPUT &&
            secondResolved.pin.direction == GemPinDirection.INPUT -> {
            GemCanvasNormalizedConnection(from = first, to = second)
        }

        firstResolved.pin.direction == GemPinDirection.INPUT &&
            secondResolved.pin.direction == GemPinDirection.OUTPUT -> {
            GemCanvasNormalizedConnection(from = second, to = first)
        }

        else -> {
            return GemCanvasConnectionEvaluation(
                errorCode = GemValidationErrorCode.INVALID_CONNECTION_DIRECTION,
                message = "Connections must start at an output pin and end at an input pin."
            )
        }
    }

    val sourcePin = graph.resolvePin(normalized.from)?.pin ?: return missingPinEvaluation(graph, normalized.from)
    val targetPin = graph.resolvePin(normalized.to)?.pin ?: return missingPinEvaluation(graph, normalized.to)
    fun pinsCompatible(a: GemPinType, b: GemPinType): Boolean {
        if (a == b) return true
        if (a is GemPinType.AnySignal && b is GemPinType.Signal) return true
        if (a is GemPinType.Signal && b is GemPinType.AnySignal) return true
        if (a is GemPinType.AnySignal && b is GemPinType.AnySignal) return true
        return false
    }
    if (!pinsCompatible(sourcePin.type, targetPin.type)) {
        return GemCanvasConnectionEvaluation(
            errorCode = GemValidationErrorCode.INCOMPATIBLE_PIN_TYPES,
            message = "Pins '${normalized.from.pinId}' and '${normalized.to.pinId}' use incompatible types."
        )
    }

    val baselineErrors = GemValidator.validate(asset, registry).errors.toSet()
    val previewConnection = GemConnection(
        id = "__gem_canvas_candidate__",
        from = normalized.from,
        to = normalized.to
    )
    val previewErrors = GemValidator.validate(
        asset = asset.connect(connection = previewConnection, graphId = graphId),
        registry = registry
    ).errors.filterNot { it in baselineErrors }
    val blockingError = previewErrors.firstOrNull { error ->
        error.connectionId == previewConnection.id ||
            (error.graphId == graphId && error.code == GemValidationErrorCode.GRAPH_CYCLE)
    }
    if (blockingError != null) {
        return GemCanvasConnectionEvaluation(
            errorCode = blockingError.code,
            message = blockingError.message
        )
    }

    return GemCanvasConnectionEvaluation(normalizedConnection = normalized)
}

internal fun duplicateGemSelection(
    asset: GemAsset,
    selection: GemEditorSelection,
    nodeIdFactory: () -> String = { UUID.randomUUID() },
    connectionIdFactory: () -> String = { UUID.randomUUID() },
    positionOffset: GemNodePosition = GemNodePosition(
        x = GEM_CANVAS_DUPLICATE_OFFSET,
        y = GEM_CANVAS_DUPLICATE_OFFSET
    )
): GemCanvasDuplicationResult {
    val graph = asset.graph(selection.graphId)
        ?: return GemCanvasDuplicationResult(
            asset = asset,
            duplicatedNodeIds = emptySet(),
            duplicatedConnectionIds = emptySet()
        )
    val selectedNodes = graph.nodes.filter { it.id in selection.nodeIds }
    if (selectedNodes.isEmpty()) {
        return GemCanvasDuplicationResult(
            asset = asset,
            duplicatedNodeIds = emptySet(),
            duplicatedConnectionIds = emptySet()
        )
    }

    val nodeIdMapping = selectedNodes.associate { node -> node.id to nodeIdFactory() }
    val duplicatedNodes = selectedNodes.map { node ->
        node.copy(
            id = nodeIdMapping.getValue(node.id),
            layout = node.layout.copy(
                position = GemNodePosition(
                    x = node.layout.position.x + positionOffset.x,
                    y = node.layout.position.y + positionOffset.y
                )
            )
        )
    }
    val duplicatedConnections = graph.connections
        .filter { connection ->
            connection.from.nodeId in nodeIdMapping &&
                connection.to.nodeId in nodeIdMapping
        }
        .map { connection ->
            GemConnection(
                id = connectionIdFactory(),
                from = connection.from.copy(nodeId = nodeIdMapping.getValue(connection.from.nodeId)),
                to = connection.to.copy(nodeId = nodeIdMapping.getValue(connection.to.nodeId))
            )
        }

    val duplicatedNodeIds = duplicatedNodes.mapTo(linkedSetOf()) { it.id }
    val duplicatedConnectionIds = duplicatedConnections.mapTo(linkedSetOf()) { it.id }
    val duplicatedAsset = asset.updateGraph(selection.graphId) { graphState ->
        duplicatedNodes.fold(graphState) { current, node -> current.putNode(node) }
            .let { current ->
                duplicatedConnections.fold(current) { graphWithConnections, connection ->
                    graphWithConnections.connect(connection)
                }
            }
    }
    return GemCanvasDuplicationResult(
        asset = duplicatedAsset,
        duplicatedNodeIds = duplicatedNodeIds,
        duplicatedConnectionIds = duplicatedConnectionIds
    )
}

internal fun deleteGemSelection(
    asset: GemAsset,
    selection: GemEditorSelection
): GemAsset {
    var updated = asset
    selection.connectionIds.forEach { connectionId ->
        updated = updated.disconnect(connectionId = connectionId, graphId = selection.graphId)
    }
    selection.nodeIds.forEach { nodeId ->
        updated = updated.removeNode(nodeId = nodeId, graphId = selection.graphId)
    }
    return updated
}

internal data class GemCanvasConnectionCurve(
    val start: Offset,
    val control1: Offset,
    val control2: Offset,
    val end: Offset
)

internal fun gemCanvasConnectionCurve(
    start: Offset,
    end: Offset
): GemCanvasConnectionCurve {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val chord = sqrt(dx * dx + dy * dy)
    // Scale tension with the total chord length for a consistent curve feel regardless
    // of direction. Using chord (not just dx) handles backward connections and vertically
    // aligned nodes naturally — they get a wider loop instead of a flat, degenerate curve.
    val tension = (chord * 0.4f).coerceAtLeast(80f)
    return GemCanvasConnectionCurve(
        start = start,
        control1 = Offset(start.x + tension, start.y),
        control2 = Offset(end.x - tension, end.y),
        end = end
    )
}

internal fun findGemCanvasConnectionAt(
    graph: GemGraph,
    worldPoint: Offset,
    tolerance: Float = GEM_CANVAS_CONNECTION_HIT_TOLERANCE,
    nodePositions: Map<String, Offset> = graph.nodes.associate { node -> node.id to node.layout.position.toOffset() }
): GemConnection? {
    return graph.connections
        .mapNotNull { connection ->
            val fromNode = graph.node(connection.from.nodeId) ?: return@mapNotNull null
            val toNode = graph.node(connection.to.nodeId) ?: return@mapNotNull null
            val from = gemCanvasPinPosition(
                node = fromNode,
                pinId = connection.from.pinId,
                position = nodePositions.getValue(fromNode.id)
            ) ?: return@mapNotNull null
            val to = gemCanvasPinPosition(
                node = toNode,
                pinId = connection.to.pinId,
                position = nodePositions.getValue(toNode.id)
            ) ?: return@mapNotNull null
            connection to gemCanvasConnectionCurve(from, to).distanceTo(worldPoint)
        }
        .filter { (_, distance) -> distance <= tolerance }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

internal fun gemCanvasPinPosition(
    node: GemNodeInstance,
    pinId: String,
    position: Offset = node.layout.position.toOffset(),
    density: Float = 2f
): Offset? {
    val metrics = measureGemCanvasNode(node, density)
    val inputPins = node.pins.filter { it.direction == GemPinDirection.INPUT }
    val outputPins = node.pins.filter { it.direction == GemPinDirection.OUTPUT }
    val inputIndex = inputPins.indexOfFirst { it.id == pinId }
    if (inputIndex >= 0) {
        return Offset(
            x = position.x +
                GEM_CANVAS_NODE_BODY_PADDING +
                GEM_CANVAS_NODE_PIN_LABEL_OFFSET +
                GEM_CANVAS_NODE_PIN_RADIUS,
            y = pinCenterY(position = position, rowIndex = inputIndex)
        )
    }

    val outputIndex = outputPins.indexOfFirst { it.id == pinId }
    if (outputIndex >= 0) {
        return Offset(
            x = position.x +
                metrics.width -
                GEM_CANVAS_NODE_BODY_PADDING -
                GEM_CANVAS_NODE_PIN_LABEL_OFFSET -
                GEM_CANVAS_NODE_PIN_RADIUS,
            y = pinCenterY(position = position, rowIndex = outputIndex)
        )
    }

    return null
}

internal fun GemNodePosition.toOffset(): Offset = Offset(x = x, y = y)

internal fun Offset.toGemNodePosition(): GemNodePosition = GemNodePosition(x = x, y = y)

private data class ResolvedGemCanvasPin(
    val node: GemNodeInstance,
    val pin: GemPin
)

private fun GemGraph.resolvePin(ref: GemPinRef): ResolvedGemCanvasPin? {
    val node = node(ref.nodeId) ?: return null
    val pin = node.pins.firstOrNull { it.id == ref.pinId } ?: return null
    return ResolvedGemCanvasPin(node = node, pin = pin)
}

private fun missingPinEvaluation(
    graph: GemGraph,
    ref: GemPinRef
): GemCanvasConnectionEvaluation {
    val node = graph.node(ref.nodeId)
    return GemCanvasConnectionEvaluation(
        errorCode = if (node == null) GemValidationErrorCode.MISSING_CONNECTION_NODE else GemValidationErrorCode.MISSING_CONNECTION_PIN,
        message = if (node == null) {
            "Node '${ref.nodeId}' does not exist."
        } else {
            "Pin '${ref.pinId}' does not exist on node '${ref.nodeId}'."
        }
    )
}

private fun pinCenterY(
    position: Offset,
    rowIndex: Int
): Float {
    return position.y +
        GEM_CANVAS_NODE_HEADER_HEIGHT +
        GEM_CANVAS_NODE_BODY_PADDING +
        rowIndex * GEM_CANVAS_NODE_PIN_ROW_HEIGHT +
        GEM_CANVAS_NODE_PIN_ROW_HEIGHT / 2f
}

internal fun Double.toCanvasNumberLabel(): String {
    val rounded = roundToInt()
    return if (this == rounded.toDouble()) {
        rounded.toString()
    } else {
        toString()
    }
}

private fun Rect.normalized(): Rect = Rect(
    left = min(left, right),
    top = min(top, bottom),
    right = max(left, right),
    bottom = max(top, bottom)
)

private fun GemCanvasConnectionCurve.distanceTo(point: Offset): Float {
    var nearest = Float.MAX_VALUE
    var previous = start
    for (step in 1..18) {
        val t = step / 18f
        val current = cubicBezier(t)
        nearest = min(nearest, point.distanceToSegment(previous, current))
        previous = current
    }
    return nearest
}

private fun GemCanvasConnectionCurve.cubicBezier(t: Float): Offset {
    val oneMinusT = 1f - t
    return (start * (oneMinusT * oneMinusT * oneMinusT)) +
        (control1 * (3f * oneMinusT * oneMinusT * t)) +
        (control2 * (3f * oneMinusT * t * t)) +
        (end * (t * t * t))
}

private fun Offset.distanceToSegment(
    start: Offset,
    end: Offset
): Float {
    val segment = end - start
    val pointDelta = this - start
    val denominator = segment.x * segment.x + segment.y * segment.y
    if (denominator == 0f) {
        return distanceTo(start)
    }
    val projection = ((pointDelta.x * segment.x) + (pointDelta.y * segment.y)) / denominator
    val clamped = projection.coerceIn(0f, 1f)
    val nearest = start + (segment * clamped)
    return distanceTo(nearest)
}

private fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}

private operator fun Offset.plus(other: Offset): Offset = Offset(
    x = x + other.x,
    y = y + other.y
)

private operator fun Offset.minus(other: Offset): Offset = Offset(
    x = x - other.x,
    y = y - other.y
)

private operator fun Offset.times(scale: Float): Offset = Offset(
    x = x * scale,
    y = y * scale
)
