package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Lucide
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.DialType
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

private const val MIN_AMOUNT = 0f
private const val MAX_AMOUNT = 3f
private const val DEFAULT_AMOUNT = 0.5f
private const val MIN_SPEED = 1
private const val MAX_SPEED = 16
private const val DEFAULT_SPEED = 4

@Serializable
data class JitterNodeState(
    val amount: Float = DEFAULT_AMOUNT,
    val speed: Int = DEFAULT_SPEED,
) : CompositionNodeState

object JitterNode : TransformNode() {
    override val automationParameters = listOf(
        floatAutomationParameter<JitterNodeState>("amount", "Amount", MIN_AMOUNT, MAX_AMOUNT, JitterNodeState::amount) { state, value -> state.copy(amount = value) },
        intAutomationParameter<JitterNodeState>("speed", "Speed", MIN_SPEED, MAX_SPEED, JitterNodeState::speed) { state, value -> state.copy(speed = value) },
    )

    override val type = "jitter"
    override val label = "Jitter"
    override val icon = Lucide.Activity
    override val pickerCategory = CompositionNodePickerCategory.Effects
    override val bodyWidth: Dp = 200.dp
    override val bodyHeight: Dp = 128.dp

    override fun defaultState(): CompositionNodeState = JitterNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? JitterNodeState ?: return inputFrames
        val amount = state.amount.coerceIn(MIN_AMOUNT, MAX_AMOUNT)
        if (amount <= 0f) return inputFrames

        val speed = state.speed.coerceIn(MIN_SPEED, MAX_SPEED)
        val timeStep = (context.progress.coerceIn(0f, 1f) * speed).toInt()

        return inputFrames.map { frame ->
            frame.copy(
                strokes = frame.strokes.map { stroke ->
                    stroke.copy(
                        points = stroke.points.mapIndexed { index, point ->
                            val hX = jitterHash(node.id, point.x.roundToInt(), point.y.roundToInt(), timeStep, index * 2)
                            val hY = jitterHash(node.id, point.x.roundToInt(), point.y.roundToInt(), timeStep, index * 2 + 1)
                            val dx = (hX * 2f - 1f) * amount
                            val dy = (hY * 2f - 1f) * amount
                            Vec2(x = point.x + dx, y = point.y + dy)
                        }
                    )
                }
            )
        }
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? JitterNodeState ?: return
        val amount = state.amount.coerceIn(MIN_AMOUNT, MAX_AMOUNT)
        val speed = state.speed.coerceIn(MIN_SPEED, MAX_SPEED)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutomatableDial(
                parameterId = "amount",
                type = DialType.Continuous,
                value = (amount - MIN_AMOUNT) / (MAX_AMOUNT - MIN_AMOUNT),
                defaultValue = (DEFAULT_AMOUNT - MIN_AMOUNT) / (MAX_AMOUNT - MIN_AMOUNT),
                title = "Amount",
                text = "${(amount * 10).roundToInt() / 10f}",
                onValueChange = { value ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                amount = (MIN_AMOUNT + value * (MAX_AMOUNT - MIN_AMOUNT)).coerceIn(MIN_AMOUNT, MAX_AMOUNT),
                            )
                        )
                    )
                },
                onResolveTextValue = { value ->
                    value.toFloatOrNull()?.let { amountVal ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    amount = amountVal.coerceIn(MIN_AMOUNT, MAX_AMOUNT),
                                )
                            )
                        )
                    }
                },
            )

            AutomatableDial(
                parameterId = "speed",
                type = DialType.Steps(values = (MIN_SPEED..MAX_SPEED).toList()),
                value = speed,
                defaultValue = DEFAULT_SPEED,
                title = "Speed",
                text = speed.toString(),
                onValueChange = { value ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                speed = value.coerceIn(MIN_SPEED, MAX_SPEED),
                            )
                        )
                    )
                },
                onResolveTextValue = { value ->
                    value.trim().toIntOrNull()?.let { speedVal ->
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

private fun jitterHash(nodeId: String, x: Int, y: Int, step: Int, salt: Int): Float {
    var hash = nodeId.hashCode().toUInt()
    hash = hash xor (x.toUInt() * 0x9E3779B9u)
    hash = hash xor (y.toUInt() * 0x85EBCA6Bu)
    hash = hash xor (step.toUInt() * 0xC2B2AE35u)
    hash = hash xor (salt.toUInt() * 0x27D4EB2Du)
    hash = (hash xor (hash shr 16)) * 0x7FEB352Du
    hash = (hash xor (hash shr 15)) * 0x846CA68Bu
    hash = hash xor (hash shr 16)
    return (hash and 0x00FFFFFFu).toFloat() / 0x01000000u.toFloat()
}
