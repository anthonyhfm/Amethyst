package dev.anthonyhfm.amethyst.devices.audio.effects

import androidx.compose.foundation.layout.Column
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
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceCapability
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Eight-band parametric EQ compatible with Ableton EQ Eight's serialized filter modes. */
class EqEightChainDevice : AudioChainDevice<EqEightChainDeviceState>() {
    override val state = MutableStateFlow(EqEightChainDeviceState())
    override val helpRef = "EQ Eight"

    private var configuration = AudioConfiguration(44_100, 2, 128)
    private val bankA = Array(BAND_COUNT) { EqBandProcessor() }
    private val bankB = Array(BAND_COUNT) { EqBandProcessor() }

    override fun prepareAudio(configuration: AudioConfiguration) {
        this.configuration = configuration
        resetAudio()
        val snapshot = state.value
        configureBank(bankA, snapshot.bandsA, snapshot.scale)
        configureBank(bankB, snapshot.bandsB, snapshot.scale)
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        val snapshot = state.value
        configureBank(bankA, snapshot.bandsA, snapshot.scale)
        configureBank(bankB, snapshot.bandsB, snapshot.scale)
        val outputGain = dbToLinear(snapshot.globalGainDb)

        var frame = 0
        while (frame < block.frameCount) {
            val offset = frame * block.channels
            if (block.channels == 1) {
                block.samples[offset] = processBankLeft(bankA, block.samples[offset]) * outputGain
            } else {
                var left = block.samples[offset]
                var right = block.samples[offset + 1]
                when (snapshot.channelMode.coerceIn(0, 2)) {
                    0 -> {
                        left = processBankLeft(bankA, left)
                        right = processBankRight(bankA, right)
                    }
                    1 -> {
                        left = processBankLeft(bankA, left)
                        right = processBankRight(bankB, right)
                    }
                    else -> {
                        val mid = (left + right) * 0.5f
                        val side = (left - right) * 0.5f
                        val processedMid = processBankLeft(bankA, mid)
                        val processedSide = processBankLeft(bankB, side)
                        left = processedMid + processedSide
                        right = processedMid - processedSide
                    }
                }
                block.samples[offset] = (left * outputGain).finiteEqAudio()
                block.samples[offset + 1] = (right * outputGain).finiteEqAudio()
            }
            frame++
        }
    }

    override fun resetAudio() {
        bankA.forEach(EqBandProcessor::reset)
        bankB.forEach(EqBandProcessor::reset)
    }

    @Composable
    override fun Content() {
        val snapshot by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        ChainDeviceShell(
            title = "EQ Eight",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(220.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(Modifier.padding(10.dp)) {
                Text("${snapshot.bandsA.count { it.enabled }} active bands")
                Text("Output ${snapshot.globalGainDb} dB")
            }
        }
    }

    private fun configureBank(bank: Array<EqBandProcessor>, bands: List<EqEightBandState>, scale: Float) {
        var index = 0
        while (index < BAND_COUNT) {
            bank[index].configure(
                band = if (index < bands.size) bands[index] else DISABLED_EQ_BAND,
                gainScale = scale,
                sampleRate = configuration.sampleRate,
            )
            index++
        }
    }

    companion object : ChainDeviceFactory<EqEightChainDeviceState> {
        override val capabilities = setOf(DeviceCapability.AudioEffect)
        override val stateClass = EqEightChainDeviceState::class
        override val serializer = EqEightChainDeviceState.serializer()
        override fun create() = EqEightChainDevice()
        private const val BAND_COUNT = 8
    }
}

@Serializable
data class EqEightBandState(
    val enabled: Boolean = false,
    /** 0 LC48, 1 LC12, 2 low shelf, 3 bell, 4 notch, 5 high shelf, 6 HC12, 7 HC48. */
    val mode: Int = 3,
    val frequencyHz: Float = 1_000f,
    val gainDb: Float = 0f,
    val q: Float = 0.70710677f,
)

@Serializable
data class EqEightChainDeviceState(
    val bandsA: List<EqEightBandState> = List(8) { EqEightBandState() },
    val bandsB: List<EqEightBandState> = List(8) { EqEightBandState() },
    /** 0 stereo, 1 left/right and 2 mid/side. */
    val channelMode: Int = 0,
    val globalGainDb: Float = 0f,
    val scale: Float = 1f,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}

private class EqBandProcessor {
    private val stages = Array(4) { EqBiquad() }
    private var enabled = false
    private var stageCount = 1
    private var lastMode = Int.MIN_VALUE
    private var lastFrequency = Float.NaN
    private var lastGain = Float.NaN
    private var lastQ = Float.NaN
    private var lastSampleRate = 0

    fun configure(band: EqEightBandState, gainScale: Float, sampleRate: Int) {
        if (enabled && !band.enabled) reset()
        enabled = band.enabled
        stageCount = if (band.mode == 0 || band.mode == 7) 4 else 1
        if (!enabled) return
        val gain = band.gainDb.takeIf(Float::isFinite)?.times(gainScale.takeIf(Float::isFinite) ?: 1f) ?: 0f
        val frequency = band.frequencyHz.takeIf(Float::isFinite) ?: 1_000f
        val q = band.q.takeIf(Float::isFinite) ?: 0.70710677f
        if (band.mode == lastMode && frequency == lastFrequency && gain == lastGain &&
            q == lastQ && sampleRate == lastSampleRate
        ) return
        lastMode = band.mode
        lastFrequency = frequency
        lastGain = gain
        lastQ = q
        lastSampleRate = sampleRate
        var stage = 0
        while (stage < stageCount) {
            val stageQ = if (stageCount == 4) {
                q * STEEP_FILTER_Q[stage] / STEEP_FILTER_Q[0]
            } else {
                q
            }
            stages[stage].configure(band.mode, frequency, gain, stageQ, sampleRate)
            stage++
        }
    }

