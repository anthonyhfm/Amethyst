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
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.automatedAt
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.CompositionNodeDefinition
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.math.min
import kotlin.math.roundToInt

object GraphProcessor {
    private const val ACTIVE_RANGE_STEPS = 64
    private const val ACTIVE_RANGE_REFINEMENTS = 5
    private const val MAX_ACTIVE_RANGE_CACHE_ENTRIES = 256

    private data class SourceRangeCacheKey(
        val node: CompositionNode,
        val bounds: Pair<IntOffset, IntSize>,
        val triggerOrigin: Vec2?,
    )

    private val sourceRangeCacheLock = SynchronizedObject()
    private val sourceRangeCache = mutableMapOf<SourceRangeCacheKey, Pair<Float, Float>>()

    fun renderFrame(
        graph: CompositionGraph,
        progress: Float,
        outputOrigin: Any?,
        bounds: Pair<IntOffset, IntSize> = resolveBounds(),
        triggerOrigin: Vec2? = null,
        durationMs: Double = 1_000.0,
    ): List<Signal.LED> {
        if (!GraphValidator.validate(graph).isValid) return emptyList()
        val output = graph.node(graph.outputNodeId) ?: return emptyList()
        val context = EvaluationContext(
            bounds = bounds,
            outputOrigin = outputOrigin,
            progress = progress.coerceIn(0f, 1f),
            triggerOrigin = triggerOrigin,
            durationMs = durationMs.coerceAtLeast(1.0),
        )
        return graph.connections
            .filter { it.toNodeId == output.id }
            .flatMap { evaluateNode(graph, it.fromNodeId, context) }
            .flatMap { it.strokes }
            .flatMap { stroke -> rasterizeStroke(stroke, bounds) }
            .groupBy { it.x to it.y }
            .map { (_, samples) -> samples.reduce(::over) }
    }

    internal fun evaluateNode(
        graph: CompositionGraph,
        nodeId: String,
        context: EvaluationContext,
    ): List<GeometryFrame> {
        val rawNode = graph.node(nodeId) ?: return emptyList()
        val node = rawNode.automatedAt(context.progress)
        val definition = NodeRegistry.definitionFor(node) ?: return emptyList()
        val custom = definition.evaluate(graph, node, context)
        if (custom != null) return custom

        val inputContext = definition.inputContext(node, context)
        val inputFrames = graph.connections
            .filter { it.toNodeId == node.id }
            .flatMap { evaluateNode(graph, it.fromNodeId, inputContext) }

        return if (definition.hasInput) {
            definition.transformFrames(node, context, inputFrames)
        } else {
            evaluateSource(definition, rawNode, context)
        }
    }

    private fun evaluateSource(
        definition: CompositionNodeDefinition,
        rawNode: CompositionNode,
        context: EvaluationContext,
    ): List<GeometryFrame> {
        if (!definition.stretchVisibleSourceTimeline) {
            return definition.sourceFrames(rawNode.automatedAt(context.progress), context)
        }

        val (activeStart, activeEnd) = sourceActiveRange(definition, rawNode, context)
        val mappedProgress = if (activeEnd - activeStart < 0.0001f) {
            activeStart
        } else {
            activeStart + context.progress.coerceIn(0f, 1f) * (activeEnd - activeStart)
        }
        val mappedContext = context.copy(progress = mappedProgress.coerceIn(0f, 1f))
        return definition.sourceFrames(rawNode.automatedAt(mappedContext.progress), mappedContext)
    }

