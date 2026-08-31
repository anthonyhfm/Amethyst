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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tv
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.DialType
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

private const val MIN_INTENSITY = 0f
private const val MAX_INTENSITY = 1f
private const val DEFAULT_INTENSITY = 0.5f
private const val MIN_SLICES = 1
private const val MAX_SLICES = 16
private const val DEFAULT_SLICES = 4

@Serializable
data class GlitchNodeState(
    val intensity: Float = DEFAULT_INTENSITY,
    val slices: Int = DEFAULT_SLICES,
) : CompositionNodeState

object GlitchNode : TransformNode() {
    override val automationParameters = listOf(
        floatAutomationParameter<GlitchNodeState>("intensity", "Intensity", MIN_INTENSITY, MAX_INTENSITY, GlitchNodeState::intensity) { state, value -> state.copy(intensity = value) },
        intAutomationParameter<GlitchNodeState>("slices", "Slices", MIN_SLICES, MAX_SLICES, GlitchNodeState::slices) { state, value -> state.copy(slices = value) },
    )

    override val type = "glitch"
    override val label = "Glitch"
    override val icon = Lucide.Tv
    override val pickerCategory = CompositionNodePickerCategory.Effects
    override val bodyWidth: Dp = 200.dp
    override val bodyHeight: Dp = 128.dp

    override fun defaultState(): CompositionNodeState = GlitchNodeState()

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? GlitchNodeState ?: return inputFrames
        val intensity = state.intensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY)
        if (intensity <= 0f) return inputFrames

        val slices = state.slices.coerceIn(MIN_SLICES, MAX_SLICES)
        val timeStep = (context.progress.coerceIn(0f, 1f) * 16).toInt()
        val minX = context.bounds.first.x.toFloat()
        val minY = context.bounds.first.y.toFloat()
        val boundsWidth = context.bounds.second.width.coerceAtLeast(1).toFloat()
        val boundsHeight = context.bounds.second.height.coerceAtLeast(1).toFloat()

        return inputFrames.map { frame ->
            frame.copy(
                strokes = frame.strokes.map { stroke ->
                    stroke.copy(
                        points = stroke.points.map { point ->
                            val sliceY = (((point.y - minY) / boundsHeight) * slices).toInt().coerceIn(0, slices - 1)
                            val sliceX = (((point.x - minX) / boundsWidth) * slices).toInt().coerceIn(0, slices - 1)

                            // Horizontal tearing (shifts X based on Y slice)
                            val hashH = glitchHash(node.id, sliceY, timeStep, salt = 0)
                            val triggerH = (hashH and 0x00FFu).toFloat() / 255f
                            val offsetX = if (triggerH < intensity) {
                                val dir = if ((hashH and 0x0100u) != 0u) 1f else -1f
                                val shiftMag = ((hashH and 0x0F000u) shr 12).toFloat() / 15f
                                dir * (0.5f + shiftMag * 0.5f) * intensity * 4f
                            } else {
                                0f
                            }

                            // Vertical tearing (shifts Y based on X slice)
                            val hashV = glitchHash(node.id, sliceX, timeStep, salt = 1)
                            val triggerV = (hashV and 0x00FFu).toFloat() / 255f
                            val offsetY = if (triggerV < intensity) {
                                val dir = if ((hashV and 0x0100u) != 0u) 1f else -1f
                                val shiftMag = ((hashV and 0x0F000u) shr 12).toFloat() / 15f
                                dir * (0.5f + shiftMag * 0.5f) * intensity * 4f
                            } else {
                                0f
                            }

                            // Block micro-glitch (shifts cell X & Y)
                            val hashB = glitchHash(node.id, sliceX * 31 + sliceY, timeStep, salt = 2)
                            val triggerB = (hashB and 0x00FFu).toFloat() / 255f
                            val (blockX, blockY) = if (triggerB < intensity * 0.6f) {
                                val dirX = if ((hashB and 0x0100u) != 0u) 1f else -1f
                                val dirY = if ((hashB and 0x0200u) != 0u) 1f else -1f
                                val bMagX = ((hashB and 0x03000u) shr 12).toFloat() / 3f
                                val bMagY = ((hashB and 0x0C000u) shr 14).toFloat() / 3f
                                Pair(dirX * bMagX * intensity * 2.5f, dirY * bMagY * intensity * 2.5f)
                            } else {
                                Pair(0f, 0f)
                            }

                            Vec2(
                                x = point.x + offsetX + blockX,
                                y = point.y + offsetY + blockY,
                            )
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
        val state = node.state as? GlitchNodeState ?: return
        val intensity = state.intensity.coerceIn(MIN_INTENSITY, MAX_INTENSITY)
        val slices = state.slices.coerceIn(MIN_SLICES, MAX_SLICES)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AutomatableDial(
                parameterId = "intensity",
                type = DialType.Continuous,
                value = (intensity - MIN_INTENSITY) / (MAX_INTENSITY - MIN_INTENSITY),
                defaultValue = (DEFAULT_INTENSITY - MIN_INTENSITY) / (MAX_INTENSITY - MIN_INTENSITY),
                title = "Intensity",
                text = "${(intensity * 100).roundToInt()}%",
                onValueChange = { value ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                intensity = (MIN_INTENSITY + value * (MAX_INTENSITY - MIN_INTENSITY)).coerceIn(MIN_INTENSITY, MAX_INTENSITY),
                            )
                        )
                    )
                },
                onResolveTextValue = { value ->
                    value.removeSuffix("%").trim().toFloatOrNull()?.let { intensityVal ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    intensity = (intensityVal / 100f).coerceIn(MIN_INTENSITY, MAX_INTENSITY),
                                )
                            )
                        )
                    }
                },
            )

            AutomatableDial(
                parameterId = "slices",
                type = DialType.Steps(values = (MIN_SLICES..MAX_SLICES).toList()),
                value = slices,
                defaultValue = DEFAULT_SLICES,
                title = "Slices",
                text = slices.toString(),
                onValueChange = { value ->
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                slices = value.coerceIn(MIN_SLICES, MAX_SLICES),
                            )
                        )
                    )
                },
                onResolveTextValue = { value ->
                    value.trim().toIntOrNull()?.let { slicesVal ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    slices = slicesVal.coerceIn(MIN_SLICES, MAX_SLICES),
                                )
                            )
                        )
                    }
                },
            )
        }
    }
}

private fun glitchHash(nodeId: String, sliceIndex: Int, step: Int, salt: Int): UInt {
    var hash = nodeId.hashCode().toUInt()
    hash = hash xor (sliceIndex.toUInt() * 0x9E3779B9u)
    hash = hash xor (step.toUInt() * 0x85EBCA6Bu)
    hash = hash xor (salt.toUInt() * 0x27D4EB2Du)
    hash = (hash xor (hash shr 16)) * 0x7FEB352Du
    hash = (hash xor (hash shr 15)) * 0x846CA68Bu
    hash = hash xor (hash shr 16)
    return hash
}
