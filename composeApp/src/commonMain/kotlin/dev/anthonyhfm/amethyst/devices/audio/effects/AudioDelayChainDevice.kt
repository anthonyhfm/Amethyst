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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
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
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

@Serializable
enum class AudioDelayTimeMode(val label: String) { Milliseconds("ms"), Sync("Sync") }

@Serializable
enum class AudioDelayStereoMode(val label: String) { Stereo("Stereo"), PingPong("Ping Pong") }

@Serializable
enum class AudioDelayNoteValue(val label: String, val beats: Double) {
    Sixteenth("1/16", 0.25), Eighth("1/8", 0.5), Quarter("1/4", 1.0), Half("1/2", 2.0)
}

class AudioDelayChainDevice : AudioChainDevice<AudioDelayChainDeviceState>(), ParameterOwner {
    override val state = MutableStateFlow(AudioDelayChainDeviceState())
    override val helpRef = "Audio Delay"
    override val parameterDescriptors get() = PARAMETERS
    override val tailFrames: Long
        get() = (state.value.resolvedTimeMs(WorkspaceRepository.bpm.value) * 0.001 * configuration.sampleRate * 12.0)
            .toLong().coerceAtLeast(0L)

    private var configuration = AudioConfiguration(44_100, 2, 128)
    private var leftDelay = FloatArray(1)
    private var rightDelay = FloatArray(1)
    private var writeIndex = 0
    private var filteredFeedbackLeft = 0f
    private var filteredFeedbackRight = 0f
    private val delayFrames = SmoothedParameter(1f)
    private val wet = SmoothedParameter(0.35f)
    private val active = atomic(false)
    private var activityEnvelope = 0f

    val isTailActive: Boolean get() = active.value

    override fun prepareAudio(configuration: AudioConfiguration) {
        this.configuration = configuration
        val capacity = configuration.sampleRate * MAX_DELAY_SECONDS + configuration.maximumBlockFrames + 2
        leftDelay = FloatArray(capacity)
        rightDelay = FloatArray(capacity)
        resetAudio()
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        val snapshot = state.value
        val bpm = WorkspaceRepository.bpm.value
        val selectedTimeMs = snapshot.resolvedTimeMs(bpm)
        var frame = 0
        while (frame < block.frameCount) {
            val absoluteFrame = context.absoluteFrame + frame
            val automatedTime = if (snapshot.timeMode == AudioDelayTimeMode.Milliseconds) {
                resolveRealtimeParameter(PARAMETERS[0], selectedTimeMs, absoluteFrame)
            } else selectedTimeMs
            delayFrames.setTarget((automatedTime * configuration.sampleRate / 1_000f).coerceIn(1f, leftDelay.size - 2f))
            wet.setTarget(resolveRealtimeParameter(PARAMETERS[2], snapshot.dryWet, absoluteFrame))
            val feedback = resolveRealtimeParameter(PARAMETERS[1], snapshot.feedback, absoluteFrame).coerceIn(0f, 0.98f)
            val filterHz = resolveRealtimeParameter(PARAMETERS[3], snapshot.filterHz, absoluteFrame)
                .coerceIn(100f, configuration.sampleRate * 0.45f)
            val currentDelay = delayFrames.next(configuration.sampleRate, TIME_SMOOTHING)
            val wetValue = wet.next(configuration.sampleRate, MIX_SMOOTHING).coerceIn(0f, 1f)
            val delayedLeft = readDelay(leftDelay, currentDelay)
            val delayedRight = readDelay(rightDelay, currentDelay)
            val coefficient = exp(-2.0 * PI * filterHz / configuration.sampleRate).toFloat()
            filteredFeedbackLeft = delayedLeft * (1f - coefficient) + filteredFeedbackLeft * coefficient
            filteredFeedbackRight = delayedRight * (1f - coefficient) + filteredFeedbackRight * coefficient

            val offset = frame * block.channels
            val dryLeft = block.samples[offset].finiteOrZero()
            val dryRight = if (block.channels > 1) block.samples[offset + 1].finiteOrZero() else dryLeft
            val feedbackLeft = if (snapshot.stereoMode == AudioDelayStereoMode.PingPong) {
                filteredFeedbackRight
            } else filteredFeedbackLeft
            val feedbackRight = if (snapshot.stereoMode == AudioDelayStereoMode.PingPong) {
                filteredFeedbackLeft
            } else filteredFeedbackRight
            leftDelay[writeIndex] = (dryLeft + feedbackLeft * feedback).coerceIn(-4f, 4f).finiteOrZero()
            rightDelay[writeIndex] = (dryRight + feedbackRight * feedback).coerceIn(-4f, 4f).finiteOrZero()
            block.samples[offset] = (dryLeft + (delayedLeft - dryLeft) * wetValue).finiteOrZero()
            if (block.channels > 1) {
                block.samples[offset + 1] = (dryRight + (delayedRight - dryRight) * wetValue).finiteOrZero()
            }
            activityEnvelope = maxOf(
                maxOf(kotlin.math.abs(dryLeft), kotlin.math.abs(dryRight)),
                maxOf(kotlin.math.abs(delayedLeft), kotlin.math.abs(delayedRight)),
                activityEnvelope * 0.9995f,
            )
            writeIndex = (writeIndex + 1) % leftDelay.size
            frame++
        }
        active.value = activityEnvelope > 0.0001f
    }

    override fun resetAudio() {
        leftDelay.fill(0f)
        rightDelay.fill(0f)
        writeIndex = 0
        filteredFeedbackLeft = 0f
        filteredFeedbackRight = 0f
        activityEnvelope = 0f
        active.value = false
        val snapshot = state.value
        delayFrames.reset((snapshot.resolvedTimeMs(WorkspaceRepository.bpm.value) * configuration.sampleRate / 1_000f).coerceAtLeast(1f))
        wet.reset(snapshot.dryWet.coerceIn(0f, 1f))
    }

