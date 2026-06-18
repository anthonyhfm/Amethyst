package dev.anthonyhfm.amethyst.devices.effects.composition

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.CompositionChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.Event
import dev.anthonyhfm.amethyst.devices.effects.composition.engine.CompositionGraphEvaluator
import dev.anthonyhfm.amethyst.devices.effects.composition.engine.Rasterizer
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID

class CompositionChainDevice : LEDChainDevice<CompositionChainDeviceState>(), Chokeable {
    override val state = MutableStateFlow(CompositionChainDeviceState())
    override val helpRef = "Composition"

    private val customMode = CompositionWorkspaceMode()

    init {
        customMode.state = state.asStateFlow()
        customMode.parentDevice = this
        customMode.onEvent = { onEvent(it) }
    }

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        ChainDeviceShell(
            title = "Composition",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(120.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Button(
                onClick = {
                    WorkspaceRepository.switchMode(mode = customMode)
                },
                variant = ButtonVariant.Default,
                size = ButtonSize.IconLarge,
            ) {
                Icon(
                    imageVector = Icons.Default.Draw,
                    contentDescription = "Edit Graph",
                    modifier = Modifier.size(36.dp),
                    tint = Theme[colors][primaryForeground],
                )
            }
        }
    }

    private fun preRender(
        nodes: List<CompositionDeviceContract.SerializableNode>,
        connections: List<CompositionDeviceContract.SerializableConnection>
    ): Map<Int, List<CompositionDeviceContract.SerializableLED>> {
        val preRendered = mutableMapOf<Int, List<CompositionDeviceContract.SerializableLED>>()

        for (frame in 0 until 100) {
            val evaluator = CompositionGraphEvaluator(
                nodes = nodes,
                connections = connections,
                frame = frame,
                totalFrames = 100
            )
            val strokes = evaluator.evaluateAll()
            val leds = Rasterizer.rasterize(strokes)
            preRendered[frame] = leds.map { led ->
                CompositionDeviceContract.SerializableLED(
                    x = led.x,
                    y = led.y,
                    color = Triple(led.color.red, led.color.green, led.color.blue)
                )
            }
        }
        return preRendered
    }

    fun onEvent(event: Event) {
        state.update { currentState ->
            val updatedState = when (event) {
                is Event.AddNode -> {
                    val newNode = CompositionDeviceContract.SerializableNode(
                        id = UUID.randomUUID(),
                        type = event.type,
                        x = event.x,
                        y = event.y,
                        properties = emptyMap()
                    )
                    currentState.copy(nodes = currentState.nodes + newNode)
                }
                is Event.RemoveNode -> {
                    currentState.copy(
                        nodes = currentState.nodes.filterNot { it.id == event.nodeId },
                        connections = currentState.connections.filterNot { it.fromNodeId == event.nodeId || it.toNodeId == event.nodeId }
                    )
                }
                is Event.MoveNode -> {
                    currentState.copy(
                        nodes = currentState.nodes.map {
                            if (it.id == event.nodeId) it.copy(x = event.x, y = event.y) else it
                        }
                    )
                }
                is Event.UpdateNodeProperty -> {
                    currentState.copy(
                        nodes = currentState.nodes.map {
                            if (it.id == event.nodeId) {
                                it.copy(properties = it.properties + (event.key to event.value))
                            } else it
                        }
                    )
                }
                is Event.AddConnection -> {
                    val exists = currentState.connections.any {
                        it.toNodeId == event.toNodeId && it.toPinId == event.toPinId
                    }
                    if (exists) {
                        currentState
                    } else {
                        val newConn = CompositionDeviceContract.SerializableConnection(
                            id = UUID.randomUUID(),
                            fromNodeId = event.fromNodeId,
                            fromPinId = event.fromPinId,
                            toNodeId = event.toNodeId,
                            toPinId = event.toPinId
                        )
                        currentState.copy(connections = currentState.connections + newConn)
                    }
                }
                is Event.RemoveConnection -> {
                    currentState.copy(
                        connections = currentState.connections.filterNot { it.id == event.connectionId }
                    )
                }
                is Event.UpdateSelectedColor -> {
                    currentState.copy(selectedColor = event.color)
                }
            }
            val preRendered = preRender(updatedState.nodes, updatedState.connections)
            updatedState.copy(preRenderedFrames = preRendered)
        }
    }

