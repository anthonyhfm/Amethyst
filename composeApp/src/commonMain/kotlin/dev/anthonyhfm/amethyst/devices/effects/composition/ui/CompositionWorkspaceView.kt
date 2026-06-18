package dev.anthonyhfm.amethyst.devices.effects.composition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.rememberDialogState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.CompositionChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.Event
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.SerializableNode
import dev.anthonyhfm.amethyst.ui.components.primitives.*
import dev.anthonyhfm.amethyst.ui.theme.background as themeBackground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.border as ThemeBorder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CompositionWorkspaceView(
    stateFlow: StateFlow<CompositionChainDeviceState>,
    onEvent: (Event) -> Unit,
    paddingValues: PaddingValues
) {
    val state by stateFlow.collectAsState()
    val resizableState = rememberResizablePanelGroupState(0.65f, 0.35f)

    var selectedNodeId by remember { mutableStateOf<String?>(null) }

    // Dialog state for properties drawer
    val sheetState = rememberDialogState()
    LaunchedEffect(selectedNodeId) {
        sheetState.visible = selectedNodeId != null
    }
    LaunchedEffect(sheetState.visible) {
        if (!sheetState.visible) {
            selectedNodeId = null
        }
    }

    // Playback state for preview grid
    var currentFrameIndex by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                delay(33) // 30 FPS
                currentFrameIndex = (currentFrameIndex + 1) % 100
            }
        }
    }

    Box(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
    ) {
        ResizablePanelGroup(
            state = resizableState,
            orientation = ResizableOrientation.Horizontal,
            modifier = Modifier.fillMaxSize()
        ) {
            // Panel 1: Node Graph Canvas + Floating Actions to Add Nodes
            ResizablePanel {
                Box(modifier = Modifier.fillMaxSize()) {
                    NodeGraphCanvas(
                        nodes = state.nodes,
                        connections = state.connections,
                        selectedNodeId = selectedNodeId,
                        onSelectNode = { selectedNodeId = it },
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Toolbar to add nodes
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(Theme[colors][themeBackground].copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .border(1.dp, Theme[colors][ThemeBorder], RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add Node:",
                            color = Theme[colors][mutedForeground],
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        val nodeTypes = listOf(
                            "waterdrop", "spiral", "scanner", "rotate", "scale", "translate"
                        )
                        nodeTypes.forEach { type ->
                            Button(
                                onClick = {
                                    // Add node at a default center position
                                    onEvent(Event.AddNode(type, 100f, 100f))
                                },
                                size = ButtonSize.Small,
                                variant = ButtonVariant.Default
                            ) {
                                Text(type.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                }
            }

            // Split handle
            ResizableHandle(withHandle = true)

            // Panel 2: Preview Panel (LED animation)
            ResizablePanel {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Theme[colors][themeBackground].copy(alpha = 0.98f))
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Raster Preview",
                                color = Theme[colors][foreground],
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // 10x10 LED Grid
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth()
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                                    .border(1.dp, Theme[colors][ThemeBorder], RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                val currentLeds = state.preRenderedFrames[currentFrameIndex] ?: emptyList()
                                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cellWidth = size.width / 10f
                                    val cellHeight = size.height / 10f
                                    val radius = minOf(cellWidth, cellHeight) * 0.4f

                                    for (gx in 0 until 10) {
                                        for (gy in 0 until 10) {
                                            val led = currentLeds.find { it.x == gx && it.y == gy }
                                            val ledColor = if (led != null) {
                                                androidx.compose.ui.graphics.Color(
                                                    led.color.first, led.color.second, led.color.third
                                                )
                                            } else {
                                                androidx.compose.ui.graphics.Color(0xFF222222) // Off grid
                                            }

                                            drawCircle(
                                                color = ledColor,
                                                radius = radius,
                                                center = Offset(
                                                    x = (gx + 0.5f) * cellWidth,
                                                    y = (gy + 0.5f) * cellHeight
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Playback and Timeline Controls
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { isPlaying = !isPlaying },
                                    size = ButtonSize.Small,
                                    variant = if (isPlaying) ButtonVariant.Destructive else ButtonVariant.Default
                                ) {
                                    Text(if (isPlaying) "Pause" else "Play")
                                }
                                Text(
                                    text = "Frame: $currentFrameIndex / 99",
                                    color = Theme[colors][foreground],
                                    fontSize = 12.sp
                                )
                            }

                            Slider(
                                value = currentFrameIndex.toFloat(),
                                onValueChange = { currentFrameIndex = it.toInt() },
                                valueRange = 0f..99f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Properties Inspector Details Drawer (Sheet sliding from the right)
        val selectedNode = state.nodes.find { it.id == selectedNodeId }
        if (selectedNode != null) {
            Sheet(
                state = sheetState,
                onDismiss = { selectedNodeId = null }
            ) {
                SheetContent(
                    side = SheetSide.Right,
                    modifier = Modifier.width(360.dp)
                ) {
                    SheetHeader {
                        SheetTitle(text = "Node Properties")
                        SheetDescription(text = "Configure parameters for the selected node.")
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Type: ${selectedNode.type.replaceFirstChar { it.uppercase() }}",
                            color = Theme[colors][foreground],
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Node ID: ${selectedNode.id}",
                            color = Theme[colors][mutedForeground],
                            fontSize = 11.sp
                        )

                        Separator()

                        val keys = getPropertyKeys(selectedNode.type)
                        keys.forEach { key ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = key,
                                    color = Theme[colors][foreground],
                                    fontSize = 12.sp
                                )
                                val currentValue = getPropertyValue(selectedNode, key)
                                Input(
                                    value = currentValue,
                                    onValueChange = { newValue ->
                                        onEvent(
                                            Event.UpdateNodeProperty(
                                                nodeId = selectedNode.id,
                                                key = key,
                                                value = newValue
                                            )
                                        )
                                    },
                                    placeholder = "Enter $key",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    SheetFooter {
                        Button(
                            onClick = { selectedNodeId = null },
                            variant = ButtonVariant.Default
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

// Helpers for node properties
private fun getPropertyKeys(nodeType: String): List<String> {
    return when (nodeType.lowercase()) {
        "waterdrop" -> listOf("centerX", "centerY", "speed", "frequency", "amplitude", "thickness", "r", "g", "b", "a")
        "spiral" -> listOf("centerX", "centerY", "turns", "tightness", "speed", "amplitude", "thickness", "r", "g", "b", "a")
        "scanner" -> listOf("direction", "speed", "width", "thickness", "r", "g", "b", "a")
        "rotate" -> listOf("angle", "speed", "px", "py")
        "scale" -> listOf("sx", "sy", "speedX", "speedY", "px", "py")
        "translate" -> listOf("tx", "ty", "speedX", "speedY")
        else -> emptyList()
    }
}

private fun getPropertyValue(node: SerializableNode, key: String): String {
    return node.properties[key] ?: when (node.type.lowercase()) {
        "waterdrop" -> when (key) {
            "centerX" -> "0.5"
            "centerY" -> "0.5"
            "speed" -> "0.02"
            "frequency" -> "10.0"
            "amplitude" -> "0.05"
            "thickness" -> "0.05"
            "r" -> "1.0"
            "g" -> "1.0"
            "b" -> "1.0"
            "a" -> "1.0"
            else -> ""
        }
        "spiral" -> when (key) {
            "centerX" -> "0.5"
            "centerY" -> "0.5"
            "turns" -> "3.0"
            "tightness" -> "1.0"
            "speed" -> "0.05"
            "amplitude" -> "0.4"
            "thickness" -> "0.05"
            "r" -> "1.0"
            "g" -> "1.0"
            "b" -> "1.0"
            "a" -> "1.0"
            else -> ""
        }
        "scanner" -> when (key) {
            "direction" -> "0.0"
            "speed" -> "0.02"
            "width" -> "1.0"
            "thickness" -> "0.05"
            "r" -> "1.0"
            "g" -> "1.0"
            "b" -> "1.0"
            "a" -> "1.0"
            else -> ""
        }
        "rotate" -> when (key) {
            "angle" -> "0.0"
            "speed" -> "0.05"
            "px" -> "0.5"
            "py" -> "0.5"
            else -> ""
        }
        "scale" -> when (key) {
            "sx" -> "1.0"
            "sy" -> "1.0"
            "speedX" -> "0.0"
            "speedY" -> "0.0"
            "px" -> "0.5"
            "py" -> "0.5"
            else -> ""
        }
        "translate" -> when (key) {
            "tx" -> "0.0"
            "ty" -> "0.0"
            "speedX" -> "0.0"
            "speedY" -> "0.0"
            else -> ""
        }
        else -> ""
    }
}
