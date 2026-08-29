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
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.AudioFrameTriggerQueue
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.SidechainTriggerSink
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
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.devicesDepthFirst
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
import kotlin.math.roundToInt

@Serializable
enum class DuckerDetectorMode { Trigger, AudioEnvelope }

class DuckerChainDevice : AudioChainDevice<DuckerChainDeviceState>(), ParameterOwner, SidechainTriggerSink {
    override val state = MutableStateFlow(DuckerChainDeviceState())
    override val helpRef = "Ducker"
    override val parameterDescriptors get() = PARAMETERS
    override val sidechainSourceId: String? get() = state.value.sidechainSourceId

    private var configuration = AudioConfiguration(44_100, 2, 128)
    private val triggers = AudioFrameTriggerQueue()
    private var reduction = 0f
    private var attacking = false

    val droppedTriggerCount: Long get() = triggers.droppedCount
    val currentGainReduction: Float get() = reduction.coerceIn(0f, 1f)

    override fun enqueueSidechainTrigger(sourceId: String, targetFrame: Long) {
        if (sourceId == state.value.sidechainSourceId && state.value.detectorMode == DuckerDetectorMode.Trigger) {
            triggers.offer(targetFrame)
        }
    }

    override fun prepareAudio(configuration: AudioConfiguration) {
        this.configuration = configuration
        resetAudio()
    }

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        val snapshot = state.value
        var frame = 0
        while (frame < block.frameCount) {
            val absoluteFrame = context.absoluteFrame + frame
            var trigger = triggers.peek()
            while (trigger != null && trigger <= absoluteFrame) {
                triggers.poll()
                attacking = true
                trigger = triggers.peek()
            }

            val attackMs = resolveRealtimeParameter(PARAMETERS[0], snapshot.attackMs, absoluteFrame).coerceAtLeast(0f)
            val releaseMs = resolveRealtimeParameter(PARAMETERS[1], snapshot.releaseMs, absoluteFrame).coerceAtLeast(1f)
            val strength = resolveRealtimeParameter(PARAMETERS[2], snapshot.strength, absoluteFrame).coerceIn(0f, 1f)
            if (attacking) {
                val attackFrames = attackMs * configuration.sampleRate / 1_000f
                reduction = if (attackFrames <= 1f) 1f else (reduction + 1f / attackFrames).coerceAtMost(1f)
                if (reduction >= 1f) attacking = false
            } else if (reduction > 0f) {
                val releaseFrames = (releaseMs * configuration.sampleRate / 1_000f).coerceAtLeast(1f)
                reduction = (reduction - 1f / releaseFrames).coerceAtLeast(0f)
            }
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
        triggers.clear()
        reduction = 0f
        attacking = false
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val upstreamSamples = WorkspaceRepository.samplingChain.devicesDepthFirst()
            .takeWhile { it !== this }
            .filterIsInstance<SampleChainDevice>()
        val options = upstreamSamples.map { sample ->
            sample.selectionUUID to "${sample.title} · ${sample.selectionUUID.take(8)}"
        }
        val selectedLabel = options.firstOrNull { it.first == deviceState.sidechainSourceId }?.second
            ?: if (deviceState.sidechainSourceId == null) "None" else "Missing source"

        ChainDeviceShell(
            title = "Ducker",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(410.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sidechain From", style = Theme[typography][small])
                    Select(
                        value = selectedLabel,
                        options = listOf("None") + options.map { it.second },
                        triggerHeight = 44.dp,
                        onValueChange = { label ->
                            val before = state.value
                            val id = options.firstOrNull { it.second == label }?.first
                            state.update { it.copy(sidechainSourceId = id) }
                            pushStateChange(before, state.value)
                            parentChain?.onDeviceRuntimeStateChanged()
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
                        "Source missing or not upstream. Choose an earlier sample to repair this routing.",
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
    val detectorMode: DuckerDetectorMode = DuckerDetectorMode.Trigger,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}