    private fun sourceActiveRange(
        definition: CompositionNodeDefinition,
        node: CompositionNode,
        context: EvaluationContext,
    ): Pair<Float, Float> {
        val key = SourceRangeCacheKey(node, context.bounds, context.triggerOrigin)
        synchronized(sourceRangeCacheLock) { sourceRangeCache[key] }?.let { return it }

        fun hasContentAt(progress: Float): Boolean {
            val sampleContext = context.copy(progress = progress.coerceIn(0f, 1f))
            val sampleNode = node.automatedAt(sampleContext.progress)
            return definition.sourceFrames(sampleNode, sampleContext).any { frame ->
                frame.strokes.any { stroke -> hasVisiblePixels(stroke, context.bounds) }
            }
        }

        var firstActiveIndex = -1
        var lastActiveIndex = -1
        for (index in 0..ACTIVE_RANGE_STEPS) {
            if (hasContentAt(index.toFloat() / ACTIVE_RANGE_STEPS)) {
                if (firstActiveIndex == -1) firstActiveIndex = index
                lastActiveIndex = index
            }
        }

        val range = if (firstActiveIndex == -1) {
            0f to 1f
        } else {
            val start = if (firstActiveIndex == 0) {
                0f
            } else {
                var invisible = (firstActiveIndex - 1).toFloat() / ACTIVE_RANGE_STEPS
                var visible = firstActiveIndex.toFloat() / ACTIVE_RANGE_STEPS
                repeat(ACTIVE_RANGE_REFINEMENTS) {
                    val middle = (invisible + visible) / 2f
                    if (hasContentAt(middle)) visible = middle else invisible = middle
                }
                visible
            }
            val end = if (lastActiveIndex == ACTIVE_RANGE_STEPS) {
                1f
            } else {
                var visible = lastActiveIndex.toFloat() / ACTIVE_RANGE_STEPS
                var invisible = (lastActiveIndex + 1).toFloat() / ACTIVE_RANGE_STEPS
                repeat(ACTIVE_RANGE_REFINEMENTS) {
                    val middle = (visible + invisible) / 2f
                    if (hasContentAt(middle)) visible = middle else invisible = middle
                }
                visible
            }
            start to end.coerceAtLeast(start)
        }

        synchronized(sourceRangeCacheLock) {
            if (sourceRangeCache.size >= MAX_ACTIVE_RANGE_CACHE_ENTRIES) sourceRangeCache.clear()
            sourceRangeCache[key] = range
        }
        return range
    }

    internal fun hasVisiblePixels(
        stroke: GeometryStroke,
        bounds: Pair<IntOffset, IntSize>,
    ): Boolean {
        if (stroke.points.isEmpty()) return false

        val minX = bounds.first.x
        val minY = bounds.first.y
        val maxX = minX + bounds.second.width - 1
        val maxY = minY + bounds.second.height - 1
        if (stroke.points.size == 1 && stroke.thickness <= 0f) {
            val point = stroke.points.first()
            val x = point.x.roundToInt()
            val y = point.y.roundToInt()
            if (x in minX..maxX && y in minY..maxY) {
                val sample = Vec2(x.toFloat(), y.toFloat())
                val color = stroke.paint.colorAt(sample, bounds)
                val opacity = (color.alpha * stroke.paint.opacityAt(sample, bounds)).coerceIn(0f, 1f)
                return opacity > 0f
            }
            return false
        }
        val maxDistanceSquared = stroke.thickness * stroke.thickness

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                val point = Vec2(x.toFloat(), y.toFloat())
                val distanceSquared = distanceToPolylineSquared(point, stroke.points)
                if (distanceSquared <= maxDistanceSquared) {
                    val pointColor = stroke.paint.colorAt(point, bounds)
                    val opacity = (pointColor.alpha * stroke.paint.opacityAt(point, bounds)).coerceIn(0f, 1f)
                    if (opacity > 0f) return true
                }
            }
        }

        return false
    }

    internal fun rasterizeStroke(
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
                val sample = Vec2(x.toFloat(), y.toFloat())
                val color = stroke.paint.colorAt(sample, bounds)
                val opacity = (color.alpha * stroke.paint.opacityAt(sample, bounds)).coerceIn(0f, 1f)
                if (opacity > 0f) listOf(Signal.LED(origin = stroke.origin, x = x, y = y, color = color.copy(alpha = 1f), opacity = opacity)) else emptyList()
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
                    val pointColor = stroke.paint.colorAt(point, bounds)
                    val opacity = (pointColor.alpha * stroke.paint.opacityAt(point, bounds)).coerceIn(0f, 1f)
                    if (opacity > 0f) signals.add(Signal.LED(origin = stroke.origin, x = x, y = y, color = pointColor.copy(alpha = 1f), opacity = opacity))
                }
            }
        }

        return signals
    }

    private fun over(bottom: Signal.LED, top: Signal.LED): Signal.LED {
        val topAlpha = top.opacity.coerceIn(0f, 1f)
        val bottomAlpha = bottom.opacity.coerceIn(0f, 1f)
        val alpha = topAlpha + bottomAlpha * (1f - topAlpha)
        if (alpha <= 0f) return top.copy(color = androidx.compose.ui.graphics.Color.Black, opacity = 0f)
        fun channel(a: Float, b: Float) = (a * topAlpha + b * bottomAlpha * (1f - topAlpha)) / alpha
        return top.copy(color = androidx.compose.ui.graphics.Color(channel(top.color.red, bottom.color.red), channel(top.color.green, bottom.color.green), channel(top.color.blue, bottom.color.blue)), opacity = alpha)
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
