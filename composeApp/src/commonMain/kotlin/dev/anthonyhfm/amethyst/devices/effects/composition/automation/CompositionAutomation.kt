package dev.anthonyhfm.amethyst.devices.effects.composition.automation

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.*
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

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

/** Metadata and immutable state conversion for every currently editable node value. */
data class CompositionAutomationParameter(
    val id: String,
    val label: String,
    val minimum: Float,
    val maximum: Float,
    val bipolar: Boolean = minimum < 0f,
    val integer: Boolean = false,
    val get: (CompositionNode) -> Float,
    val set: (CompositionNode, Float) -> CompositionNode,
) {
    fun normalise(value: Float): Float = if (maximum == minimum) 0f else
        (((value.coerceIn(minimum, maximum) - minimum) / (maximum - minimum)) * 2f - 1f).coerceIn(-1f, 1f)
    fun denormalise(value: Float): Float {
        val raw = minimum + ((value.coerceIn(-1f, 1f) + 1f) * .5f) * (maximum - minimum)
        return if (integer) raw.roundToInt().toFloat() else raw
    }
    fun format(value: Float): String = when {
        integer -> value.roundToInt().toString()
        maximum == 360f -> "${value.roundToInt()}°"
        else -> ((value * 100f).roundToInt() / 100f).toString()
    }
}