    private fun readDelay(buffer: FloatArray, frames: Float): Float {
        var position = writeIndex - frames
        while (position < 0f) position += buffer.size
        val first = floor(position).toInt() % buffer.size
        val second = (first + 1) % buffer.size
        val fraction = position - floor(position)
        return buffer[first] + (buffer[second] - buffer[first]) * fraction
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val tail by produceState(initialValue = isTailActive) {
            while (true) {
                value = isTailActive
                delay(50)
            }
        }
        ChainDeviceShell(
            title = "Delay",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(470.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DelaySelect("Time", deviceState.timeMode.label, AudioDelayTimeMode.entries.map { it.label }) { label ->
                        changeState { copy(timeMode = AudioDelayTimeMode.entries.first { it.label == label }) }
                    }
                    DelaySelect("Stereo", deviceState.stereoMode.label, AudioDelayStereoMode.entries.map { it.label }) { label ->
                        changeState { copy(stereoMode = AudioDelayStereoMode.entries.first { it.label == label }) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val timeValue = if (deviceState.timeMode == AudioDelayTimeMode.Sync) {
                        deviceState.noteValue.ordinal.toFloat() / (AudioDelayNoteValue.entries.size - 1)
                    } else PARAMETERS[0].normalize(deviceState.timeMs)
                    val timeText = if (deviceState.timeMode == AudioDelayTimeMode.Sync) {
                        deviceState.noteValue.label
                    } else "${deviceState.timeMs.roundToInt()} ms"
                    EffectDial("timeMs", "Time", timeValue, timeText) { normalized ->
                        state.update { current ->
                            if (current.timeMode == AudioDelayTimeMode.Sync) {
                                val index = (normalized * (AudioDelayNoteValue.entries.size - 1)).roundToInt()
                                    .coerceIn(AudioDelayNoteValue.entries.indices)
                                current.copy(noteValue = AudioDelayNoteValue.entries[index])
                            } else {
                                current.copy(timeMs = PARAMETERS[0].denormalize(normalized))
                            }
                        }
                    }
                    EffectDial("feedback", "Feedback", deviceState.feedback, "${(deviceState.feedback * 100).roundToInt()}%") {
                        state.update { s -> s.copy(feedback = it) }
                    }
                    EffectDial("filter", "Filter", PARAMETERS[3].normalize(deviceState.filterHz), "${deviceState.filterHz.roundToInt()} Hz") {
                        state.update { s -> s.copy(filterHz = PARAMETERS[3].denormalize(it)) }
                    }
                    EffectDial("dryWet", "Dry / Wet", deviceState.dryWet, "${(deviceState.dryWet * 100).roundToInt()}%") {
                        state.update { s -> s.copy(dryWet = it) }
                    }
                }
                Text(
                    if (tail) "Tail active" else "Tail idle",
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                )
            }
        }
    }

    private fun changeState(change: AudioDelayChainDeviceState.() -> AudioDelayChainDeviceState) {
        val before = state.value
        state.value = before.change()
        pushStateChange(before, state.value)
    }

    companion object : ChainDeviceFactory<AudioDelayChainDeviceState> {
        override val capabilities = setOf(DeviceCapability.AudioEffect)
        override val stateClass = AudioDelayChainDeviceState::class
        override val serializer = AudioDelayChainDeviceState.serializer()
        override fun create() = AudioDelayChainDevice()
        val PARAMETERS = listOf(
            ParameterDescriptor("timeMs", "Time", "ms", 1f, 2_000f, 350f, ParameterScale.Logarithmic),
            ParameterDescriptor("feedback", "Feedback", "%", 0f, 0.98f, 0.35f),
            ParameterDescriptor("dryWet", "Dry / Wet", "%", 0f, 1f, 0.35f),
            ParameterDescriptor("filter", "Feedback Filter", "Hz", 100f, 20_000f, 8_000f, ParameterScale.Logarithmic),
        )
        private const val MAX_DELAY_SECONDS = 8
        private val TIME_SMOOTHING = ParameterSmoothing(20f)
        private val MIX_SMOOTHING = ParameterSmoothing(8f)
    }
}

@Serializable
data class AudioDelayChainDeviceState(
    val timeMode: AudioDelayTimeMode = AudioDelayTimeMode.Milliseconds,
    val timeMs: Float = 350f,
    val noteValue: AudioDelayNoteValue = AudioDelayNoteValue.Quarter,
    val feedback: Float = 0.35f,
    val dryWet: Float = 0.35f,
    val filterHz: Float = 8_000f,
    val stereoMode: AudioDelayStereoMode = AudioDelayStereoMode.Stereo,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    fun resolvedTimeMs(bpm: Double): Float = if (timeMode == AudioDelayTimeMode.Sync) {
        (60_000.0 / bpm.coerceAtLeast(1.0) * noteValue.beats).toFloat()
    } else timeMs

    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}

@Composable
internal fun EffectDial(id: String, title: String, value: Float, text: String, onValue: (Float) -> Unit) {
    AutomatableDial(
        parameterId = id,
        type = DialType.Continuous,
        value = value.coerceIn(0f, 1f),
        defaultValue = 0.5f,
        title = title,
        text = text,
        onValueChange = onValue,
        isFlat = false,
    )
}

@Composable
private fun DelaySelect(label: String, value: String, options: List<String>, onValue: (String) -> Unit) {
    Column(Modifier.width(138.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Theme[typography][small])
        Select(value = value, options = options, triggerHeight = 32.dp, onValueChange = onValue)
    }
}

internal fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f
