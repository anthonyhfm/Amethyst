package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

private const val MIN_REGENERATIONS = 1
private const val MAX_REGENERATIONS = 32
private const val DEFAULT_REGENERATIONS = 4
private const val DEFAULT_INTENSITY = 0.5f

@Serializable
data class NoiseNodeState(
    val regenerations: Int = DEFAULT_REGENERATIONS,
    val intensity: Float = DEFAULT_INTENSITY,
) : CompositionNodeState

object NoiseNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        intAutomationParameter<NoiseNodeState>("regenerations", "Regenerations", MIN_REGENERATIONS, MAX_REGENERATIONS, NoiseNodeState::regenerations) { state, value -> state.copy(regenerations = value) },
        floatAutomationParameter<NoiseNodeState>("intensity", "Intensity", 0f, 1f, NoiseNodeState::intensity) { state, value -> state.copy(intensity = value) },
    )

    override val type = "noise"
    override val label = "Noise"
    override val icon = Lucide.Waves
    override val hasInput = false
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Generators
    override val bodyWidth: Dp = 200.dp
    override val bodyHeight: Dp = 128.dp

    override fun defaultState(): CompositionNodeState = NoiseNodeState()

    override fun sourceFrames(
        node: CompositionNode,
        context: EvaluationContext,
    ): List<GeometryFrame> {
        val state = node.state as? NoiseNodeState ?: return emptyList()
        val regenerations = state.regenerations.coerceIn(MIN_REGENERATIONS, MAX_REGENERATIONS)
        val intensity = state.intensity.coerceIn(0f, 1f)
        if (intensity <= 0f) return emptyList()

        val minX = context.bounds.first.x
        val minY = context.bounds.first.y
        val maxX = minX + context.bounds.second.width - 1
        val maxY = minY + context.bounds.second.height - 1
        if (maxX < minX || maxY < minY) return emptyList()

        val segment = noiseSegment(
            progress = context.progress,
            regenerations = regenerations,
        )
        val strokes = buildList {
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    if (noiseValue(
                            nodeId = node.id,
                            x = x,
                            y = y,
                            segment = segment,
                        ) < intensity
                    ) {
                        add(
                            GeometryStroke(
                                points = listOf(
                                    Vec2(
                                        x = x.toFloat(),
                                        y = y.toFloat(),
                                    )
                                ),
                                color = Color.White,
                                thickness = 0f,
                                origin = context.outputOrigin,
                            ),
                        )
                    }
                }
            }
        }
        return listOf(
            GeometryFrame(
                timeMs = 0.0,
                strokes = strokes,
            )
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? NoiseNodeState ?: return
        val regenerations = state.regenerations.coerceIn(MIN_REGENERATIONS, MAX_REGENERATIONS)
        val intensity = state.intensity.coerceIn(0f, 1f)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dial(
                type = DialType.Steps(values = (MIN_REGENERATIONS..MAX_REGENERATIONS).toList()),
                value = regenerations,
                defaultValue = DEFAULT_REGENERATIONS,
                title = "Generate",
                text = regenerations.toString(),
                onValueChange = { value ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                regenerations = value.coerceIn(MIN_REGENERATIONS, MAX_REGENERATIONS),
                            )
                        )
                    )
                },
                onResolveTextValue = { value ->
                    value.trim().toIntOrNull()?.let { regenerations ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    regenerations = regenerations.coerceIn(MIN_REGENERATIONS, MAX_REGENERATIONS),
                                )
                            )
                        )
                    }
                },
            )

            Dial(
                type = DialType.Continuous,
                value = intensity,
                defaultValue = DEFAULT_INTENSITY,
                title = "Intensity",
                text = "${(intensity * 100).roundToInt()}%",
                onValueChange = { value ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                intensity = value.coerceIn(0f, 1f),
                            )
                        )
                    )
                },
                onResolveTextValue = { value ->
                    value.removeSuffix("%").trim().toFloatOrNull()?.let { intensity ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    intensity = (intensity / 100f).coerceIn(0f, 1f),
                                )
                            )
                        )
                    }
                },
            )
        }
    }
}

internal fun noiseSegment(progress: Float, regenerations: Int): Int {
    val count = regenerations.coerceIn(MIN_REGENERATIONS, MAX_REGENERATIONS)
    return min(count - 1, floor(progress.coerceIn(0f, 1f) * count).toInt())
}

private fun noiseValue(nodeId: String, x: Int, y: Int, segment: Int): Float {
    var hash = nodeId.hashCode().toUInt()
    hash = hash xor (x.toUInt() * 0x9E3779B9u)
    hash = hash xor (y.toUInt() * 0x85EBCA6Bu)
    hash = hash xor (segment.toUInt() * 0xC2B2AE35u)
    hash = (hash xor (hash shr 16)) * 0x7FEB352Du
    hash = (hash xor (hash shr 15)) * 0x846CA68Bu
    hash = hash xor (hash shr 16)
    return (hash and 0x00FFFFFFu).toFloat() / 0x01000000u.toFloat()
}
