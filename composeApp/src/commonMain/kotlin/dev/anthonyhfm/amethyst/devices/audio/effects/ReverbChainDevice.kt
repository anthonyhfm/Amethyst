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
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.roundToInt

class ReverbChainDevice : AudioChainDevice<ReverbChainDeviceState>(), ParameterOwner {
    override val state = MutableStateFlow(ReverbChainDeviceState())
    override val helpRef = "Reverb"
    override val parameterDescriptors get() = PARAMETERS
    override val tailFrames: Long
        get() = (configuration.sampleRate * (0.35 + state.value.decay * 9.65)).toLong()

    private var configuration = AudioConfiguration(44_100, 2, 128)
    private var left = ReverbChannel(configuration.sampleRate, 0)
    private var right = ReverbChannel(configuration.sampleRate, 23)
    private var preDelayLeft = FloatArray(1)
    private var preDelayRight = FloatArray(1)
    private var preDelayWrite = 0
    private val preDelayFrames = SmoothedParameter(0f)
    private val wet = SmoothedParameter(0.3f)
    private val active = atomic(false)
    private var activityEnvelope = 0f

    val isTailActive: Boolean get() = active.value

    override fun prepareAudio(configuration: AudioConfiguration) {
        this.configuration = configuration
        left = ReverbChannel(configuration.sampleRate, 0)
        right = ReverbChannel(configuration.sampleRate, 23)
        val preDelayCapacity = (configuration.sampleRate * MAX_PRE_DELAY_MS / 1_000).toInt() + 2
        preDelayLeft = FloatArray(preDelayCapacity)
        preDelayRight = FloatArray(preDelayCapacity)
        resetAudio()
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        val snapshot = state.value
        var frame = 0
        while (frame < block.frameCount) {
            val absoluteFrame = context.absoluteFrame + frame
            preDelayFrames.setTarget(
                resolveRealtimeParameter(PARAMETERS[0], snapshot.preDelayMs, absoluteFrame) * configuration.sampleRate / 1_000f,
            )
            val size = resolveRealtimeParameter(PARAMETERS[1], snapshot.size, absoluteFrame).coerceIn(0f, 1f)
            val decay = resolveRealtimeParameter(PARAMETERS[2], snapshot.decay, absoluteFrame).coerceIn(0f, 1f)
            val damping = resolveRealtimeParameter(PARAMETERS[3], snapshot.damping, absoluteFrame).coerceIn(0f, 1f)
            wet.setTarget(resolveRealtimeParameter(PARAMETERS[4], snapshot.dryWet, absoluteFrame))
            val delayedFrames = preDelayFrames.next(configuration.sampleRate, PRE_DELAY_SMOOTHING)
                .coerceIn(0f, preDelayLeft.size - 2f)
            val wetValue = wet.next(configuration.sampleRate, MIX_SMOOTHING).coerceIn(0f, 1f)
            val offset = frame * block.channels
            val dryLeft = block.samples[offset].finiteOrZero()
            val dryRight = if (block.channels > 1) block.samples[offset + 1].finiteOrZero() else dryLeft
            val delayedLeft = if (delayedFrames < 0.5f) dryLeft else readPreDelay(preDelayLeft, delayedFrames)
            val delayedRight = if (delayedFrames < 0.5f) dryRight else readPreDelay(preDelayRight, delayedFrames)
            preDelayLeft[preDelayWrite] = dryLeft
            preDelayRight[preDelayWrite] = dryRight
            preDelayWrite = (preDelayWrite + 1) % preDelayLeft.size

            val feedback = (0.22f + decay * 0.755f).coerceAtMost(0.975f)
            val roomScale = 0.45f + size * 0.55f
            val reverbedLeft = left.process(delayedLeft + delayedRight * 0.15f, feedback, damping, roomScale)
            val reverbedRight = right.process(delayedRight + delayedLeft * 0.15f, feedback, damping, roomScale)
            block.samples[offset] = (dryLeft + (reverbedLeft - dryLeft) * wetValue).finiteOrZero()
            if (block.channels > 1) {
                block.samples[offset + 1] = (dryRight + (reverbedRight - dryRight) * wetValue).finiteOrZero()
            }
            activityEnvelope = maxOf(
                maxOf(kotlin.math.abs(dryLeft), kotlin.math.abs(dryRight)),
                maxOf(kotlin.math.abs(reverbedLeft), kotlin.math.abs(reverbedRight)),
                activityEnvelope * 0.9995f,
            )
            frame++
        }
        active.value = activityEnvelope > 0.0001f
    }

    override fun resetAudio() {
        left.reset()
        right.reset()
        preDelayLeft.fill(0f)
        preDelayRight.fill(0f)
        preDelayWrite = 0
        activityEnvelope = 0f
        active.value = false
        preDelayFrames.reset(state.value.preDelayMs * configuration.sampleRate / 1_000f)
        wet.reset(state.value.dryWet.coerceIn(0f, 1f))
    }

