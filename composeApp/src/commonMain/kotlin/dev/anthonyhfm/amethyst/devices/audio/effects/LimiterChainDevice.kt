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
import dev.anthonyhfm.amethyst.core.engine.audio.dsp.StereoLinkedLookaheadLimiter
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
import kotlin.math.pow

/** Per-chain Ableton limiter using the same linked lookahead DSP as the master safety limiter. */
class LimiterChainDevice : AudioChainDevice<LimiterChainDeviceState>() {
    override val state = MutableStateFlow(LimiterChainDeviceState())
    override val helpRef = "Limiter"

    private var configuration = AudioConfiguration(44_100, 2, 128)
    private var appliedCeilingDb = Float.NaN
    private var appliedReleaseMs = Float.NaN
    private var appliedLookaheadMs = Float.NaN
    private var limiter = createLimiter(-0.3f, 300f, 3f)
    private var monoScratch = FloatArray(configuration.periodFrames * 2)

    override val latencyFrames: Int get() = limiter.lookaheadFrames
    override val tailFrames: Long get() = limiter.lookaheadFrames.toLong()
    override fun prepareAudio(configuration: AudioConfiguration) {
        this.configuration = configuration
        monoScratch = FloatArray(configuration.maximumBlockFrames * 2)
        appliedCeilingDb = Float.NaN
        appliedReleaseMs = Float.NaN
        appliedLookaheadMs = Float.NaN
        ensureLimiter()
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        ensureLimiter()
        val inputGain = 10.0.pow(state.value.inputGainDb.coerceFiniteLimiter(0f) / 20.0).toFloat()
        val sampleCount = block.frameCount * block.channels
        var index = 0
        while (index < sampleCount) {
            block.samples[index] = (block.samples[index] * inputGain).finiteLimiterAudio()
            index++
        }

        if (block.channels == 2) {
            limiter.processInterleaved(block.samples, block.frameCount)
        } else if (block.channels == 1) {
            if (monoScratch.size < block.frameCount * 2) monoScratch = FloatArray(block.frameCount * 2)
            var frame = 0
            while (frame < block.frameCount) {
                val value = block.samples[frame]
                monoScratch[frame * 2] = value
                monoScratch[frame * 2 + 1] = value
                frame++
            }
            limiter.processInterleaved(monoScratch, block.frameCount)
            frame = 0
            while (frame < block.frameCount) {
                block.samples[frame] = monoScratch[frame * 2]
                frame++
            }
        }
    }

    override fun resetAudio() = limiter.reset()

    @Composable
    override fun Content() {
        val snapshot by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        ChainDeviceShell(
            title = "Limiter",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(200.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(Modifier.padding(10.dp)) {
                Text("Ceiling ${snapshot.ceilingDb} dB")
                Text("Lookahead ${snapshot.lookaheadMs} ms")
            }
        }
    }

    private fun ensureLimiter() {
        val snapshot = state.value
        val ceilingDb = snapshot.ceilingDb.coerceFiniteLimiter(-0.3f).coerceAtMost(0f)
        val releaseMs = snapshot.releaseMs.coerceFiniteLimiter(300f).coerceAtLeast(0.1f)
        val lookaheadMs = snapshot.lookaheadMs.coerceFiniteLimiter(3f).coerceAtLeast(0f)
        if (ceilingDb == appliedCeilingDb && releaseMs == appliedReleaseMs &&
            lookaheadMs == appliedLookaheadMs
        ) return
        limiter = createLimiter(ceilingDb, releaseMs, lookaheadMs)
            .also { it.prepare(configuration.sampleRate) }
        appliedCeilingDb = ceilingDb
        appliedReleaseMs = releaseMs
        appliedLookaheadMs = lookaheadMs
    }

    private fun createLimiter(
        ceilingDb: Float,
        releaseMs: Float,
        lookaheadMs: Float,
    ) = StereoLinkedLookaheadLimiter(
        lookaheadMillis = lookaheadMs.toDouble(),
        ceilingDb = ceilingDb,
        releaseMillis = releaseMs.toDouble(),
    )

    companion object : ChainDeviceFactory<LimiterChainDeviceState> {
        override val capabilities = setOf(DeviceCapability.AudioEffect)
        override val stateClass = LimiterChainDeviceState::class
        override val serializer = LimiterChainDeviceState.serializer()
        override fun create() = LimiterChainDevice()
    }
}

@Serializable
data class LimiterChainDeviceState(
    val inputGainDb: Float = 0f,
    val ceilingDb: Float = -0.3f,
    val releaseMs: Float = 300f,
    val lookaheadMs: Float = 3f,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}

private fun Float.coerceFiniteLimiter(fallback: Float): Float = if (isFinite()) this else fallback
private fun Float.finiteLimiterAudio(): Float = if (isFinite()) this else 0f
