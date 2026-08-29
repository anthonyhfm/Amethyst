package dev.anthonyhfm.amethyst.devices.audio.effects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.parameter.ParameterDescriptor
import dev.anthonyhfm.amethyst.core.parameter.ParameterOwner
import dev.anthonyhfm.amethyst.core.parameter.ParameterSmoothing
import dev.anthonyhfm.amethyst.core.parameter.SmoothedParameter
import dev.anthonyhfm.amethyst.core.parameter.resolveRealtimeParameter
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceCapability
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

class SaturatorChainDevice : AudioChainDevice<SaturatorChainDeviceState>(), ParameterOwner {
    override val state = MutableStateFlow(SaturatorChainDeviceState())
    override val helpRef = "Saturator"
    override val parameterDescriptors get() = PARAMETERS
    private var configuration = AudioConfiguration(44_100, 2, 128)
    private val mix = SmoothedParameter(1f)
    private val output = SmoothedParameter(1f)

    override fun prepareAudio(configuration: AudioConfiguration) {
        this.configuration = configuration
        resetAudio()
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        val snapshot = state.value
        var frame = 0
        while (frame < block.frameCount) {
            val absoluteFrame = context.absoluteFrame + frame
            val driveDb = resolveRealtimeParameter(PARAMETERS[0], snapshot.driveDb, absoluteFrame).coerceIn(0f, 36f)
            val drive = 10.0.pow(driveDb / 20.0).toFloat()
            val outputDb = resolveRealtimeParameter(PARAMETERS[1], snapshot.outputDb, absoluteFrame).coerceIn(-24f, 6f)
            val compensation = if (snapshot.outputCompensation) 1f / drive else 1f
            output.setTarget(10.0.pow(outputDb / 20.0).toFloat() * compensation)
            mix.setTarget(resolveRealtimeParameter(PARAMETERS[2], snapshot.dryWet, absoluteFrame).coerceIn(0f, 1f))
            val outputGain = output.next(configuration.sampleRate, GAIN_SMOOTHING)
            val wet = mix.next(configuration.sampleRate, MIX_SMOOTHING)
            val normalization = tanh(drive).takeIf { kotlin.math.abs(it) > 0.000001f } ?: 1f
            val offset = frame * block.channels
            var channel = 0
            while (channel < block.channels) {
                val dry = block.samples[offset + channel].finiteOrZero()
                val saturated = (tanh(dry * drive) / normalization * outputGain).finiteOrZero()
                block.samples[offset + channel] = (dry + (saturated - dry) * wet).finiteOrZero()
                channel++
            }
            frame++
        }
    }

    override fun resetAudio() {
        val snapshot = state.value
        val drive = 10.0.pow(snapshot.driveDb.coerceIn(0f, 36f) / 20.0).toFloat()
        val compensation = if (snapshot.outputCompensation) 1f / drive else 1f
        output.reset(10.0.pow(snapshot.outputDb.coerceIn(-24f, 6f) / 20.0).toFloat() * compensation)
        mix.reset(snapshot.dryWet.coerceIn(0f, 1f))
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        ChainDeviceShell(
            title = "Saturator",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(360.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    EffectDial("drive", "Drive", deviceState.driveDb / 36f, "${deviceState.driveDb.roundToInt()} dB") {
                        state.update { s -> s.copy(driveDb = it * 36f) }
                    }
                    EffectDial("output", "Output", (deviceState.outputDb + 24f) / 30f, "${deviceState.outputDb.roundToInt()} dB") {
                        state.update { s -> s.copy(outputDb = it * 30f - 24f) }
                    }
                    EffectDial("dryWet", "Dry / Wet", deviceState.dryWet, "${(deviceState.dryWet * 100).roundToInt()}%") {
                        state.update { s -> s.copy(dryWet = it) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Checkbox(
                        checked = deviceState.outputCompensation,
                        onCheckedChange = { enabled -> state.update { it.copy(outputCompensation = enabled) } },
                        size = 22.dp,
                        iconSize = 16.dp,
                    )
                    Text(if (deviceState.outputCompensation) "Output compensation · On" else "Output compensation · Off")
                }
            }
        }
    }

    companion object : ChainDeviceFactory<SaturatorChainDeviceState> {
        override val capabilities = setOf(DeviceCapability.AudioEffect)
        override val stateClass = SaturatorChainDeviceState::class
        override val serializer = SaturatorChainDeviceState.serializer()
        override fun create() = SaturatorChainDevice()
        val PARAMETERS = listOf(
            ParameterDescriptor("drive", "Drive", "dB", 0f, 36f, 0f),
            ParameterDescriptor("output", "Output", "dB", -24f, 6f, 0f),
            ParameterDescriptor("dryWet", "Dry / Wet", "%", 0f, 1f, 1f),
        )
        private val GAIN_SMOOTHING = ParameterSmoothing(5f)
        private val MIX_SMOOTHING = ParameterSmoothing(5f)
    }
}

@Serializable
data class SaturatorChainDeviceState(
    val driveDb: Float = 0f,
    val outputDb: Float = 0f,
    val dryWet: Float = 1f,
    val outputCompensation: Boolean = false,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}