    private val activeRunTokens = mutableMapOf<String, Long>()
    private var nextRunToken = 0L
    private val activeLedsPerRun = mutableMapOf<String, Set<Pair<Int, Int>>>()

    private fun startRun(ownerKey: String): Long {
        val runToken = ++nextRunToken
        activeRunTokens[ownerKey] = runToken
        return runToken
    }

    private fun isRunActive(ownerKey: String, runToken: Long): Boolean {
        return activeRunTokens[ownerKey] == runToken
    }

    private fun cancelRun(ownerKey: String) {
        activeRunTokens.remove(ownerKey)
        Heaven.cancelJobsForOwner(this, ownerKey)
        val activeCoords = activeLedsPerRun.remove(ownerKey) ?: emptySet()
        if (activeCoords.isNotEmpty()) {
            val clearSignals = activeCoords.map { (cx, cy) ->
                Signal.LED(origin = this, x = cx, y = cy, color = Color.Black)
            }
            signalExit?.invoke(clearSignals)
        }
    }

    private fun scheduleFrameStep(
        triggerSignal: Signal.LED,
        ownerKey: String,
        runToken: Long,
        frameIndex: Int
    ) {
        val frames = state.value.preRenderedFrames
        if (!isRunActive(ownerKey, runToken) || frames.isEmpty()) {
            return
        }

        val maxFrame = frames.keys.maxOrNull() ?: 0
        if (frameIndex > maxFrame) {
            cancelRun(ownerKey)
            return
        }

        val currentLeds = frames[frameIndex] ?: emptyList()
        val currentCoords = currentLeds.map { it.x to it.y }.toSet()
        val prevCoords = activeLedsPerRun[ownerKey] ?: emptySet()

        val signals = mutableListOf<Signal.LED>()

        // Turn off LEDs from the previous frame that are not in the current frame
        for (coord in prevCoords) {
            if (coord !in currentCoords) {
                signals.add(Signal.LED(origin = this, x = coord.first, y = coord.second, color = Color.Black))
            }
        }

        // Turn on / update LEDs for the current frame
        for (led in currentLeds) {
            signals.add(
                Signal.LED(
                    origin = this,
                    x = led.x,
                    y = led.y,
                    color = androidx.compose.ui.graphics.Color(led.color.first, led.color.second, led.color.third)
                )
            )
        }

        activeLedsPerRun[ownerKey] = currentCoords
        signalExit?.invoke(signals)

        val fps = 30f
        val delayMs = 1000.0 / fps

        Heaven.schedule(
            delayInMs = delayMs,
            owner = this,
            identifier = ownerKey
        ) {
            scheduleFrameStep(triggerSignal, ownerKey, runToken, frameIndex + 1)
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        n.forEach { signal ->
            if (signal.color != androidx.compose.ui.graphics.Color.Black) {
                val ownerKey = "${signal.x},${signal.y}"
                cancelRun(ownerKey)

                val frames = state.value.preRenderedFrames
                if (frames.isEmpty()) {
                    signalExit?.invoke(listOf(signal))
                    return@forEach
                }

                val runToken = startRun(ownerKey)
                scheduleFrameStep(signal, ownerKey, runToken, 0)
            } else {
                val ownerKey = "${signal.x},${signal.y}"
                cancelRun(ownerKey)
            }
        }
    }

    override fun onChoke() {
        val keys = activeRunTokens.keys.toList()
        keys.forEach { cancelRun(it) }
        Heaven.cancelJobsForOwner(this)
    }

    companion object : ChainDeviceFactory<CompositionChainDeviceState> {
        override val stateClass = CompositionChainDeviceState::class
        override val serializer = CompositionChainDeviceState.serializer()
        override fun create() = CompositionChainDevice()
    }
}
