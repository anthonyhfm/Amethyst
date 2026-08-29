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
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.parameter.ParameterDescriptor
import dev.anthonyhfm.amethyst.core.parameter.ParameterOwner
import dev.anthonyhfm.amethyst.core.parameter.ParameterScale
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
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToInt

class EqThreeChainDevice : AudioChainDevice<EqThreeChainDeviceState>(), ParameterOwner {
    override val state = MutableStateFlow(EqThreeChainDeviceState())
    override val helpRef = "EQThree"
    override val parameterDescriptors get() = PARAMETERS

    private var configuration = AudioConfiguration(44_100, 2, 128)
    private val lowSplit = StereoOnePoleLowPass()
    private val midSplit = StereoOnePoleLowPass()
    private val lowGain = SmoothedParameter(1f)
    private val midGain = SmoothedParameter(1f)
    private val highGain = SmoothedParameter(1f)
    private val outputGain = SmoothedParameter(1f)

    override fun prepareAudio(configuration: AudioConfiguration) {
        this.configuration = configuration
        resetAudio()
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        val snapshot = state.value
        var frame = 0
        while (frame < block.frameCount) {
            val absoluteFrame = context.absoluteFrame + frame
            val lowCrossover = resolveRealtimeParameter(PARAMETERS[3], snapshot.lowCrossoverHz, absoluteFrame)
            val highCrossover = resolveRealtimeParameter(PARAMETERS[4], snapshot.highCrossoverHz, absoluteFrame)
                .coerceAtLeast(lowCrossover + 10f)
            lowSplit.configure(lowCrossover, configuration.sampleRate)
            midSplit.configure(highCrossover, configuration.sampleRate)
            lowGain.setTarget(if (snapshot.lowKilled) 0f else dbGain(resolveRealtimeParameter(PARAMETERS[0], snapshot.lowGainDb, absoluteFrame)))
            midGain.setTarget(if (snapshot.midKilled) 0f else dbGain(resolveRealtimeParameter(PARAMETERS[1], snapshot.midGainDb, absoluteFrame)))
            highGain.setTarget(if (snapshot.highKilled) 0f else dbGain(resolveRealtimeParameter(PARAMETERS[2], snapshot.highGainDb, absoluteFrame)))
            outputGain.setTarget(dbGain(resolveRealtimeParameter(PARAMETERS[5], snapshot.outputGainDb, absoluteFrame)))
            val lowLevel = lowGain.next(configuration.sampleRate, GAIN_SMOOTHING)
            val midLevel = midGain.next(configuration.sampleRate, GAIN_SMOOTHING)
            val highLevel = highGain.next(configuration.sampleRate, GAIN_SMOOTHING)
            val outputLevel = outputGain.next(configuration.sampleRate, GAIN_SMOOTHING)
            val offset = frame * block.channels
            val left = block.samples[offset]
            val lowLeft = lowSplit.processLeft(left)
            val remainingLeft = left - lowLeft
            val midLeft = midSplit.processLeft(remainingLeft)
            val highLeft = remainingLeft - midLeft
            block.samples[offset] = ((lowLeft * lowLevel) + (midLeft * midLevel) + (highLeft * highLevel)) * outputLevel
            if (block.channels > 1) {
                val right = block.samples[offset + 1]
                val lowRight = lowSplit.processRight(right)
                val remainingRight = right - lowRight
                val midRight = midSplit.processRight(remainingRight)
                val highRight = remainingRight - midRight
                block.samples[offset + 1] = ((lowRight * lowLevel) + (midRight * midLevel) + (highRight * highLevel)) * outputLevel
            }
            frame++
        }
    }

    override fun resetAudio() {
        lowSplit.reset(); midSplit.reset()
        lowGain.reset(1f); midGain.reset(1f); highGain.reset(1f); outputGain.reset(1f)
    }

