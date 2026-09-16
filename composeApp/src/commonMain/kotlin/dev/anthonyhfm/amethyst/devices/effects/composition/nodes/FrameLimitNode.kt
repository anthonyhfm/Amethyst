package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Timer
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.DialType
import kotlinx.serialization.Serializable
import kotlin.math.floor

@Serializable
data class FrameLimitNodeState(
    val frames: Int = 12,
) : CompositionNodeState

object FrameLimitNode : TransformNode() {
    override val automationParameters = listOf(
        intAutomationParameter<FrameLimitNodeState>(
            id = "frames",
            label = "FPS",
            minimum = MIN_FPS,
            maximum = MAX_FPS,
            get = FrameLimitNodeState::frames,
        ) { state, value -> state.copy(frames = value) },
    )

    override val type = "frame-limit"
    override val label = "Frame Limit"
    override val icon = Lucide.Timer
    override val pickerCategory = CompositionNodePickerCategory.Time
    override val bodyHeight: Dp = 128.dp
    override val bodyWidth: Dp = 128.dp

    override fun defaultState() = FrameLimitNodeState()

    override fun inputContext(
        node: CompositionNode,
        context: EvaluationContext,
    ): EvaluationContext {
        val fps = (node.state as? FrameLimitNodeState)?.frames?.coerceIn(MIN_FPS, MAX_FPS) ?: DEFAULT_FPS
        val durationMs = context.durationMs.coerceAtLeast(1.0)
        val elapsedMs = context.progress.coerceIn(0f, 1f) * durationMs
        val frameIntervalMs = 1_000.0 / fps
        return context.copy(
            progress = if (context.progress >= 1f) {
                1f
            } else {
                (floor(elapsedMs / frameIntervalMs) * frameIntervalMs / durationMs)
                    .toFloat()
                    .coerceIn(0f, 1f)
            }
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? FrameLimitNodeState ?: return

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AutomatableDial(
                parameterId = "frames",
                type = DialType.Steps(values = FPS_VALUES),
                value = state.frames,
                defaultValue = 0,
                title = "FPS",
                text = state.frames.toString(),
                onValueChange = {
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                frames = it,
                            )
                        )
                    )
                },
            )
        }
    }

    private const val MIN_FPS = 1
    private const val MAX_FPS = 120
    private const val DEFAULT_FPS = 12
    private val FPS_VALUES = (MIN_FPS..MAX_FPS).toList()
}
