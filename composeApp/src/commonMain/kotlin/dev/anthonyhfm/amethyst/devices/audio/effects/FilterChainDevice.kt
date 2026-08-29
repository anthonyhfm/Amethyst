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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
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
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.tanh

@Serializable
enum class FilterType(val label: String) {
    LowPass("Low-pass"), HighPass("High-pass"), BandPass("Band-pass"), Notch("Notch")
}

@Serializable
enum class FilterSlope(val label: String) { Db12("12 dB"), Db24("24 dB") }

class FilterChainDevice : AudioChainDevice<FilterChainDeviceState>(), ParameterOwner {
    override val state = MutableStateFlow(FilterChainDeviceState())
    override val helpRef = "Filter"
    override val parameterDescriptors get() = PARAMETERS
    private var configuration = AudioConfiguration(44_100, 2, 128)
    private val stageOne = StereoBiquad()
    private val stageTwo = StereoBiquad()
    private val wet = SmoothedParameter(1f)

    override fun prepareAudio(configuration: AudioConfiguration) {
        this.configuration = configuration
        resetAudio()
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        val snapshot = state.value
        val type = snapshot.type.toBiquad()
        var frame = 0
        while (frame < block.frameCount) {
            val absoluteFrame = context.absoluteFrame + frame
            val cutoff = resolveRealtimeParameter(PARAMETERS[0], snapshot.cutoffHz, absoluteFrame)
            val resonance = resolveRealtimeParameter(PARAMETERS[1], snapshot.resonance, absoluteFrame)
            wet.setTarget(resolveRealtimeParameter(PARAMETERS[2], snapshot.dryWet, absoluteFrame))
            val driveDb = resolveRealtimeParameter(PARAMETERS[3], snapshot.driveDb, absoluteFrame)
            val driveGain = 10.0.pow(driveDb / 20.0).toFloat()
            val wetValue = wet.next(configuration.sampleRate, WET_SMOOTHING)
            stageOne.configure(type, cutoff, resonance, configuration.sampleRate)
            stageTwo.configure(type, cutoff, resonance, configuration.sampleRate)
            val offset = frame * block.channels
            val dryLeft = block.samples[offset]
            val drivenLeft = driveSample(dryLeft, driveDb, driveGain)
            var filteredLeft = stageOne.processLeft(drivenLeft)
            if (snapshot.slope == FilterSlope.Db24) filteredLeft = stageTwo.processLeft(filteredLeft)
            block.samples[offset] = dryLeft + (filteredLeft - dryLeft) * wetValue
            if (block.channels > 1) {
                val dryRight = block.samples[offset + 1]
                val drivenRight = driveSample(dryRight, driveDb, driveGain)
                var filteredRight = stageOne.processRight(drivenRight)
                if (snapshot.slope == FilterSlope.Db24) filteredRight = stageTwo.processRight(filteredRight)
                block.samples[offset + 1] = dryRight + (filteredRight - dryRight) * wetValue
            }
            frame++
        }
    }

    override fun resetAudio() {
        stageOne.reset(); stageTwo.reset(); wet.reset(state.value.dryWet.coerceIn(0f, 1f))
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        ChainDeviceShell(
            title = "Filter",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(430.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledFilterSelect("Type", deviceState.type.label, FilterType.entries.map { it.label }) { label ->
                        val before = state.value
                        state.update { it.copy(type = FilterType.entries.first { type -> type.label == label }) }
                        pushStateChange(before, state.value)
                    }
                    LabeledFilterSelect("Slope", deviceState.slope.label, FilterSlope.entries.map { it.label }) { label ->
                        val before = state.value
                        state.update { it.copy(slope = FilterSlope.entries.first { slope -> slope.label == label }) }
                        pushStateChange(before, state.value)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterDial("cutoff", "Cutoff", PARAMETERS[0].normalize(deviceState.cutoffHz), "${deviceState.cutoffHz.roundToInt()} Hz") {
                        state.update { s -> s.copy(cutoffHz = PARAMETERS[0].denormalize(it)) }
                    }
                    FilterDial("resonance", "Resonance", PARAMETERS[1].normalize(deviceState.resonance), "${(deviceState.resonance * 100).roundToInt() / 100f}") {
                        state.update { s -> s.copy(resonance = PARAMETERS[1].denormalize(it)) }
                    }
                    FilterDial("dryWet", "Dry / Wet", deviceState.dryWet, "${(deviceState.dryWet * 100).roundToInt()}%") {
                        state.update { s -> s.copy(dryWet = it) }
                    }
                    FilterDial("drive", "Drive", deviceState.driveDb / 24f, "${deviceState.driveDb.roundToInt()} dB") {
                        state.update { s -> s.copy(driveDb = it * 24f) }
                    }
                }
            }
        }
    }

    companion object : ChainDeviceFactory<FilterChainDeviceState> {
        override val capabilities = setOf(DeviceCapability.AudioEffect)
        override val stateClass = FilterChainDeviceState::class
        override val serializer = FilterChainDeviceState.serializer()
        override fun create() = FilterChainDevice()
        val PARAMETERS = listOf(
            ParameterDescriptor("cutoff", "Cutoff", "Hz", 20f, 20_000f, 1_000f, ParameterScale.Logarithmic),
            ParameterDescriptor("resonance", "Resonance", "Q", 0.5f, 12f, 0.7071f),
            ParameterDescriptor("dryWet", "Dry / Wet", "%", 0f, 1f, 1f),
            ParameterDescriptor("drive", "Drive", "dB", 0f, 24f, 0f),
        )
        private val WET_SMOOTHING = ParameterSmoothing(8f)
    }
}

@Serializable
data class FilterChainDeviceState(
    val type: FilterType = FilterType.LowPass,
    val cutoffHz: Float = 1_000f,
    val resonance: Float = 0.7071f,
    val dryWet: Float = 1f,
    val slope: FilterSlope = FilterSlope.Db12,
    val driveDb: Float = 0f,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}

private fun driveSample(input: Float, driveDb: Float, gain: Float): Float =
    if (driveDb <= 0f) input else (tanh(input * gain) / tanh(gain)).takeIf(Float::isFinite) ?: 0f

private fun FilterType.toBiquad(): BiquadType = when (this) {
    FilterType.LowPass -> BiquadType.LowPass
    FilterType.HighPass -> BiquadType.HighPass
    FilterType.BandPass -> BiquadType.BandPass
    FilterType.Notch -> BiquadType.Notch
}

@Composable
private fun LabeledFilterSelect(label: String, value: String, options: List<String>, onValue: (String) -> Unit) {
    Column(Modifier.width(155.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        Select(value = value, options = options, triggerHeight = 44.dp, onValueChange = onValue)
    }
}

@Composable
private fun FilterDial(id: String, label: String, value: Float, text: String, onValue: (Float) -> Unit) {
    AutomatableDial(
        parameterId = id,
        type = DialType.Continuous,
        value = value.coerceIn(0f, 1f),
        defaultValue = 0.5f,
        title = label,
        text = text,
        onValueChange = onValue,
        isFlat = true,
    )
}
