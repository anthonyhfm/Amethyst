package dev.anthonyhfm.amethyst.devices.effects.composition.automation

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.NodeRegistry
import kotlinx.serialization.Serializable

/** A normalised automation lane owned by one composition-node parameter. */
@Serializable
data class CompositionAutomationLane(
    val parameterId: String,
    val points: List<CompositionAutomationPoint> = emptyList(),
) {
    fun valueAt(progress: Float, fallback: Float): Float {
        val ordered = points.sortedBy(CompositionAutomationPoint::progress)
        if (ordered.isEmpty()) return fallback
        if (progress <= ordered.first().progress) return ordered.first().value
        if (progress >= ordered.last().progress) return ordered.last().value
        val endIndex = ordered.indexOfFirst { it.progress >= progress }.coerceAtLeast(1)
        val start = ordered[endIndex - 1]
        val end = ordered[endIndex]
        val span = (end.progress - start.progress).coerceAtLeast(0.0001f)
        val t = ((progress - start.progress) / span).coerceIn(0f, 1f)
        return start.segmentValueAt(end, t)
            .coerceIn(-1f, 1f)
    }

    fun normalised(): CompositionAutomationLane = copy(
        points = points.map { it.normalised() }.sortedBy(CompositionAutomationPoint::progress)
    )
}

@Serializable
data class CompositionAutomationPoint(
    val progress: Float,
    val value: Float,
    /** Segment-relative X coordinate of the handle leading into this point. */
    val inHandleTime: Float? = null,
    val inHandleValue: Float? = null,
    /** Segment-relative X coordinate of the handle leaving this point. */
    val outHandleTime: Float? = null,
    val outHandleValue: Float? = null,
    val pointId: String = UUID.randomUUID(),
) {
    fun normalised() = copy(
        progress = progress.coerceIn(0f, 1f),
        value = value.coerceIn(-1f, 1f),
        inHandleTime = inHandleTime?.coerceIn(0f, 1f),
        inHandleValue = inHandleValue?.coerceIn(-1f, 1f),
        outHandleTime = outHandleTime?.coerceIn(0f, 1f),
        outHandleValue = outHandleValue?.coerceIn(-1f, 1f),
        pointId = pointId.ifBlank { UUID.randomUUID() },
    )
}

internal fun cubic(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
    val u = 1f - t
    return u * u * u * a + 3f * u * u * t * b + 3f * u * t * t * c + t * t * t * d
}

/** Evaluates one segment using cubic time and value coordinates. */
internal fun CompositionAutomationPoint.segmentValueAt(end: CompositionAutomationPoint, progress: Float): Float {
    val x1 = outHandleTime ?: (1f / 3f)
    val x2 = end.inHandleTime ?: (2f / 3f)
    val linearFirst = value + (end.value - value) / 3f
    val linearSecond = value + (end.value - value) * (2f / 3f)
    var low = 0f
    var high = 1f
    repeat(18) {
        val middle = (low + high) / 2f
        val x = cubic(0f, x1, x2, 1f, middle)
        if (x < progress) low = middle else high = middle
    }
    val t = (low + high) / 2f
    return cubic(value, outHandleValue ?: linearFirst, end.inHandleValue ?: linearSecond, end.value, t)
}

fun CompositionNode.automationParameters() = NodeRegistry.definitionFor(this)?.automationParameters.orEmpty()

fun CompositionNode.automationParameter(id: String) = automationParameters().firstOrNull { it.id == id }

fun CompositionNode.automatedAt(progress: Float): CompositionNode {
    var result = this
    automation.forEach { lane ->
        val parameter = result.automationParameter(lane.parameterId) ?: return@forEach
        val fallback = parameter.valueOf(result) ?: return@forEach
        result = parameter.withValue(result, parameter.denormalise(lane.valueAt(progress, parameter.normalise(fallback)))) ?: result
    }
    return result
}

fun CompositionNode.lane(parameterId: String): CompositionAutomationLane? = automation.firstOrNull { it.parameterId == parameterId }
