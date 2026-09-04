package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Waves
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.dot
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.WorkspaceDirectionPicker
import dev.anthonyhfm.amethyst.ui.components.DialType
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MIN_FREQUENCY = 0.2f
private const val MAX_FREQUENCY = 6f
private const val DEFAULT_FREQUENCY = 1.5f

private const val MIN_AMPLITUDE = 0f
private const val MAX_AMPLITUDE = 5f
private const val DEFAULT_AMPLITUDE = 1.5f

private const val MIN_SPEED = -4f
private const val MAX_SPEED = 4f
private const val DEFAULT_SPEED = 1f

@Serializable
data class WaveNodeState(
    val angleDegrees: Float = 0f,
    val frequency: Float = DEFAULT_FREQUENCY,
    val amplitude: Float = DEFAULT_AMPLITUDE,
    val speed: Float = DEFAULT_SPEED,
    val thickness: Float = 1f,
) : CompositionNodeState

object WaveNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        floatAutomationParameter<WaveNodeState>("angle", "Angle", -180f, 180f, WaveNodeState::angleDegrees) { state, value -> state.copy(angleDegrees = value) },
        floatAutomationParameter<WaveNodeState>("frequency", "Frequency", MIN_FREQUENCY, MAX_FREQUENCY, WaveNodeState::frequency) { state, value -> state.copy(frequency = value) },
        floatAutomationParameter<WaveNodeState>("amplitude", "Amplitude", MIN_AMPLITUDE, MAX_AMPLITUDE, WaveNodeState::amplitude) { state, value -> state.copy(amplitude = value) },
        floatAutomationParameter<WaveNodeState>("speed", "Speed", MIN_SPEED, MAX_SPEED, WaveNodeState::speed) { state, value -> state.copy(speed = value) },
        floatAutomationParameter<WaveNodeState>("thickness", "Thickness", 0.1f, 4f, WaveNodeState::thickness) { state, value -> state.copy(thickness = value) },
    )

    override val type = "wave"
    override val label = "Wave"
    override val icon = Lucide.Waves
    override val hasInput = false
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Generators

    override val bodyWidth: Dp = 320.dp
    override val bodyHeight: Dp = 128.dp

    override fun defaultState(): CompositionNodeState = WaveNodeState()

    override fun sourceFrames(node: CompositionNode, context: EvaluationContext): List<GeometryFrame> {
        val state = node.state as? WaveNodeState ?: return emptyList()
        val stroke = buildWaveStroke(state, context) ?: return emptyList()
        return listOf(GeometryFrame(timeMs = 0.0, strokes = listOf(stroke)))
    }

    private fun buildWaveStroke(
        state: WaveNodeState,
        context: EvaluationContext,
    ): GeometryStroke? {
        val radians = state.angleDegrees * PI.toFloat() / 180f
        val axis = Vec2(cos(radians), sin(radians))
        val perp = Vec2(-axis.y, axis.x)
        val minX = context.bounds.first.x.toFloat()
        val minY = context.bounds.first.y.toFloat()
        val maxX = (context.bounds.first.x + context.bounds.second.width - 1).toFloat()
        val maxY = (context.bounds.first.y + context.bounds.second.height - 1).toFloat()
        val center = Vec2((minX + maxX) / 2f, (minY + maxY) / 2f)

        val corners = listOf(
            Vec2(minX, minY),
            Vec2(minX, maxY),
            Vec2(maxX, minY),
            Vec2(maxX, maxY),
        )

        var minPerp = Float.POSITIVE_INFINITY
        var maxPerp = Float.NEGATIVE_INFINITY
        corners.forEach { corner ->
            val rel = Vec2(corner.x - center.x, corner.y - center.y)
            val p = rel.dot(perp)
            minPerp = min(minPerp, p)
            maxPerp = max(maxPerp, p)
        }

        val padding = 2f
        val span = (maxPerp - minPerp) + padding * 2f
        if (span <= 0f) return null

        val startP = minPerp - padding
        val step = 0.25f
        val count = max(12, ceil(span / step).toInt())
        val gridDimension = max(1f, max(context.bounds.second.width, context.bounds.second.height).toFloat())
        val phase = context.progress.coerceIn(0f, 1f) * state.speed * 2f * PI.toFloat()

        val points = (0 until count).map { index ->
            val s = startP + (index.toFloat() / (count - 1).toFloat()) * span
            val normalizedDistance = s / gridDimension
            val phaseArg = 2f * PI.toFloat() * state.frequency * normalizedDistance + phase
            val displacement = sin(phaseArg) * state.amplitude
            Vec2(
                x = center.x + perp.x * s + axis.x * displacement,
                y = center.y + perp.y * s + axis.y * displacement,
            )
        }

        return GeometryStroke(
            points = points,
            color = Color.White,
            thickness = state.thickness.coerceAtLeast(0.1f),
            origin = context.outputOrigin,
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? WaveNodeState ?: return

        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            WorkspaceDirectionPicker(
                parameterId = "angle",
                angleDegrees = state.angleDegrees,
                onAngleChange = { angle ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                angleDegrees = angle,
                            )
                        )
                    )
                },
                contentDescription = "Wave direction",
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .padding(start = 12.dp)
                    .fillMaxHeight()
                    .aspectRatio(1f),
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AutomatableDial(
                    parameterId = "frequency",
                    type = DialType.Continuous,
                    value = (state.frequency - MIN_FREQUENCY) / (MAX_FREQUENCY - MIN_FREQUENCY),
                    defaultValue = (DEFAULT_FREQUENCY - MIN_FREQUENCY) / (MAX_FREQUENCY - MIN_FREQUENCY),
                    title = "Freq",
                    text = "${(state.frequency * 10).roundToInt() / 10f}",
                    onValueChange = { value ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    frequency = (MIN_FREQUENCY + value * (MAX_FREQUENCY - MIN_FREQUENCY)).coerceIn(MIN_FREQUENCY, MAX_FREQUENCY),
                                )
                            )
                        )
                    },
                    onResolveTextValue = { value ->
                        value.toFloatOrNull()?.let { freqVal ->
                            onNodeChange(
                                node.copy(
                                    state = state.copy(
                                        frequency = freqVal.coerceIn(MIN_FREQUENCY, MAX_FREQUENCY),
                                    )
                                )
                            )
                        }
                    },
                )
                AutomatableDial(
                    parameterId = "amplitude",
                    type = DialType.Continuous,
                    value = (state.amplitude - MIN_AMPLITUDE) / (MAX_AMPLITUDE - MIN_AMPLITUDE),
                    defaultValue = (DEFAULT_AMPLITUDE - MIN_AMPLITUDE) / (MAX_AMPLITUDE - MIN_AMPLITUDE),
                    title = "Amp",
                    text = "${(state.amplitude * 10).roundToInt() / 10f}",
                    onValueChange = { value ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    amplitude = (MIN_AMPLITUDE + value * (MAX_AMPLITUDE - MIN_AMPLITUDE)).coerceIn(MIN_AMPLITUDE, MAX_AMPLITUDE),
                                )
                            )
                        )
                    },
                    onResolveTextValue = { value ->
                        value.toFloatOrNull()?.let { ampVal ->
                            onNodeChange(
                                node.copy(
                                    state = state.copy(
                                        amplitude = ampVal.coerceIn(MIN_AMPLITUDE, MAX_AMPLITUDE),
                                    )
                                )
                            )
                        }
                    },
                )
                AutomatableDial(
                    parameterId = "speed",
                    type = DialType.Continuous,
                    value = (state.speed - MIN_SPEED) / (MAX_SPEED - MIN_SPEED),
                    defaultValue = (DEFAULT_SPEED - MIN_SPEED) / (MAX_SPEED - MIN_SPEED),
                    title = "Speed",
                    text = "${(state.speed * 10).roundToInt() / 10f}",
                    onValueChange = { value ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    speed = (MIN_SPEED + value * (MAX_SPEED - MIN_SPEED)).coerceIn(MIN_SPEED, MAX_SPEED),
                                )
                            )
                        )
                    },
                    onResolveTextValue = { value ->
                        value.toFloatOrNull()?.let { speedVal ->
                            onNodeChange(
                                node.copy(
                                    state = state.copy(
                                        speed = speedVal.coerceIn(MIN_SPEED, MAX_SPEED),
                                    )
                                )
                            )
                        }
                    },
                )
            }
        }
    }
}
