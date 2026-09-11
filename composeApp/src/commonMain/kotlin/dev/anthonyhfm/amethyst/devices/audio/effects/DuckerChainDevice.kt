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
import kotlin.math.roundToInt

@Serializable
enum class DuckerDetectorMode { Trigger, AudioEnvelope }

class DuckerChainDevice : AudioChainDevice<DuckerChainDeviceState>(), ParameterOwner, SidechainAudioConsumer {
    override val state = MutableStateFlow(DuckerChainDeviceState())
    // Keep the persisted Kotlin type names stable for backwards compatibility;
    // the device is presented to users as Compressor everywhere in the UI.
    override val helpRef = "Compressor"
    override val parameterDescriptors get() = PARAMETERS
    override val sidechainSourceId: String? get() = state.value.sidechainSourceId

    private var configuration = AudioConfiguration(44_100, 2, 128)
    private var reduction = 0f
    private val eligibleSourceIds = atomic(emptySet<String>())

    val currentGainReduction: Float get() = reduction.coerceIn(0f, 1f)

    override fun replaceEligibleSidechainSources(sourceIds: Set<String>) {
        eligibleSourceIds.value = sourceIds.toSet()
    }

    override fun prepareAudio(configuration: AudioConfiguration) {
        this.configuration = configuration
        resetAudio()
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        val snapshot = state.value
        val source = snapshot.sidechainSourceId
            ?.takeIf { it in eligibleSourceIds.value }
            ?.let(context::sidechainInput)
        var frame = 0
        while (frame < block.frameCount) {
            val absoluteFrame = context.absoluteFrame + frame
            val attackMs = resolveRealtimeParameter(PARAMETERS[0], snapshot.attackMs, absoluteFrame).coerceAtLeast(0f)
            val releaseMs = resolveRealtimeParameter(PARAMETERS[1], snapshot.releaseMs, absoluteFrame).coerceAtLeast(1f)
            val strength = resolveRealtimeParameter(PARAMETERS[2], snapshot.strength, absoluteFrame).coerceIn(0f, 1f)
            val detector = source?.peakAt(frame)?.coerceIn(0f, 1f) ?: 0f
            val smoothingMs = if (detector > reduction) attackMs else releaseMs
            reduction = smoothEnvelope(reduction, detector, smoothingMs, configuration.sampleRate)
            val gain = (1f - reduction * strength).coerceIn(0f, 1f)
            val offset = frame * block.channels
            var channel = 0
            while (channel < block.channels) {
                block.samples[offset + channel] = (block.samples[offset + channel] * gain).finiteOrZero()
                channel++
            }
            frame++
        }
    }

    override fun resetAudio() {
        reduction = 0f
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
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
                    EffectDial("attack", "Attack", PARAMETERS[0].normalize(deviceState.attackMs), "${deviceState.attackMs.roundToInt()} ms") {
                        state.update { s -> s.copy(attackMs = PARAMETERS[0].denormalize(it)) }
                    }
                    EffectDial("release", "Release", PARAMETERS[1].normalize(deviceState.releaseMs), "${deviceState.releaseMs.roundToInt()} ms") {
                        state.update { s -> s.copy(releaseMs = PARAMETERS[1].denormalize(it)) }
                    }
                    EffectDial("strength", "Strength", deviceState.strength, "${(deviceState.strength * 100).roundToInt()}%") {
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
    }
}

@Serializable
data class DuckerChainDeviceState(
    val sidechainSourceId: String? = null,
    val attackMs: Float = 5f,
    val releaseMs: Float = 180f,
    val strength: Float = 0.8f,
    val detectorMode: DuckerDetectorMode = DuckerDetectorMode.AudioEnvelope,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}

private fun AudioProcessingBlock.peakAt(frame: Int): Float {
    if (frame !in 0 until frameCount) return 0f
    val offset = frame * channels
    var peak = 0f
    var channel = 0
    while (channel < channels) {
        peak = maxOf(peak, kotlin.math.abs(samples[offset + channel]))
        channel++
    }
    return peak
}

private fun smoothEnvelope(current: Float, target: Float, milliseconds: Float, sampleRate: Int): Float {
    if (milliseconds <= 0f) return target
    val frames = milliseconds * sampleRate.coerceAtLeast(1) / 1_000f
    val coefficient = kotlin.math.exp(-1f / frames.coerceAtLeast(1f))
    return (target + (current - target) * coefficient).coerceIn(0f, 1f)
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
