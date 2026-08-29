package dev.anthonyhfm.amethyst.devices.effects.delay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane
import dev.anthonyhfm.amethyst.core.parameter.ParameterDescriptor
import dev.anthonyhfm.amethyst.core.parameter.ParameterOwner
import dev.anthonyhfm.amethyst.core.parameter.ParameterScale
import dev.anthonyhfm.amethyst.core.parameter.ParameterSmoothing
import dev.anthonyhfm.amethyst.core.parameter.resolveControlParameter
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.TimeDial
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.atomicfu.atomic
import kotlin.math.pow
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext

class DelayChainDevice : GenericChainDevice<DelayChainDeviceState>(), Chokeable, ParameterOwner {
    override val helpRef = "Delay"
    override val state = MutableStateFlow(DelayChainDeviceState())
    override val parameterDescriptors get() = PARAMETERS
    private val activeJobs = atomic(0)
    val activeJobCount: Int get() = activeJobs.value

    override fun timelineDuration(context: TimelineDurationContext) =
        TimelineDuration.Finite(
            (state.value.timing.toMsValue(context.bpm.toDouble()) * (state.value.gate * 2f) * state.value.repeats)
                .toLong().coerceAtLeast(0L)
        )

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Delay",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(250.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var beforeDelay = deviceState.copy().let { (t, ms, _) ->
                    Pair(t, ms)
                }

                TimeDial(
                    title = "Delay",
                    timing = deviceState.timing,
                    onSelectTiming = { timing, msValue ->
                        state.update {
                            it.copy(timing = timing, delayMs = msValue)
                        }
                    },
                    onStartValueChange = { t, ms ->
                        beforeDelay = Pair(t, ms)
                    },
                    onFinishValueChange = { timing, msValue ->
                        pushStateChange(
                            before = state.value.copy(
                                timing = beforeDelay.first,
                                delayMs = beforeDelay.second
                            ),
                            after = state.value.copy(
                                timing = timing,
                                delayMs = msValue
                            )
                        )
                    }
                )

                var beforeGateDrag = deviceState.copy().gate
                Dial(
                    type = DialType.Continuous,
                    title = "Gate",
                    text = "${(deviceState.gate * 200).roundToInt()}%",
                    value = deviceState.gate,
                    onStartValueChange = {
                        beforeGateDrag = deviceState.gate
                    },
                    onValueChange = { value ->
                        state.update {
                            it.copy(gate = value)
                        }
                    },
                    onFinishValueChange = {
                        pushStateChange(state.value.copy(gate = beforeGateDrag), state.value.copy(gate = it))
                    },
                    onResolveTextValue = {
                        val gateText = it.removeSuffix("%").trim().toIntOrNull()

                        gateText?.let { gate ->
                            if (gate in 0..200) {
                                val before = state.value
                                val after = before.copy(gate = gate / 200f)
                                state.value = after
                                pushStateChange(before, after)
                            }
                        }
                    },
                    defaultValue = 0.5f,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AutomatableDial(
                        parameterId = "feedback",
                        type = DialType.Continuous,
                        title = "Feedback",
                        text = "${(deviceState.feedback * 100).roundToInt()}%",
                        value = deviceState.feedback,
                        defaultValue = 0.65f,
                        onValueChange = { value -> state.update { it.copy(feedback = value) } },
                        isFlat = true,
                    )
                    AutomatableDial(
                        parameterId = "repeats",
                        type = DialType.Continuous,
                        title = "Repeats",
                        text = deviceState.repeats.toString(),
                        value = (deviceState.repeats - 1f) / (MAX_REPEATS - 1f),
                        defaultValue = 0.28f,
                        onValueChange = { value ->
                            state.update { it.copy(repeats = (1 + value * (MAX_REPEATS - 1)).roundToInt()) }
                        },
                        isFlat = true,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.composeunstyled.Text("Routing")
                    Select(
                        value = deviceState.routing.label,
                        options = LightDelayRouting.entries.map { it.label },
                        triggerHeight = 44.dp,
                        onValueChange = { label ->
                            val before = state.value
                            state.update { it.copy(routing = LightDelayRouting.entries.first { mode -> mode.label == label }) }
                            pushStateChange(before, state.value)
                        },
                    )
                }
                if (deviceState.routing != LightDelayRouting.Direct) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val first = if (deviceState.routing == LightDelayRouting.PingPongSides) deviceState.sideA else deviceState.layerA
                        val second = if (deviceState.routing == LightDelayRouting.PingPongSides) deviceState.sideB else deviceState.layerB
                        val maximum = if (deviceState.routing == LightDelayRouting.PingPongSides) 7 else 15
                        Dial(
                            type = DialType.Continuous,
                            title = if (deviceState.routing == LightDelayRouting.PingPongSides) "Side A" else "Layer A",
                            text = first.toString(),
                            value = first.toFloat() / maximum,
                            onValueChange = { value ->
                                val resolved = (value * maximum).roundToInt()
                                state.update {
                                    if (it.routing == LightDelayRouting.PingPongSides) it.copy(sideA = resolved)
                                    else it.copy(layerA = resolved)
                                }
                            },
                        )
                        Dial(
                            type = DialType.Continuous,
                            title = if (deviceState.routing == LightDelayRouting.PingPongSides) "Side B" else "Layer B",
                            text = second.toString(),
                            value = second.toFloat() / maximum,
                            onValueChange = { value ->
                                val resolved = (value * maximum).roundToInt()
                                state.update {
                                    if (it.routing == LightDelayRouting.PingPongSides) it.copy(sideB = resolved)
                                    else it.copy(layerB = resolved)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
        triggerDialAutomations()
        val bpm = WorkspaceRepository.bpm.value
        val snapshot = state.value
        val baseDelay = snapshot.timing.toMsValue(bpm) * (snapshot.gate * 2f)
        val delay = resolveControlParameter(PARAMETERS[0], baseDelay.toFloat()).toDouble()
        val feedback = resolveControlParameter(PARAMETERS[1], snapshot.feedback).coerceIn(0f, 1f)
        val repeats = resolveControlParameter(PARAMETERS[2], snapshot.repeats.toFloat()).roundToInt()
            .coerceIn(1, MAX_REPEATS)
        var repeat = 1
        while (repeat <= repeats && activeJobs.value < MAX_ACTIVE_JOBS) {
            val repeatIndex = repeat
            activeJobs.incrementAndGet()
            Heaven.schedule(
                delayInMs = delay * repeat,
                owner = this,
                identifier = repeat,
            ) {
                releaseJobSlot()
                val opacity = feedback.pow(repeatIndex).coerceIn(0f, 1f)
                signalExit?.invoke(n.map { it.applyLightDelay(snapshot.routing, repeatIndex, opacity, snapshot) })
            }
            repeat++
        }
    }

    override fun onChoke() {
        // Cancel all scheduled Heaven tasks owned by this device
        Heaven.cancelJobsForOwner(this)
        activeJobs.value = 0
    }

    override fun onRemovedFromChain() {
        onChoke()
        super.onRemovedFromChain()
    }

    private fun releaseJobSlot() {
        while (true) {
            val count = activeJobs.value
            if (count <= 0 || activeJobs.compareAndSet(count, count - 1)) return
        }
    }

    companion object : ChainDeviceFactory<DelayChainDeviceState> {
        override val stateClass = DelayChainDeviceState::class
        override val serializer = DelayChainDeviceState.serializer()
        override fun create() = DelayChainDevice()
        val PARAMETERS = listOf(
            ParameterDescriptor("timeMs", "Time", "ms", 1f, 8_000f, 500f, ParameterScale.Logarithmic),
            ParameterDescriptor("feedback", "Feedback", "%", 0f, 1f, 0.65f),
            ParameterDescriptor(
                "repeats", "Repeats", minimum = 1f, maximum = 8f, defaultValue = 3f,
                scale = ParameterScale.Discrete,
                snapPoints = (1..8).map(Int::toFloat),
                smoothing = ParameterSmoothing.None,
            ),
        )
        const val MAX_REPEATS = 8
        const val MAX_ACTIVE_JOBS = 32
    }
}

@Serializable
enum class LightDelayRouting(val label: String) {
    Direct("Direct"), PingPongSides("Ping Pong · Sides"), PingPongLayers("Ping Pong · Layers")
}

@Serializable
data class DelayChainDeviceState(
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val delayMs: Long = 0,
    val gate: Float = 0.5f, // 100% = 0.5f, 200% = 1.0f
    val feedback: Float = 0.65f,
    val repeats: Int = 3,
    val routing: LightDelayRouting = LightDelayRouting.Direct,
    val sideA: Int = 0,
    val sideB: Int = 7,
    val layerA: Int = 0,
    val layerB: Int = 1,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}

internal fun Signal.applyLightDelay(
    routing: LightDelayRouting,
    repeat: Int,
    opacityMultiplier: Float,
    state: DelayChainDeviceState,
): Signal = if (this is Signal.LED) {
    copy(
        x = when (routing) {
            LightDelayRouting.PingPongSides -> if (repeat % 2 == 0) state.sideA else state.sideB
            else -> x
        },
        layer = when (routing) {
            LightDelayRouting.PingPongLayers -> if (repeat % 2 == 0) state.layerA else state.layerB
            else -> layer
        },
        opacity = (opacity * opacityMultiplier).coerceIn(0f, 1f),
    )
} else this