object CompositionAutomationParameters {
    fun forNode(node: CompositionNode): List<CompositionAutomationParameter> = when (node.state) {
        is ScannerNodeState -> listOf(float("angle", "Angle", -180f, 180f, { (it.state as ScannerNodeState).angleDegrees }) { n, v -> n.copy(state = (n.state as ScannerNodeState).copy(angleDegrees = v)) })
        is NoiseNodeState -> listOf(int("regenerations", "Regenerations", 1, 16, { (it.state as NoiseNodeState).regenerations }) { n, v -> n.copy(state = (n.state as NoiseNodeState).copy(regenerations = v.roundToInt())) }, float("intensity", "Intensity", 0f, 1f, { (it.state as NoiseNodeState).intensity }) { n, v -> n.copy(state = (n.state as NoiseNodeState).copy(intensity = v)) })
        is WaterdropNodeState -> listOf(float("origin-x", "Origin X", 0f, 1f, { (it.state as WaterdropNodeState).originX }) { n, v -> n.copy(state = (n.state as WaterdropNodeState).copy(originX = v)) }, float("origin-y", "Origin Y", 0f, 1f, { (it.state as WaterdropNodeState).originY }) { n, v -> n.copy(state = (n.state as WaterdropNodeState).copy(originY = v)) }, float("curvature", "Curvature", .5f, 8f, { (it.state as WaterdropNodeState).curvature }) { n, v -> n.copy(state = (n.state as WaterdropNodeState).copy(curvature = v)) })
        is SpiralNodeState -> listOf(float("origin-x", "Origin X", 0f, 1f, { (it.state as SpiralNodeState).originX }) { n, v -> n.copy(state = (n.state as SpiralNodeState).copy(originX = v)) }, float("origin-y", "Origin Y", 0f, 1f, { (it.state as SpiralNodeState).originY }) { n, v -> n.copy(state = (n.state as SpiralNodeState).copy(originY = v)) }, float("turns", "Turns", .25f, 12f, { (it.state as SpiralNodeState).turns }) { n, v -> n.copy(state = (n.state as SpiralNodeState).copy(turns = v)) })
        is RotateNodeState -> listOf(float("angle", "Angle", -180f, 180f, { (it.state as RotateNodeState).angleDegrees }) { n, v -> n.copy(state = (n.state as RotateNodeState).copy(angleDegrees = v)) })
        is MirrorNodeState -> listOf(float("angle", "Angle", -180f, 180f, { (it.state as MirrorNodeState).angleDegrees }) { n, v -> n.copy(state = (n.state as MirrorNodeState).copy(angleDegrees = v)) })
        is PinchNodeState -> listOf(float("pinch", "Pinch", -2f, 2f, { (it.state as PinchNodeState).pinch }) { n, v -> n.copy(state = (n.state as PinchNodeState).copy(pinch = v)) }, choice("bilateral", "Bilateral", listOf("Off", "On"), { if ((it.state as PinchNodeState).bilateral) "On" else "Off" }) { n, value -> n.copy(state = (n.state as PinchNodeState).copy(bilateral = value == "On")) })
        is SymmetryNodeState -> listOf(choice("mode", "Mode", listOf("mirror-half", "quad-mirror", "quad-pinwheel"), { (it.state as SymmetryNodeState).mode }) { n, value -> n.copy(state = (n.state as SymmetryNodeState).copy(mode = value)) }, choice("axis", "Axis", listOf("horizontal", "vertical"), { (it.state as SymmetryNodeState).axis }) { n, value -> n.copy(state = (n.state as SymmetryNodeState).copy(axis = value)) }, choice("anchor", "Source anchor", listOf("bl", "br", "tr", "tl"), { (it.state as SymmetryNodeState).sourceAnchor }) { n, value -> n.copy(state = (n.state as SymmetryNodeState).copy(sourceAnchor = value)) })
        is MoveNodeState -> listOf(int("offset-x", "Offset X", -64, 64, { (it.state as MoveNodeState).offsetX }) { n, v -> n.copy(state = (n.state as MoveNodeState).copy(offsetX = v.roundToInt())) }, int("offset-y", "Offset Y", -64, 64, { (it.state as MoveNodeState).offsetY }) { n, v -> n.copy(state = (n.state as MoveNodeState).copy(offsetY = v.roundToInt())) })
        is LoopNodeState -> listOf(float("start", "Start", 0f, 1f, { (it.state as LoopNodeState).startProgress }) { n, v -> n.copy(state = (n.state as LoopNodeState).copy(startProgress = v)) }, float("end", "End", 0f, 1f, { (it.state as LoopNodeState).endProgress }) { n, v -> n.copy(state = (n.state as LoopNodeState).copy(endProgress = v)) }, int("repeats", "Repeats", 1, 16, { (it.state as LoopNodeState).repeats }) { n, v -> n.copy(state = (n.state as LoopNodeState).copy(repeats = v.roundToInt())) })
        is LineNodeState -> listOf(float("start-x", "Start X", 0f, 1f, { (it.state as LineNodeState).startX }) { n,v->n.copy(state=(n.state as LineNodeState).copy(startX=v)) }, float("start-y", "Start Y", 0f, 1f, { (it.state as LineNodeState).startY }) { n,v->n.copy(state=(n.state as LineNodeState).copy(startY=v)) }, float("end-x", "End X", 0f, 1f, { (it.state as LineNodeState).endX }) { n,v->n.copy(state=(n.state as LineNodeState).copy(endX=v)) }, float("end-y", "End Y", 0f, 1f, { (it.state as LineNodeState).endY }) { n,v->n.copy(state=(n.state as LineNodeState).copy(endY=v)) }, float("thickness", "Thickness", 0f, 4f, { (it.state as LineNodeState).thickness }) { n,v->n.copy(state=(n.state as LineNodeState).copy(thickness=v)) })
        is ColorNodeState -> listOf(float("alpha", "Alpha", 0f, 1f, { (it.state as ColorNodeState).alpha }) { n,v->n.copy(state=(n.state as ColorNodeState).copy(alpha=v)) })
        is TimeCutNodeState -> listOf(
            float("start", "Start", 0f, 1f, { (it.state as TimeCutNodeState).startProgress }) { n, v ->
                n.copy(state = (n.state as TimeCutNodeState).copy(startProgress = v.coerceAtMost((n.state as TimeCutNodeState).endProgress)))
            },
            float("end", "End", 0f, 1f, { (it.state as TimeCutNodeState).endProgress }) { n, v ->
                n.copy(state = (n.state as TimeCutNodeState).copy(endProgress = v.coerceAtLeast((n.state as TimeCutNodeState).startProgress)))
            },
        )
        is VortexNodeState -> listOf(float("origin-x","Origin X",0f,1f,{(it.state as VortexNodeState).originX}){n,v->n.copy(state=(n.state as VortexNodeState).copy(originX=v))},float("origin-y","Origin Y",0f,1f,{(it.state as VortexNodeState).originY}){n,v->n.copy(state=(n.state as VortexNodeState).copy(originY=v))},float("strength","Strength",-2f,2f,{(it.state as VortexNodeState).strength}){n,v->n.copy(state=(n.state as VortexNodeState).copy(strength=v))},float("radius","Radius",0f,1f,{(it.state as VortexNodeState).radius}){n,v->n.copy(state=(n.state as VortexNodeState).copy(radius=v))},float("falloff","Falloff",.1f,4f,{(it.state as VortexNodeState).falloff}){n,v->n.copy(state=(n.state as VortexNodeState).copy(falloff=v))})
        is FocusNodeState -> listOf(float("radius","Radius",0f,1f,{(it.state as FocusNodeState).radius}){n,v->n.copy(state=(n.state as FocusNodeState).copy(radius=v))},float("feather","Feather",0f,1f,{(it.state as FocusNodeState).feather}){n,v->n.copy(state=(n.state as FocusNodeState).copy(feather=v))})
        is ColorShiftNodeState -> listOf(float("hue","Hue",-180f,180f,{(it.state as ColorShiftNodeState).hueDegrees}){n,v->n.copy(state=(n.state as ColorShiftNodeState).copy(hueDegrees=v))},float("saturation","Saturation",-1f,1f,{(it.state as ColorShiftNodeState).saturationDelta}){n,v->n.copy(state=(n.state as ColorShiftNodeState).copy(saturationDelta=v))},float("lightness","Lightness",-1f,1f,{(it.state as ColorShiftNodeState).lightnessDelta}){n,v->n.copy(state=(n.state as ColorShiftNodeState).copy(lightnessDelta=v))})
        is TimeWrapNodeState -> listOf(float("target-start","Target start",0f,1f,{(it.state as TimeWrapNodeState).targetStart}){n,v->n.copy(state=(n.state as TimeWrapNodeState).copy(targetStart=v))},float("target-end","Target end",0f,1f,{(it.state as TimeWrapNodeState).targetEnd}){n,v->n.copy(state=(n.state as TimeWrapNodeState).copy(targetEnd=v))})
        is SliceNodeState -> listOf(float("angle","Angle",-180f,180f,{(it.state as SliceNodeState).angleDegrees}){n,v->n.copy(state=(n.state as SliceNodeState).copy(angleDegrees=v))},float("width","Width",0f,1f,{(it.state as SliceNodeState).width}){n,v->n.copy(state=(n.state as SliceNodeState).copy(width=v))})
        is FrameLimitNodeState -> listOf(int("frames","Frames per cycle",1,120,{(it.state as FrameLimitNodeState).frames}){n,v->n.copy(state=(n.state as FrameLimitNodeState).copy(frames=v.roundToInt()))})
        else -> emptyList()
    }