    private fun readPreDelay(buffer: FloatArray, frames: Float): Float {
        var position = preDelayWrite - frames
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
            title = "Reverb",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(470.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    EffectDial("preDelay", "Pre-delay", deviceState.preDelayMs / MAX_PRE_DELAY_MS, "${deviceState.preDelayMs.roundToInt()} ms") {
                        state.update { s -> s.copy(preDelayMs = it * MAX_PRE_DELAY_MS) }
                    }
                    EffectDial("size", "Size", deviceState.size, "${(deviceState.size * 100).roundToInt()}%") {
                        state.update { s -> s.copy(size = it) }
                    }
                    EffectDial("decay", "Decay", deviceState.decay, "${formatDecay(deviceState.decay)} s") {
                        state.update { s -> s.copy(decay = it) }
                    }
                    EffectDial("damping", "Damping", deviceState.damping, "${(deviceState.damping * 100).roundToInt()}%") {
                        state.update { s -> s.copy(damping = it) }
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

    companion object : ChainDeviceFactory<ReverbChainDeviceState> {
        override val capabilities = setOf(DeviceCapability.AudioEffect)
        override val stateClass = ReverbChainDeviceState::class
        override val serializer = ReverbChainDeviceState.serializer()
        override fun create() = ReverbChainDevice()
        val PARAMETERS = listOf(
            ParameterDescriptor("preDelay", "Pre-delay", "ms", 0f, MAX_PRE_DELAY_MS, 15f),
            ParameterDescriptor("size", "Size", "%", 0f, 1f, 0.55f),
            ParameterDescriptor("decay", "Decay", "%", 0f, 1f, 0.55f),
            ParameterDescriptor("damping", "Damping", "%", 0f, 1f, 0.45f),
            ParameterDescriptor("dryWet", "Dry / Wet", "%", 0f, 1f, 0.3f),
        )
        const val MAX_PRE_DELAY_MS = 250f
        private val PRE_DELAY_SMOOTHING = ParameterSmoothing(15f)
        private val MIX_SMOOTHING = ParameterSmoothing(8f)
    }
}

@Serializable
data class ReverbChainDeviceState(
    val preDelayMs: Float = 15f,
    val size: Float = 0.55f,
    val decay: Float = 0.55f,
    val damping: Float = 0.45f,
    val dryWet: Float = 0.3f,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}

private class ReverbChannel(sampleRate: Int, stereoOffset: Int) {
    private val combs = intArrayOf(1116, 1188, 1277, 1356).map { tuning ->
        ReverbComb(((tuning + stereoOffset) * sampleRate / 44_100.0 * 1.7).toInt().coerceAtLeast(8))
    }
    private val allPasses = intArrayOf(556, 441).map { tuning ->
        ReverbAllPass(((tuning + stereoOffset) * sampleRate / 44_100.0 * 1.7).toInt().coerceAtLeast(8))
    }

    fun process(input: Float, feedback: Float, damping: Float, roomScale: Float): Float {
        var sum = 0f
        combs.forEach { sum += it.process(input, feedback, damping, roomScale) }
        var output = sum / combs.size
        allPasses.forEach { output = it.process(output, roomScale) }
        return output.finiteOrZero()
    }

    fun reset() {
        combs.forEach(ReverbComb::reset)
        allPasses.forEach(ReverbAllPass::reset)
    }
}

private class ReverbComb(maximumLength: Int) {
    private val buffer = FloatArray(maximumLength)
    private var index = 0
    private var damped = 0f

    fun process(input: Float, feedback: Float, damping: Float, roomScale: Float): Float {
        val length = (buffer.size * roomScale).roundToInt().coerceIn(2, buffer.size)
        if (index >= length) index = 0
        val output = buffer[index]
        damped = output * (1f - damping * 0.9f) + damped * (damping * 0.9f)
        buffer[index] = (input + damped * feedback).coerceIn(-4f, 4f).finiteOrZero()
        index++
        if (index >= length) index = 0
        return output
    }

    fun reset() {
        buffer.fill(0f)
        index = 0
        damped = 0f
    }
}

private class ReverbAllPass(maximumLength: Int) {
    private val buffer = FloatArray(maximumLength)
    private var index = 0

    fun process(input: Float, roomScale: Float): Float {
        val length = (buffer.size * roomScale).roundToInt().coerceIn(2, buffer.size)
        if (index >= length) index = 0
        val buffered = buffer[index]
        val output = -input + buffered
        buffer[index] = (input + buffered * 0.5f).finiteOrZero()
        index++
        if (index >= length) index = 0
        return output
    }

    fun reset() {
        buffer.fill(0f)
        index = 0
    }
}

private fun formatDecay(value: Float): String = ((0.35f + value * 9.65f) * 10f).roundToInt().div(10f).toString()
