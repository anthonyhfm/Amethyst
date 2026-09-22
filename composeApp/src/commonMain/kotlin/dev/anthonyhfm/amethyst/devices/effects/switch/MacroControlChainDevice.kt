package dev.anthonyhfm.amethyst.devices.effects.switch

import amethyst.composeapp.generated.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import org.jetbrains.compose.resources.stringResource
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane
import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomationTarget
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerRuntime
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerRuntimeAware
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.LiveAutomationSource
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.elements.SIGNAL_EXTRA_SILENT_REPLAY
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.parameter.ParameterDescriptor
import dev.anthonyhfm.amethyst.core.parameter.ParameterOwner
import dev.anthonyhfm.amethyst.core.parameter.ParameterScale
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.atomicfu.atomic
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import dev.anthonyhfm.amethyst.devices.DeviceCapability

class MacroControlChainDevice : GenericChainDevice<MacroControlChainDeviceState>(), ParameterOwner,
    AudioTriggerRuntimeAware, LiveAutomationSource {
    override val capabilities: Set<DeviceCapability> = setOf(DeviceCapability.Modulation)
    override fun timelineDuration(context: TimelineDurationContext) =
        TimelineDuration.None
    override val state = MutableStateFlow(MacroControlChainDeviceState())
    override val helpRef = "MacroControl"
    override val parameterDescriptors get() = PARAMETERS
    override var audioTriggerRuntime: AudioTriggerRuntime? = null
    override val target: LiveAutomationTarget
        get() = LiveAutomationTarget.Macro(resolvedMacroId().orEmpty())
    override val isAutomationRunning: Boolean
        get() = isDialAutomationRunning(VALUE_PARAMETER_ID) || latchedValue.value.isFinite()
    override val activationSequence: Long get() = activeSequence.value

    private val latchedValue = atomic(Float.NaN)
    private val activeSequence = atomic(0L)

    val currentNormalizedValue: Float?
        get() = if (isAutomationRunning) {
            automationValueAt(audioTriggerRuntime?.currentFrame ?: 0L)
        } else null

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val macros by WorkspaceRepository.macros.collectAsState()
        val mappings by WorkspaceRepository.parameterMappings.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val macroLabel = stringResource(Res.string.device_macro_control_macro_headline)
        val defaultMacroName = stringResource(Res.string.device_macro_control_macro_1)
        val effectiveValue by produceState(initialValue = deviceState.value) {
            while (true) {
                withFrameNanos {
                    value = (currentNormalizedValue?.times(127f)?.roundToInt() ?: state.value.value)
                        .coerceIn(0, 127)
                }
            }
        }

        ChainDeviceShell(
            title = stringResource(Res.string.device_macro_control_title),
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(156.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),

                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (macros.isNotEmpty()) {
                    val selectedIndex = macros.indexOfFirst { it.id == deviceState.macroId }
                        .takeIf { it >= 0 } ?: deviceState.macro.coerceIn(0, macros.lastIndex)
                    val selectedMacro = macros[selectedIndex]
                    val mappingCount = mappings.count { it.macroId == selectedMacro.id }
                    if (macros.size > 1) {
                        var beforeMacro by remember { mutableStateOf(deviceState) }
                        Dial(
                            title = macroLabel,
                            value = selectedIndex,
                            type = DialType.Steps(IntArray(macros.size) { it }.toList()),
                            text = selectedMacro.name.ifBlank { "$macroLabel ${selectedIndex + 1}" },
                            onResolveTextValue = {
                                val macroText = it.trim().toIntOrNull()

                                macroText?.let { macro ->
                                    if (macro in 1..macros.size) {
                                        val before = state.value
                                        clearAutomationOverride()
                                        state.update {
                                            it.copy(macro = macro - 1, macroId = macros[macro - 1].id)
                                        }
                                        pushStateChange(before, state.value)
                                    }
                                }
                            },
                            onStartValueChange = {
                                beforeMacro = state.value
                            },
                            onFinishValueChange = {
                                pushStateChange(before = beforeMacro, after = state.value)
                            },
                            onValueChange = { value ->
                                clearAutomationOverride()
                                state.update {
                                    it.copy(macro = value, macroId = macros[value].id)
                                }
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedMacro.name.ifBlank { "$macroLabel 1" },
                                textAlign = TextAlign.Center,
                                style = Theme[typography][small],
                                color = Theme[colors][foreground]
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(
                                if (mappingCount == 1) Res.string.device_macro_control_mapping_count
                                else Res.string.device_macro_control_mappings_count,
                                mappingCount,
                            ),
                            style = Theme[typography][small],
                            color = Theme[colors][mutedForeground],
                        )
                        if (getDialAutomation(VALUE_PARAMETER_ID) != null) {
                            Text(
                                text = stringResource(Res.string.device_macro_control_auto),
                                style = Theme[typography][small],
                                color = Theme[colors][foreground],
                            )
                        }
                    }

                    var beforeValue by remember { mutableStateOf(deviceState) }
                    AutomatableDial(
                        parameterId = "value",
                        title = stringResource(Res.string.device_macro_control_value_headline),
                        value = deviceState.value,
                        defaultValue = 0,
                        type = DialType.Steps(IntArray(128) { it }.toList()),
                        text = effectiveValue.toString(),
                        onResolveTextValue = {
                            val valueText = it.trim().toIntOrNull()

                            valueText?.let { value ->
                                if (value in 0..127) {
                                    val before = state.value
                                    clearAutomationOverride()
                                    state.update {
                                        it.copy(value = value)
                                    }
                                    pushStateChange(before, state.value)
                                }
                            }
                        },
                        onStartValueChange = { beforeValue = state.value },
                        onValueChange = { value ->
                            clearAutomationOverride()
                            state.update {
                                it.copy(value = value)
                            }
                        },
                        onFinishValueChange = { pushStateChange(beforeValue, state.value) },
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.device_macro_control_no_macros),
                        modifier = Modifier
                            .padding(horizontal = 12.dp),
                        textAlign = TextAlign.Center,
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground]
                    )
                    Button(
                        onClick = { WorkspaceRepository.addMacro(Macro(value = 0, name = defaultMacroName)) },
                        size = ButtonSize.Small,
                    ) {
                        Text(stringResource(Res.string.device_macro_control_create))
                    }
                }
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
        val silentReplay = n.any { it.extras[SIGNAL_EXTRA_SILENT_REPLAY] == 1 }
        val down = n.any {
            when (it) {
                is Signal.LED -> it.color != Color.Black
                is Signal.Midi -> it.velocity != 0
                else -> false
            }
        }
        val macroIndex = resolvedMacroIndex()
        val targetValue = state.value.value

        if (down && macroIndex != null) {
            if (getDialAutomation(VALUE_PARAMETER_ID) == null) {
                WorkspaceRepository.macros.value.getOrNull(macroIndex)?.let { macro ->
                    WorkspaceRepository.setMacroValue(
                        index = macroIndex,
                        macro = macro.copy(value = targetValue),
                        undoable = false,
                    )
                }
            }

            if (!silentReplay) {
                val runtime = audioTriggerRuntime
                val currentFrame = runtime?.currentFrame ?: 0L
                val requestedFrame = n.filterIsInstance<Signal.Midi>()
                    .firstNotNullOfOrNull { it.audioTriggerBatch?.requestedTargetFrame }
                    ?: currentFrame
                val frame = maxOf(requestedFrame, currentFrame)
                activeSequence.value = runtime?.nextAutomationSequence() ?: activeSequence.value + 1L
                latchedValue.value = Float.NaN
                if (getDialAutomation(VALUE_PARAMETER_ID) != null) {
                    triggerDialAutomationsAtFrame(
                        frame = frame,
                        sampleRate = runtime?.sampleRate ?: 44_100,
                        bpm = WorkspaceRepository.bpm.value.toFloat(),
                    )
                } else {
                    latchedValue.value = targetValue / 127f
                }
            }
        }

        signalExit?.invoke(n)
    }

    override fun automationValueAt(frame: Long): Float {
        val manual = state.value.value / 127f
        if (isDialAutomationRunning(VALUE_PARAMETER_ID)) {
            return evaluateAutomatedDialValueAtFrame(VALUE_PARAMETER_ID, manual, frame)
                .also { latchedValue.value = it }
        }
        return latchedValue.value.takeIf(Float::isFinite) ?: manual
    }

    override fun clearAutomationOverride() {
        stopDialAutomations()
        latchedValue.value = Float.NaN
    }

    private fun resolvedMacroId(): String? {
        val index = resolvedMacroIndex() ?: return null
        return WorkspaceRepository.macros.value.getOrNull(index)?.id
    }

    private fun resolvedMacroIndex(): Int? {
        val macros = WorkspaceRepository.macros.value
        if (macros.isEmpty()) return null
        return macros.indexOfFirst { it.id == state.value.macroId }
            .takeIf { it >= 0 }
            ?: state.value.macro.takeIf { it in macros.indices }
    }

    companion object : ChainDeviceFactory<MacroControlChainDeviceState> {
        override val capabilities: Set<DeviceCapability> = setOf(DeviceCapability.Modulation)
        override val stateClass = MacroControlChainDeviceState::class
        override val serializer = MacroControlChainDeviceState.serializer()
        override fun create() = MacroControlChainDevice()

        val PARAMETERS = listOf(
            ParameterDescriptor(
                id = "value",
                label = "Value",
                unit = "",
                minimum = 0f,
                maximum = 127f,
                defaultValue = 0f,
                scale = ParameterScale.Discrete,
                snapPoints = List(128) { it.toFloat() },
            ),
        )
        private const val VALUE_PARAMETER_ID = "value"
    }
}

@Serializable
data class MacroControlChainDeviceState(
    val macro: Int = 0,
    val value: Int = 0,
    val macroId: String? = null,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState =
        copy(automations = automations)
}
