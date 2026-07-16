package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import kotlin.math.roundToInt

/** Metadata and state access for one automatable value owned by a node definition. */
class CompositionAutomationParameter internal constructor(
    val id: String,
    val label: String,
    val minimum: Float,
    val maximum: Float,
    val bipolar: Boolean = minimum < 0f,
    val integer: Boolean = false,
    private val readState: (CompositionNodeState) -> Float?,
    private val writeState: (CompositionNodeState, Float) -> CompositionNodeState?,
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

    fun valueOf(node: CompositionNode): Float? = readState(node.state)

    fun withValue(node: CompositionNode, value: Float): CompositionNode? =
        writeState(node.state, value)?.let { state -> node.copy(state = state) }
}

internal inline fun <reified State : CompositionNodeState> floatAutomationParameter(
    id: String,
    label: String,
    minimum: Float,
    maximum: Float,
    noinline get: (State) -> Float,
    noinline set: (State, Float) -> State,
): CompositionAutomationParameter = typedAutomationParameter<State>(
    id, label, minimum, maximum, integer = false, get = get, set = set,
)

internal inline fun <reified State : CompositionNodeState> intAutomationParameter(
    id: String,
    label: String,
    minimum: Int,
    maximum: Int,
    noinline get: (State) -> Int,
    noinline set: (State, Int) -> State,
): CompositionAutomationParameter = typedAutomationParameter<State>(
    id, label, minimum.toFloat(), maximum.toFloat(), integer = true,
    get = { get(it).toFloat() },
    set = { state, value -> set(state, value.roundToInt()) },
)

internal inline fun <reified State : CompositionNodeState> choiceAutomationParameter(
    id: String,
    label: String,
    values: List<String>,
    noinline get: (State) -> String,
    noinline set: (State, String) -> State,
): CompositionAutomationParameter = typedAutomationParameter<State>(
    id, label, 0f, (values.size - 1).toFloat(), integer = true,
    get = { values.indexOf(get(it)).coerceAtLeast(0).toFloat() },
    set = { state, value -> set(state, values[value.roundToInt().coerceIn(0, values.lastIndex)]) },
)

internal inline fun <reified State : CompositionNodeState> typedAutomationParameter(
    id: String,
    label: String,
    minimum: Float,
    maximum: Float,
    integer: Boolean,
    noinline get: (State) -> Float,
    noinline set: (State, Float) -> State,
): CompositionAutomationParameter = CompositionAutomationParameter(
    id = id,
    label = label,
    minimum = minimum,
    maximum = maximum,
    integer = integer,
    readState = { state -> (state as? State)?.let(get) },
    writeState = { state, value -> (state as? State)?.let { set(it, value.coerceIn(minimum, maximum)) } },
)
