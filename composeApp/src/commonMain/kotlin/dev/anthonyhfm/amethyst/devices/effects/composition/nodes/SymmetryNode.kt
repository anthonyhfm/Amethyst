package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.composeunstyled.Text
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryStroke
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.SelectItem
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.abs

object SymmetryNode : CompositionNodeDefinition {
    override val type = "symmetry"
    override val label = "Symmetry"
    override val hasInput = true
    override val hasOutput = true
    override val bodyMinHeight = 180.dp

    override fun defaultState(): CompositionNodeState = SymmetryNodeState()
    override fun acceptsState(state: CompositionNodeState): Boolean = state is SymmetryNodeState

    override fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> {
        val state = node.state as? SymmetryNodeState ?: return inputFrames
        return inputFrames.map { frame ->
            frame.copy(strokes = frame.strokes.flatMap { applySymmetry(it, state, context) })
        }
    }

    private fun applySymmetry(
        stroke: GeometryStroke,
        state: SymmetryNodeState,
        context: EvaluationContext,
    ): List<GeometryStroke> {
        val center = Vec2(
            x = context.bounds.first.x + (context.bounds.second.width - 1) / 2f,
            y = context.bounds.first.y + (context.bounds.second.height - 1) / 2f,
        )

        return when (state.mode) {
            "mirror-half" -> {
                val keepMin = if (state.axis == "horizontal") {
                    state.sourceAnchor == "bl" || state.sourceAnchor == "tl"
                } else {
                    state.sourceAnchor == "tl" || state.sourceAnchor == "tr"
                }

                val sourcePoints = stroke.points.filter { p ->
                    if (state.axis == "horizontal") {
                        if (keepMin) p.x <= center.x else p.x >= center.x
                    } else {
                        if (keepMin) p.y <= center.y else p.y >= center.y
                    }
                }

                if (sourcePoints.isEmpty()) return emptyList()

                val original = stroke.copy(points = sourcePoints)
                val mirrored = stroke.copy(
                    points = sourcePoints.map { p ->
                        if (state.axis == "horizontal") {
                            Vec2(2 * center.x - p.x, p.y)
                        } else {
                            Vec2(p.x, 2 * center.y - p.y)
                        }
                    }
                )
                listOf(original, mirrored)
            }
            "quad-mirror" -> {
                val sourcePoints = stroke.points.filter { p ->
                    isInQuadrant(p, state.sourceAnchor, center)
                }

                if (sourcePoints.isEmpty()) return emptyList()

                val quadrants = listOf("tl", "tr", "bl", "br")
                quadrants.map { quad ->
                    stroke.copy(
                        points = sourcePoints.map { p ->
                            val dx = abs(p.x - center.x)
                            val dy = abs(p.y - center.y)
                            when (quad) {
                                "tl" -> Vec2(center.x - dx, center.y - dy)
                                "tr" -> Vec2(center.x + dx, center.y - dy)
                                "bl" -> Vec2(center.x - dx, center.y + dy)
                                "br" -> Vec2(center.x + dx, center.y + dy)
                                else -> p
                            }
                        }
                    )
                }
            }
            "quad-pinwheel" -> {
                val sourcePoints = stroke.points.filter { p ->
                    isInQuadrant(p, state.sourceAnchor, center)
                }

                if (sourcePoints.isEmpty()) return emptyList()

                val quadrants = listOf("bl", "br", "tr", "tl")
                val sourceIndex = quadrants.indexOf(state.sourceAnchor).coerceAtLeast(0)

                quadrants.mapIndexed { targetIndex, _ ->
                    val delta = (targetIndex - sourceIndex + 4) % 4
                    stroke.copy(
                        points = sourcePoints.map { p ->
                            val dx = p.x - center.x
                            val dy = p.y - center.y
                            when (delta) {
                                0 -> p
                                1 -> Vec2(center.x + dy, center.y - dx) // 90 CCW
                                2 -> Vec2(center.x - dx, center.y - dy) // 180 CCW
                                3 -> Vec2(center.x - dy, center.y + dx) // 270 CCW
                                else -> p
                            }
                        }
                    )
                }
            }
            else -> listOf(stroke)
        }
    }

    private fun isInQuadrant(p: Vec2, anchor: String, center: Vec2): Boolean {
        return when (anchor) {
            "tl" -> p.x <= center.x && p.y <= center.y
            "tr" -> p.x >= center.x && p.y <= center.y
            "bl" -> p.x <= center.x && p.y >= center.y
            "br" -> p.x >= center.x && p.y >= center.y
            else -> false
        }
    }

    @Composable
    override fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    ) {
        val state = node.state as? SymmetryNodeState ?: return
    }
}
