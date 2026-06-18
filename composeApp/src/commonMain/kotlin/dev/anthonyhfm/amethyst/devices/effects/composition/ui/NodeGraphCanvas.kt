package dev.anthonyhfm.amethyst.devices.effects.composition.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.Event
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.SerializableNode
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.SerializableConnection
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.theme.background as themeBackground
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.accent

@Composable
fun NodeGraphCanvas(
    nodes: List<SerializableNode>,
    connections: List<SerializableConnection>,
    selectedNodeId: String?,
    onSelectNode: (String?) -> Unit,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableStateOf(1f) }

    val density = LocalDensity.current
    val cardWidthDp = 150.dp
    val cardHeightDp = 70.dp
    val cardWidthPx = with(density) { cardWidthDp.toPx() }
    val cardHeightPx = with(density) { cardHeightDp.toPx() }

    // State to track active connection drag
    var draggingPinFromNodeId by remember { mutableStateOf<String?>(null) }
    var draggingPinFromPinId by remember { mutableStateOf<String?>(null) }
    var dragTargetOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Theme[colors][themeBackground].copy(alpha = 0.95f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSelectNode(null) }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.3f, 3.0f)
                    panOffset += pan
                }
            }
    ) {
        // Render Background Grid
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 40f * zoomScale
            val startX = panOffset.x % gridSpacing
            val startY = panOffset.y % gridSpacing

            for (x in generateSequence(startX) { it + gridSpacing }.takeWhile { it < size.width }) {
                drawLine(
                    color = Color.Gray.copy(alpha = 0.15f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
            }
            for (y in generateSequence(startY) { it + gridSpacing }.takeWhile { it < size.height }) {
                drawLine(
                    color = Color.Gray.copy(alpha = 0.15f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }
        }

        // Canvas for Connection Lines (bezier curves)
        Canvas(modifier = Modifier.fillMaxSize()) {
            connections.forEach { connection ->
                val fromNode = nodes.find { it.id == connection.fromNodeId }
                val toNode = nodes.find { it.id == connection.toNodeId }

                if (fromNode != null && toNode != null) {
                    val start = Offset(
                        x = (fromNode.x + cardWidthPx) * zoomScale + panOffset.x,
                        y = (fromNode.y + cardHeightPx / 2f) * zoomScale + panOffset.y
                    )
                    val end = Offset(
                        x = toNode.x * zoomScale + panOffset.x,
                        y = (toNode.y + cardHeightPx / 2f) * zoomScale + panOffset.y
                    )

                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(
                            x1 = start.x + 80f * zoomScale, y1 = start.y,
                            x2 = end.x - 80f * zoomScale, y2 = end.y,
                            x3 = end.x, y3 = end.y
                        )
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF3B82F6), // Accent Blue
                        style = Stroke(width = 3f * zoomScale)
                    )
                }
            }

            // Draw active connection drag line
            val activeNodeId = draggingPinFromNodeId
            val activePinId = draggingPinFromPinId
            if (activeNodeId != null && activePinId != null) {
                val fromNode = nodes.find { it.id == activeNodeId }
                if (fromNode != null) {
                    val start = Offset(
                        x = (fromNode.x + cardWidthPx) * zoomScale + panOffset.x,
                        y = (fromNode.y + cardHeightPx / 2f) * zoomScale + panOffset.y
                    )
                    val end = dragTargetOffset

                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(
                            x1 = start.x + 80f * zoomScale, y1 = start.y,
                            x2 = end.x - 80f * zoomScale, y2 = end.y,
                            x3 = end.x, y3 = end.y
                        )
                    }

                    drawPath(
                        path = path,
                        color = Color.LightGray.copy(alpha = 0.8f),
                        style = Stroke(width = 2f * zoomScale)
                    )
                }
            }
        }

        // Render Node Cards
        nodes.forEach { node ->
            val isSelected = selectedNodeId == node.id

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (node.x * zoomScale + panOffset.x).toInt(),
                            y = (node.y * zoomScale + panOffset.y).toInt()
                        )
                    }
                    .size(width = cardWidthDp * zoomScale, height = cardHeightDp * zoomScale)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Theme[colors][themeBackground])
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Theme[colors][accent] else Theme[colors][border],
                        shape = RoundedCornerShape(8.dp)
                    )
                    .pointerInput(node.id) {
                        detectTapGestures(
                            onTap = { onSelectNode(node.id) }
                        )
                    }
                    .pointerInput(node.id) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newX = node.x + dragAmount.x / zoomScale
                                val newY = node.y + dragAmount.y / zoomScale
                                onEvent(Event.MoveNode(node.id, newX, newY))
                            }
                        )
                    }
            ) {
                // Pin - Input (Left side middle, only if it's a modifier node type)
                val isGenerator = node.type.lowercase() in setOf("waterdrop", "spiral", "scanner")
                if (!isGenerator) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (-6).dp * zoomScale)
                            .size(12.dp * zoomScale)
                            .clip(CircleShape)
                            .background(Color.Gray)
                            .border(1.dp, Theme[colors][border], CircleShape)
                    )
                }

                // Pin - Output (Right side middle)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 6.dp * zoomScale)
                        .size(12.dp * zoomScale)
                        .clip(CircleShape)
                        .background(Color(0xFF3B82F6))
                        .border(1.dp, Theme[colors][border], CircleShape)
                        .pointerInput(node.id) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    draggingPinFromNodeId = node.id
                                    draggingPinFromPinId = "output"
                                    dragTargetOffset = Offset(
                                        x = (node.x + cardWidthPx) * zoomScale + panOffset.x,
                                        y = (node.y + cardHeightPx / 2f) * zoomScale + panOffset.y
                                    )
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragTargetOffset += dragAmount
                                },
                                onDragEnd = {
                                    // Detect if connection was dropped over an input pin
                                    val releaseCanvasPos = (dragTargetOffset - panOffset) / zoomScale
                                    val targetNode = nodes.find { other ->
                                        if (other.id == node.id) return@find false
                                        val otherIsGenerator = other.type.lowercase() in setOf("waterdrop", "spiral", "scanner")
                                        if (otherIsGenerator) return@find false

                                        // Distance to target's input pin at (other.x, other.y + cardHeightPx / 2f)
                                        val targetPinPos = Offset(other.x, other.y + cardHeightPx / 2f / zoomScale)
                                        val distance = (releaseCanvasPos - targetPinPos).getDistance()
                                        distance < 30f / zoomScale
                                    }

                                    if (targetNode != null) {
                                        onEvent(
                                            Event.AddConnection(
                                                fromNodeId = node.id,
                                                fromPinId = "output",
                                                toNodeId = targetNode.id,
                                                toPinId = "input"
                                            )
                                        )
                                    }
                                    draggingPinFromNodeId = null
                                    draggingPinFromPinId = null
                                },
                                onDragCancel = {
                                    draggingPinFromNodeId = null
                                    draggingPinFromPinId = null
                                }
                            )
                        }
                )

                // Content of the card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp * zoomScale, vertical = 6.dp * zoomScale),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = node.type.replaceFirstChar { it.uppercase() },
                            color = Theme[colors][foreground],
                            fontSize = 12.sp * zoomScale,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { onEvent(Event.RemoveNode(node.id)) },
                            modifier = Modifier.size(16.dp * zoomScale)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Node",
                                tint = Theme[colors][mutedForeground],
                                modifier = Modifier.size(12.dp * zoomScale)
                            )
                        }
                    }

                    Text(
                        text = "ID: ${node.id.take(4)}...",
                        color = Theme[colors][mutedForeground],
                        fontSize = 10.sp * zoomScale
                    )
                }
            }
        }
    }
}
