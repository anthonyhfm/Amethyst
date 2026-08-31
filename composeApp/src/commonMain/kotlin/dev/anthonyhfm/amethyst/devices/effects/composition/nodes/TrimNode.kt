package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Crop
import com.composables.icons.lucide.Lucide
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionGraph
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.GraphProcessor
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.DialType
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class TrimNodeState(
    val padding: Float = 0f,
) : CompositionNodeState

object TrimNode : TransformNode() {
    override val automationParameters = listOf(
        floatAutomationParameter<TrimNodeState>("padding", "Padding", 0f, 0.5f, TrimNodeState::padding) { state, value -> state.copy(padding = value) },
    )

    override val type = "trim"
    override val label = "Trim"
    override val icon = Lucide.Crop
    override val pickerCategory = CompositionNodePickerCategory.Time

    override val bodyWidth: Dp = 128.dp
    override val bodyHeight: Dp = 128.dp

    override fun defaultState(): CompositionNodeState = TrimNodeState()

    private data class RangeCacheKey(
        val nodeId: String,
        val nodesHash: Int,
        val connectionsHash: Int,
        val bounds: Pair<IntOffset, IntSize>,
        val triggerOrigin: Vec2?,
    )

    private var cachedRange: Pair<RangeCacheKey, Pair<Float, Float>>? = null

    override fun evaluate(
        graph: CompositionGraph,
        node: CompositionNode,
        context: EvaluationContext,
    ): List<GeometryFrame> {
        val upstreamNodeIds = graph.connections
            .filter { it.toNodeId == node.id }
            .map { it.fromNodeId }
        if (upstreamNodeIds.isEmpty()) return emptyList()

        val state = node.state as? TrimNodeState ?: TrimNodeState()
        val (tStart, tEnd) = getOrDetectActiveRange(graph, node.id, upstreamNodeIds, context)

        val rawSpan = (tEnd - tStart).coerceAtLeast(0f)
        val paddedStart = (tStart - state.padding * rawSpan).coerceIn(0f, 1f)
        val paddedEnd = (tEnd + state.padding * rawSpan).coerceIn(0f, 1f)
        val span = (paddedEnd - paddedStart).coerceAtLeast(0f)

        val mappedProgress = if (span < 0.0001f) {
            paddedStart
        } else {
            (paddedStart + context.progress.coerceIn(0f, 1f) * span).coerceIn(0f, 1f)
        }

        val mappedContext = context.copy(progress = mappedProgress)
        return upstreamNodeIds.flatMap { upstreamId ->
            GraphProcessor.evaluateNode(graph, upstreamId, mappedContext)
        }
    }

    private fun getOrDetectActiveRange(
        graph: CompositionGraph,
        nodeId: String,
        upstreamNodeIds: List<String>,
        context: EvaluationContext,
    ): Pair<Float, Float> {
        val nodesHash = graph.nodes.fold(0) { acc, n -> acc * 31 + n.state.hashCode() + n.type.hashCode() }
        val connectionsHash = graph.connections.hashCode()
        val cacheKey = RangeCacheKey(nodeId, nodesHash, connectionsHash, context.bounds, context.triggerOrigin)

        val existing = cachedRange
        if (existing != null && existing.first == cacheKey) {
            return existing.second
        }

        val computed = detectActiveRange(graph, upstreamNodeIds, context)
        cachedRange = Pair(cacheKey, computed)
        return computed
    }

    private fun detectActiveRange(
        graph: CompositionGraph,
        upstreamNodeIds: List<String>,
        context: EvaluationContext,
    ): Pair<Float, Float> {
        val steps = 64
        var firstActiveIndex = -1
        var lastActiveIndex = -1

        fun hasContentAt(t: Float): Boolean {
            val sampleContext = context.copy(progress = t.coerceIn(0f, 1f))
            val frames = upstreamNodeIds.flatMap { upstreamId ->
                GraphProcessor.evaluateNode(graph, upstreamId, sampleContext)
            }
            if (frames.isEmpty()) return false
            for (frame in frames) {
                for (stroke in frame.strokes) {
                    if (GraphProcessor.hasVisiblePixels(stroke, context.bounds)) {
                        return true
                    }
                }
            }
            return false
        }

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            if (hasContentAt(t)) {
                if (firstActiveIndex == -1) {
                    firstActiveIndex = i
                }
                lastActiveIndex = i
            }
        }

        if (firstActiveIndex == -1) {
            return Pair(0f, 1f)
        }

        val tStart = if (firstActiveIndex > 0) {
            var low = (firstActiveIndex - 1).toFloat() / steps
            var high = firstActiveIndex.toFloat() / steps
            repeat(5) {
                val mid = (low + high) / 2f
                if (hasContentAt(mid)) {
                    high = mid
                } else {
                    low = mid
                }
            }
            high
        } else {
            0f
        }

        var tEnd = if (lastActiveIndex < steps) {
            var low = lastActiveIndex.toFloat() / steps
            var high = (lastActiveIndex + 1).toFloat() / steps
            repeat(5) {
                val mid = (low + high) / 2f
                if (hasContentAt(mid)) {
                    low = mid
                } else {
                    high = mid
                }
            }
            low
        } else {
            1f
        }

        if (tEnd < tStart) {
            tEnd = tStart
        }

        return Pair(tStart, tEnd)
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? TrimNodeState ?: return

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AutomatableDial(
                parameterId = "padding",
                type = DialType.Continuous,
                value = (state.padding / 0.5f).coerceIn(0f, 1f),
                defaultValue = 0f,
                title = "Padding",
                text = "${(state.padding * 100).roundToInt()}%",
                onValueChange = { value ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                padding = (value * 0.5f).coerceIn(0f, 0.5f),
                            )
                        )
                    )
                },
                onResolveTextValue = { value ->
                    val num = value.removeSuffix("%").trim().toFloatOrNull()
                    if (num != null) {
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    padding = (num / 100f).coerceIn(0f, 0.5f),
                                )
                            )
                        )
                    }
                },
            )
        }
    }
}
