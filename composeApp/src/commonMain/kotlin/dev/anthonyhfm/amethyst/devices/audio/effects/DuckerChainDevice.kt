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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.parameter.ParameterDescriptor
import dev.anthonyhfm.amethyst.core.parameter.ParameterOwner
import dev.anthonyhfm.amethyst.core.parameter.ParameterScale
import dev.anthonyhfm.amethyst.core.parameter.resolveRealtimeParameter
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.devices.AudioRenderContext
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceCapability
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.devices.SidechainAudioConsumer
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.atomicfu.atomic
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

class DuckerChainDevice : AudioChainDevice<DuckerChainDeviceState>(), ParameterOwner, SidechainAudioConsumer {
    override val state = MutableStateFlow(DuckerChainDeviceState())
    // Keep the persisted Kotlin type names stable for backwards compatibility;
    // the device is presented to users as Compressor everywhere in the UI.
    override val helpRef = "Compressor"
    override val parameterDescriptors get() = PARAMETERS
    override val sidechainSourceId: String? get() = state.value.sidechainSourceId

    private var configuration = AudioConfiguration(44_100, 2, 128)
    private var reduction = 0f
    private var compressorReductionDb = 0f
    private var publishedGainReduction = 0f
    private val eligibleSourceIds = atomic(emptySet<String>())
    private var detectorBlocks = arrayOfNulls<AudioProcessingBlock>(MAX_DETECTOR_INPUTS)
    private var detectorBlockCount = 0
    private var delayLeft = FloatArray(1)
    private var delayRight = FloatArray(1)
    private var delayWriteIndex = 0

    val currentGainReduction: Float get() = publishedGainReduction.coerceIn(0f, 1f)

    override val latencyFrames: Int
        get() = lookaheadFrames(state.value.lookaheadMs)

    override fun replaceEligibleSidechainSources(sourceIds: Set<String>) {
        eligibleSourceIds.value = sourceIds.toSet()
    }

    override fun prepareAudio(configuration: AudioConfiguration) {
        this.configuration = configuration
        val maximumLookaheadFrames = lookaheadFrames(MAX_LOOKAHEAD_MS)
        delayLeft = FloatArray(maximumLookaheadFrames + 1)
        delayRight = FloatArray(maximumLookaheadFrames + 1)
        resetAudio()
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        val snapshot = state.value
        collectDetectorBlocks(snapshot, context)
        val thresholdDb = snapshot.thresholdDb
        val compressorMode = thresholdDb != null
        val lookaheadFrames = if (compressorMode) lookaheadFrames(snapshot.lookaheadMs) else 0
        val detectorGain = dbToLinear(snapshot.detectorGainDb)
        val makeupGain = dbToLinear(snapshot.makeupDb)
        val dryWet = snapshot.dryWet.coerceIn(0f, 1f)
        var frame = 0
        while (frame < block.frameCount) {
            val absoluteFrame = context.absoluteFrame + frame
            val attackMs = resolveRealtimeParameter(PARAMETERS[0], snapshot.attackMs, absoluteFrame).coerceAtLeast(0f)
            val releaseMs = resolveRealtimeParameter(PARAMETERS[1], snapshot.releaseMs, absoluteFrame).coerceAtLeast(0f)
            val strength = resolveRealtimeParameter(PARAMETERS[2], snapshot.strength, absoluteFrame).coerceIn(0f, 1f)
            val detector = detectorPeakAt(frame) * detectorGain
            val gain = if (thresholdDb != null) {
                val targetReductionDb = compressorGainReductionDb(
                    detector = detector,
                    thresholdDb = thresholdDb,
                    ratio = snapshot.ratio,
                    kneeDb = snapshot.kneeDb,
                )
                val smoothingMs = if (targetReductionDb > compressorReductionDb) attackMs else releaseMs
                compressorReductionDb = smoothValue(
                    current = compressorReductionDb,
                    target = targetReductionDb,
                    milliseconds = smoothingMs,
                    sampleRate = configuration.sampleRate,
                ).coerceAtLeast(0f)
                val compressedGain = dbToLinear(-compressorReductionDb * strength) * makeupGain
                val mixedGain = (1f - dryWet) + dryWet * compressedGain
                publishedGainReduction = (1f - dbToLinear(-compressorReductionDb * strength)).coerceIn(0f, 1f)
                mixedGain.finiteOrOne()
            } else {
                val legacyDetector = detector.coerceIn(0f, 1f)
                val smoothingMs = if (legacyDetector > reduction) attackMs else releaseMs.coerceAtLeast(1f)
                reduction = smoothEnvelope(reduction, legacyDetector, smoothingMs, configuration.sampleRate)
                publishedGainReduction = (reduction * strength).coerceIn(0f, 1f)
                (1f - publishedGainReduction).coerceIn(0f, 1f)
            }
            val offset = frame * block.channels
            val inputLeft = block.samples[offset]
            val inputRight = if (block.channels > 1) block.samples[offset + 1] else inputLeft
            val delayedLeft: Float
            val delayedRight: Float
            if (lookaheadFrames <= 0) {
                delayedLeft = inputLeft
                delayedRight = inputRight
            } else {
                val delayFrames = lookaheadFrames.coerceAtMost(delayLeft.lastIndex)
                val readIndex = (delayWriteIndex - delayFrames).floorMod(delayLeft.size)
                delayedLeft = delayLeft[readIndex]
                delayedRight = delayRight[readIndex]
                delayLeft[delayWriteIndex] = inputLeft
                delayRight[delayWriteIndex] = inputRight
                delayWriteIndex++
                if (delayWriteIndex == delayLeft.size) delayWriteIndex = 0
            }
            var channel = 0
            while (channel < block.channels) {
                val delayedSample = if (channel == 0) delayedLeft else delayedRight
                block.samples[offset + channel] = (delayedSample * gain).finiteOrZero()
                channel++
            }
            frame++
        }
    }

