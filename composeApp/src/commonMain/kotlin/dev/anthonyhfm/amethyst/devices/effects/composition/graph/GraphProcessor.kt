package dev.anthonyhfm.amethyst.devices.effects.composition.graph

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.NodeRegistry
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.distanceSquared
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.min
import kotlin.math.roundToInt

object GraphProcessor {
    private const val DEFAULT_FRAME_COUNT = 32

    data class CompositionRenderPlan(
        val frames: List<ScheduledFrame>,
        val errors: List<String> = emptyList(),
    )

    data class ScheduledFrame(
        val delayMs: Double,
        val signals: List<Signal.LED>,
    )

    fun render(inputSignals: List<Signal.LED>, graph: CompositionGraph): CompositionRenderPlan {
        val validation = GraphValidator.validate(graph)
        if (!validation.isValid) {
            return CompositionRenderPlan(frames = emptyList(), errors = validation.errors)
        }

        graph.node(graph.outputNodeId)
            ?: return CompositionRenderPlan(emptyList(), listOf("Output node is missing."))
        val bounds = resolveBounds()
        val outputOrigin = inputSignals.firstOrNull()?.origin
        val frames = (0 until DEFAULT_FRAME_COUNT).map { frameIndex ->
            val progress = frameIndex.toFloat() / (DEFAULT_FRAME_COUNT - 1).toFloat()
            val delay = 450.0 * progress
            val signals = renderFrame(
                graph = graph,
                progress = progress,
                outputOrigin = outputOrigin,
                bounds = bounds,
            )
            ScheduledFrame(
                delayMs = delay,
                signals = signals,
            )
        }

        return CompositionRenderPlan(frames = frames)
    }

    fun renderFrame(
        graph: CompositionGraph,
        progress: Float,
        outputOrigin: Any?,
        bounds: Pair<IntOffset, IntSize> = resolveBounds(),
    ): List<Signal.LED> {
        if (!GraphValidator.validate(graph).isValid) return emptyList()
        val output = graph.node(graph.outputNodeId) ?: return emptyList()
        val context = EvaluationContext(
            bounds = bounds,
            outputOrigin = outputOrigin,
            progress = progress.coerceIn(0f, 1f),
        )
        return graph.connections
            .filter { it.toNodeId == output.id }
            .flatMap { evaluateNode(graph, it.fromNodeId, context) }
            .flatMap { it.strokes }
            .flatMap { stroke -> rasterizeStroke(stroke, bounds) }
    }

    private fun evaluateNode(
        graph: CompositionGraph,
        nodeId: String,
        context: EvaluationContext,
    ): List<GeometryFrame> {
        val node = graph.node(nodeId) ?: return emptyList()
        val definition = NodeRegistry.definitionFor(node) ?: return emptyList()
        val inputContext = definition.inputContext(node, context)
        val inputFrames = graph.connections
            .filter { it.toNodeId == node.id }
            .flatMap { evaluateNode(graph, it.fromNodeId, inputContext) }

        return if (definition.hasInput) {
            definition.transformFrames(node, context, inputFrames)
        } else {
            definition.sourceFrames(node, context)
        }
    }

    private fun rasterizeStroke(
        stroke: GeometryStroke,
        bounds: Pair<IntOffset, IntSize>,
    ): List<Signal.LED> {
        if (stroke.points.isEmpty()) return emptyList()

        val minX = bounds.first.x
        val minY = bounds.first.y
        val maxX = minX + bounds.second.width - 1
        val maxY = minY + bounds.second.height - 1
        if (stroke.points.size == 1 && stroke.thickness <= 0f) {
            val point = stroke.points.first()
            val x = point.x.roundToInt()
            val y = point.y.roundToInt()
            return if (x in minX..maxX && y in minY..maxY) {
                listOf(Signal.LED(origin = stroke.origin, x = x, y = y, color = stroke.color))
            } else {
                emptyList()
            }
        }
        val maxDistanceSquared = stroke.thickness * stroke.thickness
        val signals = mutableListOf<Signal.LED>()

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                val point = Vec2(x.toFloat(), y.toFloat())
                val distanceSquared = distanceToPolylineSquared(point, stroke.points)
                if (distanceSquared <= maxDistanceSquared) {
                    signals.add(Signal.LED(origin = stroke.origin, x = x, y = y, color = stroke.color))
                }
            }
        }

        return signals
    }

    private fun distanceToPolylineSquared(point: Vec2, polyline: List<Vec2>): Float {
        if (polyline.size == 1) return point.distanceSquared(polyline.first())

        var best = Float.POSITIVE_INFINITY
        for (index in 0 until polyline.lastIndex) {
            best = min(best, distanceToSegmentSquared(point, polyline[index], polyline[index + 1]))
        }
        return best
    }

    private fun distanceToSegmentSquared(point: Vec2, start: Vec2, end: Vec2): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0.000001f) return point.distanceSquared(start)
        val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        val projected = Vec2(start.x + t * dx, start.y + t * dy)
        return point.distanceSquared(projected)
    }

    fun resolveBounds(): Pair<IntOffset, IntSize> {
        val bounds = WorkspaceRepository.bounds
        if (bounds.second.width > 0 && bounds.second.height > 0) return bounds
        return IntOffset(0, 0) to IntSize(10, 10)
    }
}