    override fun signalEnter(n: List<Signal>) = super.signalEnter(n)

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        ChainDeviceShell(
            title = "EQ Three",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(430.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GainDial("lowGain", "Low", deviceState.lowGainDb) { state.update { s -> s.copy(lowGainDb = it) } }
                    GainDial("midGain", "Mid", deviceState.midGainDb) { state.update { s -> s.copy(midGainDb = it) } }
                    GainDial("highGain", "High", deviceState.highGainDb) { state.update { s -> s.copy(highGainDb = it) } }
                    GainDial("output", "Output", deviceState.outputGainDb) { state.update { s -> s.copy(outputGainDb = it) } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    KillToggle("Low kill", deviceState.lowKilled) { state.update { s -> s.copy(lowKilled = it) } }
                    KillToggle("Mid kill", deviceState.midKilled) { state.update { s -> s.copy(midKilled = it) } }
                    KillToggle("High kill", deviceState.highKilled) { state.update { s -> s.copy(highKilled = it) } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FrequencyDial("lowCrossover", "Low / Mid", deviceState.lowCrossoverHz, 60f, 2_000f) { state.update { s -> s.copy(lowCrossoverHz = it) } }
                    FrequencyDial("highCrossover", "Mid / High", deviceState.highCrossoverHz, 500f, 16_000f) { state.update { s -> s.copy(highCrossoverHz = it) } }
                }
            }
        }
    }

    companion object : ChainDeviceFactory<EqThreeChainDeviceState> {
        override val capabilities = setOf(DeviceCapability.AudioEffect)
        override val stateClass = EqThreeChainDeviceState::class
        override val serializer = EqThreeChainDeviceState.serializer()
        override fun create() = EqThreeChainDevice()
        val PARAMETERS = listOf(
            ParameterDescriptor("lowGain", "Low Gain", "dB", -24f, 24f, 0f),
            ParameterDescriptor("midGain", "Mid Gain", "dB", -24f, 24f, 0f),
            ParameterDescriptor("highGain", "High Gain", "dB", -24f, 24f, 0f),
            ParameterDescriptor("lowCrossover", "Low / Mid Crossover", "Hz", 60f, 2_000f, 250f, ParameterScale.Logarithmic),
            ParameterDescriptor("highCrossover", "Mid / High Crossover", "Hz", 500f, 16_000f, 2_500f, ParameterScale.Logarithmic),
            ParameterDescriptor("output", "Output", "dB", -24f, 24f, 0f),
        )
        private val GAIN_SMOOTHING = ParameterSmoothing(3f)
        private fun dbGain(db: Float) = 10.0.pow(db / 20.0).toFloat()
    }
}

@Serializable
data class EqThreeChainDeviceState(
    val lowGainDb: Float = 0f,
    val midGainDb: Float = 0f,
    val highGainDb: Float = 0f,
    val lowCrossoverHz: Float = 250f,
    val highCrossoverHz: Float = 2_500f,
    val outputGainDb: Float = 0f,
    val lowKilled: Boolean = false,
    val midKilled: Boolean = false,
    val highKilled: Boolean = false,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}

@Composable
private fun GainDial(id: String, label: String, value: Float, onValue: (Float) -> Unit) {
    AutomatableDial(
        parameterId = id,
        type = DialType.Continuous,
        value = ((value + 24f) / 48f).coerceIn(0f, 1f),
        defaultValue = 0.5f,
        title = label,
        text = "${value.roundToInt()} dB",
        onValueChange = { onValue(it * 48f - 24f) },
        isFlat = true,
    )
}

@Composable
private fun FrequencyDial(id: String, label: String, value: Float, min: Float, max: Float, onValue: (Float) -> Unit) {
    AutomatableDial(
        parameterId = id,
        type = DialType.Continuous,
        value = ((value - min) / (max - min)).coerceIn(0f, 1f),
        defaultValue = 0.5f,
        title = label,
        text = "${value.roundToInt()} Hz",
        onValueChange = { onValue(min + it * (max - min)) },
        isFlat = true,
    )
}

@Composable
private fun KillToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChecked, size = 22.dp, iconSize = 16.dp)
        Text(if (checked) "$label · On" else "$label · Off")
    }
}