    override fun resetAudio() {
        reduction = 0f
        compressorReductionDb = 0f
        publishedGainReduction = 0f
        detectorBlockCount = 0
        detectorBlocks.fill(null)
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayWriteIndex = 0
    }

    private fun collectDetectorBlocks(snapshot: DuckerChainDeviceState, context: AudioRenderContext) {
        detectorBlockCount = 0
        snapshot.sidechainBusId?.takeIf(String::isNotBlank)?.let { busId ->
            val busInputs = context.sidechainInputs(busId)
            var index = 0
            while (index < busInputs.size) {
                addDetectorBlock(busInputs[index])
                index++
            }
            clearUnusedDetectorBlocks()
            return
        }

        val eligibleIds = eligibleSourceIds.value
        var sourceIndex = 0
        while (sourceIndex < snapshot.sidechainSourceIds.size) {
            val sourceId = snapshot.sidechainSourceIds[sourceIndex]
            if (sourceId in eligibleIds) {
                val input = context.sidechainInput(sourceId)
                if (input != null) addDetectorBlock(input)
            }
            sourceIndex++
        }
        val legacySourceId = snapshot.sidechainSourceId
        if (legacySourceId != null &&
            legacySourceId in eligibleIds &&
            legacySourceId !in snapshot.sidechainSourceIds
        ) {
            val input = context.sidechainInput(legacySourceId)
            if (input != null) addDetectorBlock(input)
        }

        clearUnusedDetectorBlocks()
    }

    private fun clearUnusedDetectorBlocks() {
        var index = detectorBlockCount
        while (index < detectorBlocks.size && detectorBlocks[index] != null) {
            detectorBlocks[index] = null
            index++
        }
    }

    private fun addDetectorBlock(block: AudioProcessingBlock) {
        if (detectorBlockCount >= detectorBlocks.size) return
        detectorBlocks[detectorBlockCount++] = block
    }

    private fun detectorPeakAt(frame: Int): Float {
        var left = 0f
        var right = 0f
        var index = 0
        while (index < detectorBlockCount) {
            val source = detectorBlocks[index++] ?: continue
            if (frame !in 0 until source.frameCount || source.channels <= 0) continue
            val offset = frame * source.channels
            left += source.samples[offset]
            right += if (source.channels > 1) source.samples[offset + 1] else source.samples[offset]
        }
        return maxOf(kotlin.math.abs(left), kotlin.math.abs(right)).finiteOrZero()
    }

    private fun lookaheadFrames(milliseconds: Float): Int = ceil(
        milliseconds.coerceIn(0f, MAX_LOOKAHEAD_MS) * configuration.sampleRate.coerceAtLeast(1) / 1_000.0,
    ).toInt()

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        var gestureStart by remember { mutableStateOf(deviceState) }
        val startGesture = { gestureStart = state.value }
        val finishGesture = { pushStateChange(gestureStart, state.value) }
        val options = WorkspaceRepository.samplingChain.sampleOptions(eligibleSourceIds.value)
        val selectedLabel = options.firstOrNull { it.first == deviceState.sidechainSourceId }?.second
            ?: if (deviceState.sidechainSourceId == null) "None" else "Missing source"

