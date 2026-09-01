package dev.anthonyhfm.amethyst.devices.audio.automation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomation
import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomationCurve
import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomationRetriggerMode
import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomationRuntime
import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomationTarget
import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomationTimingUnit
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerRuntime
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerRuntimeAware
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.LiveAutomationSource
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.PadTriggerKey
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.toPadTriggerEvent
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceCapability
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.FlatDial
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.Tabs
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsContent
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsList
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsTrigger
import dev.anthonyhfm.amethyst.ui.theme.chart2
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.ui.components.automation.ParameterAutomationPopover
import dev.anthonyhfm.amethyst.core.controls.automation.AutomationParameter
import dev.anthonyhfm.amethyst.core.parameter.ParameterOwner
import dev.anthonyhfm.amethyst.devices.devicesDepthFirst

class AutomationChainDevice : AudioChainDevice<AutomationChainDeviceState>(),
    AudioTriggerRuntimeAware, LiveAutomationSource {
    override val state = MutableStateFlow(AutomationChainDeviceState())
    override val helpRef = "LiveAutomation"
    override val capabilities = setOf(DeviceCapability.Modulation)
    override val target: LiveAutomationTarget get() = state.value.target
    override val isAutomationRunning: Boolean get() = runtime.isRunning

    private val runtime = LiveAutomationRuntime(state.value.automation)
    private val configuration = atomic(AudioConfiguration(44_100, 2, 128))
    private val pendingFrame = atomic(-1L)
    private val pendingBpm = atomic(120f)
    private val pendingAutomation = atomic<LiveAutomation?>(null)
    private val pendingStopFrame = atomic(-1L)
    private val pendingTriggerKey = atomic<PadTriggerKey?>(null)
    private val activeTriggerKey = atomic<PadTriggerKey?>(null)

    val currentNormalizedValue: Float?
        get() = if (isAutomationRunning) {
            automationValueAt(audioTriggerRuntime?.currentFrame ?: 0L)
        } else null

    private fun updateStateWithHistory(transform: (AutomationChainDeviceState) -> AutomationChainDeviceState) {
        val before = state.value
        val after = transform(before)
        if (after != before) {
            state.value = after
            pushStateChange(before, after)
        }
    }

    override fun automationValueAt(frame: Long): Float =
        ((runtime.valueAtFrame(frame) + 1f) * 0.5f).coerceIn(0f, 1f)

    override fun signalEnter(n: List<Signal>) {
        n.filterIsInstance<Signal.Midi>().firstOrNull { it.velocity > 0 }?.let { signal ->
            val event = signal.toPadTriggerEvent(audioTriggerRuntime?.currentFrame ?: 0L)
            pendingAutomation.value = state.value.automation
            pendingBpm.value = WorkspaceRepository.bpm.value.toFloat()
            pendingTriggerKey.value = event.key
            pendingFrame.value = event.targetFrame
        }
        if (state.value.automation.settings.stopOnPadUp) {
            n.filterIsInstance<Signal.Midi>().firstOrNull { it.velocity == 0 }?.let { signal ->
                val event = signal.toPadTriggerEvent(audioTriggerRuntime?.currentFrame ?: 0L)
                if (event.key == activeTriggerKey.value || event.key == pendingTriggerKey.value) {
                    pendingStopFrame.value = event.targetFrame
                }
            }
        }
        signalExit?.invoke(n)
    }

    override fun prepareAudio(configuration: AudioConfiguration) {
        this.configuration.value = configuration
        runtime.stop()
        pendingFrame.value = -1L
        pendingTriggerKey.value = null
        activeTriggerKey.value = null
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        val frame = pendingFrame.value
        if (frame >= 0L && frame <= context.absoluteFrame + block.frameCount) {
            pendingAutomation.getAndSet(null)?.let { automation ->
                runtime.automation = automation
                if (runtime.trigger(frame, configuration.value.sampleRate, pendingBpm.value)) {
                    activeTriggerKey.value = pendingTriggerKey.value
                }
            }
            pendingTriggerKey.value = null
            pendingFrame.compareAndSet(frame, -1L)
        }
        if (runtime.isRunning) runtime.valueAtFrame(context.absoluteFrame + block.frameCount)
        if (!runtime.isRunning) activeTriggerKey.value = null
        val stopFrame = pendingStopFrame.value
        if (stopFrame >= 0L && stopFrame <= context.absoluteFrame + block.frameCount) {
            runtime.stop()
            activeTriggerKey.value = null
            pendingStopFrame.compareAndSet(stopFrame, -1L)
        }
    }

    override fun resetAudio() {
        runtime.stop()
        pendingFrame.value = -1L
        pendingStopFrame.value = -1L
        pendingTriggerKey.value = null
        activeTriggerKey.value = null
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val macros by WorkspaceRepository.macros.collectAsState()
        // Reading the Compose state keeps target options current when devices are added/removed.
        WorkspaceRepository.samplingChain.devices.value
        val curveColor = Theme[colors][chart2]
        val macroOptions = macros.mapIndexed { index, macro ->
            macro.id to macro.name.ifBlank { "Macro ${index + 1}" }
        }
        val parameterOptions = WorkspaceRepository.samplingChain.devicesDepthFirst()
            .mapNotNull { device ->
                (device as? ParameterOwner)?.let { owner -> device to owner }
            }
            .flatMap { (device, owner) ->
                owner.parameterDescriptors.filter { it.automatable }.map { descriptor ->
                    val address = dev.anthonyhfm.amethyst.core.parameter.ParameterAddress(
                        device.selectionUUID,
                        descriptor.id,
                    )
                    address to "${device.title} / ${descriptor.label}"
                }
            }
            .distinctBy { it.first }
        var curveEditorMode by remember { mutableStateOf("Simple") }
        var showAdvancedEditor by remember { mutableStateOf(false) }

        ChainDeviceShell(
            title = "Live Automation",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(360.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val isMacro = deviceState.target is LiveAutomationTarget.Macro
                LabeledSelect("Target type", if (isMacro) "Macro" else "Parameter", listOf("Macro", "Parameter")) { value ->
                    updateStateWithHistory {
                        it.copy(target = if (value == "Macro") {
                            LiveAutomationTarget.Macro(macroOptions.firstOrNull()?.first.orEmpty())
                        } else {
                            LiveAutomationTarget.Parameter(
                                parameterOptions.firstOrNull()?.first
                                    ?: dev.anthonyhfm.amethyst.core.parameter.ParameterAddress("missing", "missing"),
                            )
                        })
                    }
                }

                if (isMacro) {
                    val selectedId = (deviceState.target as LiveAutomationTarget.Macro).macroId
                    LabeledSelect(
                        "Target",
                        macroOptions.firstOrNull { it.first == selectedId }?.second ?: "Missing target",
                        macroOptions.map { it.second }.ifEmpty { listOf("Missing target") },
                    ) { label ->
                        updateStateWithHistory { it.copy(target = LiveAutomationTarget.Macro(macroOptions.firstOrNull { pair -> pair.second == label }?.first.orEmpty())) }
                    }
                } else {
                    val selected = (deviceState.target as LiveAutomationTarget.Parameter).address
                    LabeledSelect(
                        "Target",
                        parameterOptions.firstOrNull { it.first == selected }?.second ?: "Missing target",
                        parameterOptions.map { it.second }.ifEmpty { listOf("Missing target") },
                    ) { label ->
                        parameterOptions.firstOrNull { it.second == label }?.first?.let { address ->
                            updateStateWithHistory { it.copy(target = LiveAutomationTarget.Parameter(address)) }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EndpointDial("From A", deviceState.automation.startValue) { value ->
                        updateStateWithHistory { it.copy(automation = it.automation.withEndpoints(value, it.automation.endValue)) }
                    }
                    EndpointDial("To B", deviceState.automation.endValue) { value ->
                        updateStateWithHistory { it.copy(automation = it.automation.withEndpoints(it.automation.startValue, value)) }
                    }
                    FlatDial(
                        type = DialType.Continuous,
                        title = "Duration",
                        text = durationText(deviceState.automation),
                        value = (deviceState.automation.settings.durationValue / 16f).coerceIn(0f, 1f),
                        onValueChange = { normalized ->
                            updateStateWithHistory { it.copy(automation = it.automation.copy(settings = it.automation.settings.copy(durationValue = (normalized * 16f).coerceAtLeast(0.01f)))) }
                        },
                    )
                }

                LabeledSelect("Unit", deviceState.automation.settings.timingUnit.name, LiveAutomationTimingUnit.entries.map { it.name }) { value ->
                    updateStateWithHistory { it.copy(automation = it.automation.copy(settings = it.automation.settings.copy(timingUnit = LiveAutomationTimingUnit.valueOf(value)))) }
                }
                Tabs(selectedTab = curveEditorMode, tabs = listOf("Simple", "Advanced")) {
                    TabsList(Modifier.fillMaxWidth()) {
                        listOf("Simple", "Advanced").forEach { tab ->
                            TabsTrigger(
                                key = tab,
                                selected = curveEditorMode == tab,
                                onSelected = { curveEditorMode = tab },
                                modifier = Modifier.weight(1f),
                            ) { Text(tab) }
                        }
                    }
                    TabsContent("Simple") {
                        LabeledSelect(
                            "Curve",
                            deviceState.automation.settings.curve.takeUnless { it == LiveAutomationCurve.Bezier }?.name
                                ?: LiveAutomationCurve.Linear.name,
                            LiveAutomationCurve.entries.filterNot { it == LiveAutomationCurve.Bezier }.map { it.name },
                        ) { value ->
                            updateStateWithHistory { it.copy(automation = it.automation.copy(settings = it.automation.settings.copy(curve = LiveAutomationCurve.valueOf(value)))) }
                        }
                    }
                    TabsContent("Advanced") {
                        ParameterAutomationPopover(
                            expanded = showAdvancedEditor,
                            parameter = object : AutomationParameter {
                                override val id = "liveAutomation"
                                override val label = "Live Automation"
                            },
                            lane = deviceState.automation,
                            onUpdateLane = { automation ->
                                updateStateWithHistory {
                                    it.copy(automation = automation.copy(settings = automation.settings.copy(curve = LiveAutomationCurve.Bezier)))
                                }
                            },
                            onRemoveAutomation = { showAdvancedEditor = false },
                            onDismissRequest = { showAdvancedEditor = false },
                        ) {
                            Button(onClick = {
                                updateStateWithHistory { it.copy(automation = it.automation.copy(settings = it.automation.settings.copy(curve = LiveAutomationCurve.Bezier))) }
                                showAdvancedEditor = true
                            }) { Text("Open Bezier editor") }
                        }
                    }
                }
                LabeledSelect("Retrigger", deviceState.automation.settings.retriggerMode.label, LiveAutomationRetriggerMode.entries.map { it.label }) { value ->
                    updateStateWithHistory { it.copy(automation = it.automation.copy(settings = it.automation.settings.copy(retriggerMode = LiveAutomationRetriggerMode.entries.first { mode -> mode.label == value }))) }
                }
                LabeledSelect(
                    "Pad Up",
                    if (deviceState.automation.settings.stopOnPadUp) "Stop automation" else "Ignore",
                    listOf("Ignore", "Stop automation"),
                ) { value ->
                    updateStateWithHistory { it.copy(automation = it.automation.copy(settings = it.automation.settings.copy(stopOnPadUp = value == "Stop automation"))) }
                }

                Canvas(Modifier.fillMaxWidth().height(92.dp)) {
                    val path = Path()
                    repeat(49) { index ->
                        val progress = index / 48f
                        val normalized = (deviceState.automation.valueAt(progress, 0f) + 1f) * 0.5f
                        val point = Offset(progress * size.width, (1f - normalized) * size.height)
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, curveColor, style = Stroke(width = 2.dp.toPx()))
                }
                Text(if (isAutomationRunning) "AUTO · running" else "AUTO · ready")
            }
        }
    }

    companion object : ChainDeviceFactory<AutomationChainDeviceState> {
        override val capabilities = setOf(DeviceCapability.Modulation)
        override val stateClass = AutomationChainDeviceState::class
        override val serializer = AutomationChainDeviceState.serializer()
        override fun create() = AutomationChainDevice()
    }
}

@Serializable
data class AutomationChainDeviceState(
    val target: LiveAutomationTarget = LiveAutomationTarget.Macro(""),
    val automation: LiveAutomation = LiveAutomation(),
) : DeviceState()

@Composable
private fun LabeledSelect(label: String, value: String, options: List<String>, onValueChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        Select(value = value, options = options, triggerHeight = 32.dp, onValueChange = onValueChange)
    }
}

@Composable
private fun EndpointDial(label: String, value: Float, onValueChange: (Float) -> Unit) {
    FlatDial(
        type = DialType.Continuous,
        title = label,
        text = (((value + 1f) * 0.5f) * 127f).roundToInt().toString(),
        value = ((value + 1f) * 0.5f).coerceIn(0f, 1f),
        onValueChange = { onValueChange(it * 2f - 1f) },
        onResolveTextValue = { text ->
            text.trim().toFloatOrNull()?.takeIf { it in 0f..127f }?.let {
                onValueChange(it / 127f * 2f - 1f)
            }
        },
    )
}

private val LiveAutomationRetriggerMode.label: String get() = when (this) {
    LiveAutomationRetriggerMode.IgnoreWhileRunning -> "Ignore while running"
    LiveAutomationRetriggerMode.Restart -> "Restart"
    LiveAutomationRetriggerMode.ContinueFromCurrent -> "Continue from current"
    LiveAutomationRetriggerMode.Blend -> "Blend"
}

private fun durationText(automation: LiveAutomation): String = when (automation.settings.timingUnit) {
    LiveAutomationTimingUnit.Milliseconds -> "${automation.settings.durationValue.roundToInt()} ms"
    LiveAutomationTimingUnit.Beats -> "${automation.settings.durationValue} beats"
}
