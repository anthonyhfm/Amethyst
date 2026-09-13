package dev.anthonyhfm.amethyst.devices.effects.switch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane
import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomationTarget
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerRuntime
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioTriggerRuntimeAware
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.LiveAutomationSource
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.parameter.ParameterDescriptor
import dev.anthonyhfm.amethyst.core.parameter.ParameterOwner
import dev.anthonyhfm.amethyst.core.parameter.ParameterScale
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.atomicfu.atomic
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
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Macro Control",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(120.dp),
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
                    if (macros.size > 1) {
                        var beforeMacro = deviceState.copy().macro
                        Dial(
                            title = "Macro",
                            value = selectedIndex,
                            type = DialType.Steps(IntArray(macros.size) { it }.toList()),
                            text = selectedMacro.name.ifBlank { "Macro ${selectedIndex + 1}" },
                            onResolveTextValue = {
                                val macroText = it.trim().toIntOrNull()

                                macroText?.let { macro ->
                                    if (macro in 1..macros.size) {
                                        clearAutomationOverride()
                                        state.update {
                                            it.copy(macro = macro - 1, macroId = macros[macro - 1].id)
                                        }
                                    }
                                }
                            },
                            onStartValueChange = {
                                beforeMacro = it
                            },
                            onFinishValueChange = {
                                pushStateChange(
                                    before = state.value.copy(macro = beforeMacro),
                                    after = state.value
                                )
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
                                text = selectedMacro.name.ifBlank { "Macro 1" },
                                textAlign = TextAlign.Center,
                                style = Theme[typography][small],
                                color = Theme[colors][foreground]
                            )
                        }
                    }

                    AutomatableDial(
                        parameterId = "value",
                        title = "Value",
                        value = deviceState.value,
                        defaultValue = 0,
                        type = DialType.Steps(IntArray(128) { it }.toList()),
                        text = deviceState.value.toString(),
                        onResolveTextValue = {
                            val valueText = it.trim().toIntOrNull()

                            valueText?.let { value ->
                                if (value in 0..127) {
                                    clearAutomationOverride()
                                    state.update {
                                        it.copy(value = value)
                                    }
                                }
                            }
                        },
                        onValueChange = { value ->
                            clearAutomationOverride()
                            state.update {
                                it.copy(value = value)
                            }
                        },
                    )
                } else {
                    Text(
                        text = "No macros available",
                        modifier = Modifier
                            .padding(horizontal = 12.dp),
                        textAlign = TextAlign.Center,
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground]
                    )
                }
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
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
            WorkspaceRepository.macros.value.getOrNull(macroIndex)?.let { macro ->
                WorkspaceRepository.setMacroValue(
                    index = macroIndex,
                    macro = macro.copy(value = targetValue),
                    undoable = false,
                )
            }

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