    fun byId(node: CompositionNode, id: String) = forNode(node).firstOrNull { it.id == id }

    private fun float(id: String, label: String, min: Float, max: Float, get: (CompositionNode) -> Float, set: (CompositionNode, Float) -> CompositionNode) = CompositionAutomationParameter(id, label, min, max, get = get, set = set)
    private fun int(id: String, label: String, min: Int, max: Int, get: (CompositionNode) -> Int, set: (CompositionNode, Float) -> CompositionNode) = CompositionAutomationParameter(id, label, min.toFloat(), max.toFloat(), integer = true, get = { get(it).toFloat() }, set = set)
    private fun choice(id: String, label: String, values: List<String>, get: (CompositionNode) -> String, set: (CompositionNode, String) -> CompositionNode) = CompositionAutomationParameter(id, label, 0f, (values.size - 1).toFloat(), integer = true, get = { values.indexOf(get(it)).coerceAtLeast(0).toFloat() }, set = { node, value -> set(node, values[value.roundToInt().coerceIn(0, values.lastIndex)]) })
}

fun CompositionNode.automatedAt(progress: Float): CompositionNode {
    var result = this
    automation.forEach { lane ->
        val parameter = CompositionAutomationParameters.byId(result, lane.parameterId) ?: return@forEach
        result = parameter.set(result, parameter.denormalise(lane.valueAt(progress, parameter.normalise(parameter.get(result)))))
    }
    return result
}

fun CompositionNode.lane(parameterId: String): CompositionAutomationLane? = automation.firstOrNull { it.parameterId == parameterId }
