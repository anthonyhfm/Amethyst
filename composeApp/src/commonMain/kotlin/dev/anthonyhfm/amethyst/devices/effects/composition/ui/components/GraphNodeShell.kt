package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.nodes.NodeRegistry
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.cardForeground
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainSurface
import dev.anthonyhfm.amethyst.ui.theme.chainSurfaceRaised
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.selectionForeground
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
const val DEFAULT_GRAPH_NODE_BODY_WIDTH = 188f
const val DEFAULT_GRAPH_NODE_BODY_HEIGHT = 96f
const val GRAPH_NODE_TITLE_HEIGHT = 28f
const val GRAPH_NODE_PORT_RADIUS = DataCableGeometry.PORT_RADIUS_DP
const val GRAPH_NODE_PORT_TOUCH_WIDTH = 36f

@Composable
fun GraphNodeShell(
    node: CompositionNode,
    selected: Boolean,
    connectedInput: Boolean = false,
    connectedOutput: Boolean = false,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onDragBy: (Float, Float) -> Unit,
    onInputPortClick: () -> Unit,
    onInputPortDragStart: () -> Unit,
    onInputPortDragBy: (Float, Float) -> Unit,
    onInputPortDragEnd: () -> Unit,
    onOutputPortDragStart: () -> Unit,
    onOutputPortDragBy: (Float, Float) -> Unit,
    onOutputPortDragEnd: () -> Unit,
    inputPortHighlighted: Boolean = false,
    outputPortHighlighted: Boolean = false,
    onNodeChange: (CompositionNode) -> Unit,
) {
    val titleBarColor = if (selected) Theme[colors][selectionSurface] else Theme[chainColorTokens][chainSurfaceRaised]
    val titleColor = if (selected) Theme[colors][selectionForeground] else Theme[colors][cardForeground]
    val definition = NodeRegistry.definitionFor(node)
    val bodyWidth = definition?.bodyWidth ?: DEFAULT_GRAPH_NODE_BODY_WIDTH.dp
    val bodyHeight = definition?.bodyHeight ?: DEFAULT_GRAPH_NODE_BODY_HEIGHT.dp

    val currentOnInputPortClick by rememberUpdatedState(onInputPortClick)
    val currentOnInputPortDragStart by rememberUpdatedState(onInputPortDragStart)
    val currentOnInputPortDragBy by rememberUpdatedState(onInputPortDragBy)
    val currentOnInputPortDragEnd by rememberUpdatedState(onInputPortDragEnd)
    val currentOnOutputPortDragStart by rememberUpdatedState(onOutputPortDragStart)
    val currentOnOutputPortDragBy by rememberUpdatedState(onOutputPortDragBy)
    val currentOnOutputPortDragEnd by rememberUpdatedState(onOutputPortDragEnd)
    val currentOnDragBy by rememberUpdatedState(onDragBy)

    Column(
        modifier = modifier
            .width(bodyWidth)
            .clip(DefaultShape)
            .background(Theme[chainColorTokens][chainSurface])
            .border(1.dp, titleBarColor, DefaultShape)
            .clickable(onClick = onSelect),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(GRAPH_NODE_TITLE_HEIGHT.dp)
                .background(titleBarColor)
                .pointerInput(node.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        currentOnDragBy(dragAmount.x, dragAmount.y)
                    }
                },
        ) {
            if (definition?.hasInput == true) {
                DataPort(
                    input = true,
                    connected = connectedInput,
                    highlighted = inputPortHighlighted,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(onClick = { currentOnInputPortClick() })
                        .pointerInput(node.id) {
                            detectDragGestures(
                                onDragStart = { currentOnInputPortDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOnInputPortDragBy(dragAmount.x, dragAmount.y)
                                },
                                onDragEnd = { currentOnInputPortDragEnd() },
                                onDragCancel = { currentOnInputPortDragEnd() },
                            )
                        },
                )
            }

            Text(
                text = node.label,
                style = Theme[typography][small],
                color = titleColor,
                modifier = Modifier.align(Alignment.Center),
            )

            if (definition?.hasOutput == true) {
                DataPort(
                    input = false,
                    connected = connectedOutput,
                    highlighted = outputPortHighlighted,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .pointerInput(node.id) {
                            detectDragGestures(
                                onDragStart = { currentOnOutputPortDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOnOutputPortDragBy(dragAmount.x, dragAmount.y)
                                },
                                onDragEnd = { currentOnOutputPortDragEnd() },
                                onDragCancel = { currentOnOutputPortDragEnd() },
                            )
                        },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bodyHeight),
        ) {
            if (definition != null) {
                definition.NodeBody(node, onNodeChange)
            } else {
                Text(
                    text = "Unknown node",
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * An abstract graph port. Inputs are hollow rings and outputs are filled points, so connection
 * direction remains readable without relying on colour or a hardware metaphor.
 */
@Composable
private fun DataPort(
    input: Boolean,
    connected: Boolean,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .width(GRAPH_NODE_PORT_TOUCH_WIDTH.dp)
            .height(GRAPH_NODE_TITLE_HEIGHT.dp),
    ) {
        val u = 1.dp.toPx()
        val radius = DataCableGeometry.PORT_RADIUS_DP * u
        val inset = DataCableGeometry.PORT_CENTER_INSET_DP * u
        val center = if (input) {
            Offset(inset, size.height / 2f)
        } else {
            Offset(size.width - inset, size.height / 2f)
        }
        val portColor = when {
            highlighted -> DataCableGeometry.DRAG_COLOR
            connected -> DataCableGeometry.DATA_COLOR
            else -> DataCableGeometry.IDLE_PORT_COLOR
        }

        if (highlighted) {
            drawCircle(
                color = DataCableGeometry.DRAG_COLOR.copy(alpha = 0.18f),
                radius = radius + 4f * u,
                center = center,
            )
        }

        if (input) {
            drawCircle(
                color = portColor,
                radius = radius,
                center = center,
                style = Stroke(width = 1.5f * u),
            )
        } else {
            drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = radius + u, center = center)
            drawCircle(color = portColor, radius = radius, center = center)
        }
    }
}