    fun processLeft(input: Float): Float {
        if (!enabled) return input
        var value = input
        var stage = 0
        while (stage < stageCount) {
            value = stages[stage].processLeft(value)
            stage++
        }
        return value
    }

    fun processRight(input: Float): Float {
        if (!enabled) return input
        var value = input
        var stage = 0
        while (stage < stageCount) {
            value = stages[stage].processRight(value)
            stage++
        }
        return value
    }

    fun reset() = stages.forEach(EqBiquad::reset)
}

/** RBJ cookbook biquad used privately so the existing general filter remains unchanged. */
private class EqBiquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1Left = 0.0
    private var z2Left = 0.0
    private var z1Right = 0.0
    private var z2Right = 0.0

    fun configure(mode: Int, frequencyHz: Float, gainDb: Float, q: Float, sampleRate: Int) {
        val frequency = frequencyHz.takeIf(Float::isFinite)?.coerceIn(10f, sampleRate * 0.49f) ?: 1_000f
        val safeQ = q.takeIf(Float::isFinite)?.coerceIn(0.1f, 18f) ?: 0.70710677f
        val omega = 2.0 * PI * frequency / sampleRate
        val cosine = cos(omega)
        val sine = sin(omega)
        val alpha = sine / (2.0 * safeQ)
        val amplitude = 10.0.pow(gainDb.coerceIn(-48f, 48f) / 40.0)
        val raw = when (mode) {
            0, 1 -> highPass(cosine, alpha)
            2 -> lowShelf(cosine, sine, amplitude, safeQ)
            3 -> bell(cosine, alpha, amplitude)
            4 -> notch(cosine, alpha)
            5 -> highShelf(cosine, sine, amplitude, safeQ)
            6, 7 -> lowPass(cosine, alpha)
            else -> doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        }
        setNormalized(raw)
    }

    fun processLeft(input: Float): Float {
        val output = b0 * input + z1Left
        z1Left = b1 * input - a1 * output + z2Left
        z2Left = b2 * input - a2 * output
        return output.toFloat().finiteEqAudio()
    }

    fun processRight(input: Float): Float {
        val output = b0 * input + z1Right
        z1Right = b1 * input - a1 * output + z2Right
        z2Right = b2 * input - a2 * output
        return output.toFloat().finiteEqAudio()
    }

    fun reset() {
        z1Left = 0.0; z2Left = 0.0; z1Right = 0.0; z2Right = 0.0
    }

    private fun setNormalized(raw: DoubleArray) {
        val a0 = raw[3]
        val values = doubleArrayOf(raw[0] / a0, raw[1] / a0, raw[2] / a0, raw[4] / a0, raw[5] / a0)
        if (values.all(Double::isFinite)) {
            b0 = values[0]; b1 = values[1]; b2 = values[2]; a1 = values[3]; a2 = values[4]
        } else {
            b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
        }
    }

    private fun lowPass(c: Double, a: Double) =
        doubleArrayOf((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + a, -2 * c, 1 - a)

    private fun highPass(c: Double, a: Double) =
        doubleArrayOf((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + a, -2 * c, 1 - a)

    private fun notch(c: Double, a: Double) =
        doubleArrayOf(1.0, -2 * c, 1.0, 1 + a, -2 * c, 1 - a)

    private fun bell(c: Double, a: Double, gain: Double) =
        doubleArrayOf(1 + a * gain, -2 * c, 1 - a * gain, 1 + a / gain, -2 * c, 1 - a / gain)

    private fun lowShelf(c: Double, s: Double, gain: Double, q: Float): DoubleArray {
        val alpha = s / (2.0 * q)
        val beta = 2.0 * sqrt(gain) * alpha
        return doubleArrayOf(
            gain * ((gain + 1) - (gain - 1) * c + beta),
            2 * gain * ((gain - 1) - (gain + 1) * c),
            gain * ((gain + 1) - (gain - 1) * c - beta),
            (gain + 1) + (gain - 1) * c + beta,
            -2 * ((gain - 1) + (gain + 1) * c),
            (gain + 1) + (gain - 1) * c - beta,
        )
    }

    private fun highShelf(c: Double, s: Double, gain: Double, q: Float): DoubleArray {
        val alpha = s / (2.0 * q)
        val beta = 2.0 * sqrt(gain) * alpha
        return doubleArrayOf(
            gain * ((gain + 1) + (gain - 1) * c + beta),
            -2 * gain * ((gain - 1) + (gain + 1) * c),
            gain * ((gain + 1) + (gain - 1) * c - beta),
            (gain + 1) - (gain - 1) * c + beta,
            2 * ((gain - 1) - (gain + 1) * c),
            (gain + 1) - (gain - 1) * c - beta,
        )
    }
}

private fun processBankLeft(bank: Array<EqBandProcessor>, input: Float): Float {
    var value = input
    var index = 0
    while (index < bank.size) {
        value = bank[index].processLeft(value)
        index++
    }
    return value
}

private fun processBankRight(bank: Array<EqBandProcessor>, input: Float): Float {
    var value = input
    var index = 0
    while (index < bank.size) {
        value = bank[index].processRight(value)
        index++
    }
    return value
}

private fun dbToLinear(db: Float): Float = 10.0.pow((db.takeIf(Float::isFinite) ?: 0f) / 20.0).toFloat()

private fun Float.finiteEqAudio(): Float = if (isFinite()) this else 0f
private val DISABLED_EQ_BAND = EqEightBandState()
/** Eighth-order Butterworth pole pairs; their product is -3 dB at the cutoff. */
private val STEEP_FILTER_Q = floatArrayOf(0.5097956f, 0.6013449f, 0.8999762f, 2.5629154f)
