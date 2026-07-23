package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PenLine
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableWorkspaceLineSelector
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
data class LinePoint(
    val x: Float,
    val y: Float,
)

@Serializable
data class LineNodeState(
    val points: List<LinePoint> = listOf(LinePoint(0.25f, 0.5f), LinePoint(0.75f, 0.5f)),
    val startX: Float = 0.25f,
    val startY: Float = 0.5f,
    val endX: Float = 0.75f,
    val endY: Float = 0.5f,
    val thickness: Float = 1f,
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 1f,
) : CompositionNodeState {
    fun resolvedPoints(): List<LinePoint> {
        if (points.isNotEmpty()) return points
        return listOf(LinePoint(startX, startY), LinePoint(endX, endY))
    }
}

object LineNode : CompositionNodeDefinition {
    override val automationParameters = listOf(
        floatAutomationParameter<LineNodeState>("start-x", "Start X", 0f, 1f, LineNodeState::startX) { state, value -> state.copy(startX = value) },
        floatAutomationParameter<LineNodeState>("start-y", "Start Y", 0f, 1f, LineNodeState::startY) { state, value -> state.copy(startY = value) },
        floatAutomationParameter<LineNodeState>("end-x", "End X", 0f, 1f, LineNodeState::endX) { state, value -> state.copy(endX = value) },
        floatAutomationParameter<LineNodeState>("end-y", "End Y", 0f, 1f, LineNodeState::endY) { state, value -> state.copy(endY = value) },
        floatAutomationParameter<LineNodeState>("thickness", "Thickness", 0f, 4f, LineNodeState::thickness) { state, value -> state.copy(thickness = value) },
    )

    override val type = "line"
    override val label = "Line"
    override val icon = Lucide.PenLine

    override val hasInput = false
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Generators
    override val bodyWidth: Dp = 216.dp
    override val bodyHeight: Dp = 128.dp

    override fun defaultState() = LineNodeState()

    override fun sourceFrames(
        node: CompositionNode,
        context: EvaluationContext,
    ): List<GeometryFrame> {
        val state = node.state as? LineNodeState ?: return emptyList()
        val bounds = context.bounds

        fun x(value: Float) =
            bounds.first.x + value.coerceIn(0f, 1f) * (bounds.second.width - 1).coerceAtLeast(0)

        fun y(value: Float) =
            bounds.first.y + value.coerceIn(0f, 1f) * (bounds.second.height - 1).coerceAtLeast(0)

        val color = Color(
            red = state.red.coerceIn(0f, 1f),
            green = state.green.coerceIn(0f, 1f),
            blue = state.blue.coerceIn(0f, 1f),
        )

        val resolvedPts = state.resolvedPoints()
        val strokePoints = resolvedPts.map { p ->
            Vec2(
                x = x(p.x),
                y = y(p.y),
            )
        }

        return listOf(
            GeometryFrame(
                timeMs = 0.0,
                strokes = listOf(
                    GeometryStroke(
                        points = strokePoints,
                        color = color,
                        thickness = state.thickness.coerceAtLeast(0f),
                        origin = context.outputOrigin,
                    )
                ),
            )
        )
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? LineNodeState ?: return
        val bounds = WorkspaceRepository.bounds.validOrFallbackBounds()
        var selectedIndex by remember { mutableStateOf(0) }

        val resolvedPoints = state.resolvedPoints()
        val safeSelectedIndex = selectedIndex.coerceIn(0, (resolvedPoints.size - 1).coerceAtLeast(0))

        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            AutomatableWorkspaceLineSelector(
                points = resolvedPoints,
                selectedIndex = safeSelectedIndex,
                bounds = bounds,
                onPointsChange = { newPoints, newSelIdx ->
                    selectedIndex = newSelIdx
                    val first = newPoints.firstOrNull() ?: LinePoint(0.25f, 0.5f)
                    val last = newPoints.lastOrNull() ?: LinePoint(0.75f, 0.5f)
                    onNodeChange(
                        node.copy(
                            state = state.copy(
                                points = newPoints,
                                startX = first.x,
                                startY = first.y,
                                endX = last.x,
                                endY = last.y,
                            )
                        )
                    )
                },
                onSelectPoint = { index ->
                    selectedIndex = index
                },
                onDeletePoint = if (resolvedPoints.size > 2) {
                    {
                        val newPoints = resolvedPoints.toMutableList()
                        val idxToRemove = safeSelectedIndex
                        newPoints.removeAt(idxToRemove)
                        val newSel = (idxToRemove - 1).coerceIn(0, newPoints.size - 1)
                        selectedIndex = newSel
                        val first = newPoints.firstOrNull() ?: LinePoint(0.25f, 0.5f)
                        val last = newPoints.lastOrNull() ?: LinePoint(0.75f, 0.5f)
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    points = newPoints,
                                    startX = first.x,
                                    startY = first.y,
                                    endX = last.x,
                                    endY = last.y,
                                )
                            )
                        )
                    }
                } else null,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .padding(start = 12.dp)
                    .fillMaxHeight()
                    .aspectRatio(1f),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                AutomatableDial(
                    parameterId = "thickness",
                    type = DialType.Continuous,
                    value = (state.thickness / 4f).coerceIn(0f, 1f),
                    defaultValue = 0.25f,
                    title = "Thickness",
                    text = "${(state.thickness * 10).roundToInt() / 10f}",
                    onValueChange = { value ->
                        onNodeChange(
                            node.copy(
                                state = state.copy(
                                    thickness = (value * 4f).coerceIn(0f, 4f),
                                )
                            )
                        )
                    },
                    onResolveTextValue = { value ->
                        value.toFloatOrNull()?.let { thickness ->
                            onNodeChange(
                                node.copy(
                                    state = state.copy(
                                        thickness = thickness.coerceIn(0f, 4f),
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