        ChainDeviceShell(
            title = "Compressor",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(250.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Sidechain From",
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                    )
                    Select(
                        value = selectedLabel,
                        options = listOf("None") + options.map { it.second },
                        triggerHeight = 32.dp,
                        onValueChange = { label ->
                            val before = state.value
                            val id = options.firstOrNull { it.second == label }?.first
                            state.update { it.copy(sidechainSourceId = id) }
                            pushStateChange(before, state.value)
                        },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    EffectDial("attack", "Attack", PARAMETERS[0].normalize(deviceState.attackMs), "${deviceState.attackMs.roundToInt()} ms", startGesture, finishGesture) {
                        state.update { s -> s.copy(attackMs = PARAMETERS[0].denormalize(it)) }
                    }
                    EffectDial("release", "Release", PARAMETERS[1].normalize(deviceState.releaseMs), "${deviceState.releaseMs.roundToInt()} ms", startGesture, finishGesture) {
                        state.update { s -> s.copy(releaseMs = PARAMETERS[1].denormalize(it)) }
                    }
                    EffectDial("strength", "Strength", deviceState.strength, "${(deviceState.strength * 100).roundToInt()}%", startGesture, finishGesture) {
                        state.update { s -> s.copy(strength = it) }
                    }
                }
                if (deviceState.sidechainSourceId != null && options.none { it.first == deviceState.sidechainSourceId }) {
                    Text(
                        "Source missing or part of the compressed signal. Choose an external sample.",
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                    )
                }
            }
        }
    }

    companion object : ChainDeviceFactory<DuckerChainDeviceState> {
        override val capabilities = setOf(DeviceCapability.AudioEffect)
        override val stateClass = DuckerChainDeviceState::class
        override val serializer = DuckerChainDeviceState.serializer()
        override fun create() = DuckerChainDevice()
        val PARAMETERS = listOf(
            ParameterDescriptor("attack", "Attack", "ms", 0f, 500f, 5f),
            ParameterDescriptor("release", "Release", "ms", 10f, 2_000f, 180f, ParameterScale.Logarithmic),
            ParameterDescriptor("strength", "Strength", "%", 0f, 1f, 0.8f),
        )

        private const val MAX_DETECTOR_INPUTS = 128
        private const val MAX_LOOKAHEAD_MS = 10f
    }
}

@Serializable
data class DuckerChainDeviceState(
    val sidechainSourceId: String? = null,
    val attackMs: Float = 5f,
    val releaseMs: Float = 180f,
    val strength: Float = 0.8f,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
    val sidechainBusId: String? = null,
    val sidechainSourceIds: List<String> = emptyList(),
    /** Null keeps projects saved with the original Strength Ducker on its legacy transfer curve. */
    val thresholdDb: Float? = null,
    val ratio: Float = 4f,
    val kneeDb: Float = 0f,
    val detectorGainDb: Float = 0f,
    val makeupDb: Float = 0f,
    val dryWet: Float = 1f,
    val lookaheadMs: Float = 0f,
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}

private fun smoothEnvelope(current: Float, target: Float, milliseconds: Float, sampleRate: Int): Float {
    return smoothValue(current, target, milliseconds, sampleRate).coerceIn(0f, 1f)
}

private fun smoothValue(current: Float, target: Float, milliseconds: Float, sampleRate: Int): Float {
    if (milliseconds <= 0f) return target
    val frames = milliseconds * sampleRate.coerceAtLeast(1) / 1_000f
    val coefficient = exp(-1f / frames.coerceAtLeast(1f))
    return target + (current - target) * coefficient
}

private fun compressorGainReductionDb(
    detector: Float,
    thresholdDb: Float,
    ratio: Float,
    kneeDb: Float,
): Float {
    if (!detector.isFinite() || detector <= 0f) return 0f
    val levelDb = 20f * log10(detector.coerceAtLeast(1e-12f))
    val normalizedRatio = ratio.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    val slope = 1f - 1f / normalizedRatio
    val overDb = levelDb - thresholdDb.coerceIn(-120f, 24f)
    val knee = kneeDb.takeIf { it.isFinite() }?.coerceIn(0f, 48f) ?: 0f
    return when {
        slope <= 0f -> 0f
        knee <= 0f -> maxOf(0f, overDb * slope)
        // Ableton defines Knee as the distance on either side of Threshold:
        // with a 10 dB knee around -20 dB, compression begins at -30 dB and
        // reaches the full ratio at -10 dB. The usual compressor formula takes
        // a total knee width, so Live's value corresponds to twice that span.
        overDb <= -knee -> 0f
        overDb >= knee -> overDb * slope
        else -> {
            val kneePosition = overDb + knee
            slope * kneePosition * kneePosition / (4f * knee)
        }
    }.coerceAtLeast(0f)
}

private fun dbToLinear(decibels: Float): Float =
    10.0.pow(decibels.coerceIn(-120f, 60f) / 20.0).toFloat()

private fun Float.finiteOrOne(): Float = if (isFinite()) this else 1f

private fun Int.floorMod(divisor: Int): Int {
    val remainder = this % divisor
    return if (remainder < 0) remainder + divisor else remainder
}

private fun Chain.sampleOptions(eligibleIds: Set<String>): List<Pair<String, String>> {
    val raw = buildList {
        fun visit(chain: Chain, path: List<String>) {
            chain.devices.value.forEach { device ->
                if (device is SampleChainDevice && device.selectionUUID in eligibleIds) {
                    add(device.selectionUUID to (path + device.title).joinToString(" / "))
                }
                if (device is NestedChainDevice) {
                    device.nestedChains().forEachIndexed { index, nested ->
                        visit(nested, path + "${device.title} ${index + 1}")
                    }
                }
            }
        }
        visit(this@sampleOptions, emptyList())
    }
    val counts = raw.groupingBy { it.second }.eachCount()
    val occurrences = mutableMapOf<String, Int>()
    return raw.map { (id, title) ->
        val occurrence = (occurrences[title] ?: 0) + 1
        occurrences[title] = occurrence
        id to if (counts[title] == 1) title else "$title ($occurrence)"
    }
}
