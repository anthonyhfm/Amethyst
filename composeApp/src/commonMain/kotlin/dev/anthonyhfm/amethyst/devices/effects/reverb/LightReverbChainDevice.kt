package dev.anthonyhfm.amethyst.devices.effects.reverb

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
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.parameter.ParameterDescriptor
import dev.anthonyhfm.amethyst.core.parameter.ParameterOwner
import dev.anthonyhfm.amethyst.core.parameter.resolveControlParameter
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToInt

class LightReverbChainDevice : GenericChainDevice<LightReverbChainDeviceState>(), Chokeable, ParameterOwner {
    override val helpRef = "Light Reverb"
    override val state = MutableStateFlow(LightReverbChainDeviceState())
    override val parameterDescriptors get() = PARAMETERS
    private val activeJobs = atomic(0)
    val activeJobCount: Int get() = activeJobs.value

    override fun timelineDuration(context: TimelineDurationContext): TimelineDuration {
        val snapshot = state.value
        val steps = decaySteps(snapshot.size)
        return TimelineDuration.Finite((diffusionIntervalMs(snapshot.size) * steps).toLong())
    }

    override fun signalEnter(n: List<Signal>) {
        triggerDialAutomations()
        val snapshot = state.value
        val size = resolveControlParameter(PARAMETERS[0], snapshot.size).coerceIn(0f, 1f)
        val decay = resolveControlParameter(PARAMETERS[1], snapshot.decay).coerceIn(0f, 0.95f)
        val diffusion = resolveControlParameter(PARAMETERS[2], snapshot.diffusion).coerceIn(0f, 1f)
        val mix = resolveControlParameter(PARAMETERS[3], snapshot.dryWet).coerceIn(0f, 1f)
        val damping = resolveControlParameter(PARAMETERS[4], snapshot.damping).coerceIn(0f, 1f)
        val dry = n.map { signal -> signal.withLightOpacity(1f - mix) }
        if (mix < 1f) signalExit?.invoke(dry)

        val steps = decaySteps(size)
        val interval = diffusionIntervalMs(size)
        var step = 1
        while (step <= steps && activeJobs.value < MAX_ACTIVE_JOBS) {
            val scheduledStep = step
            activeJobs.incrementAndGet()
            Heaven.schedule(
                delayInMs = interval * step,
                owner = this,
                identifier = step,
            ) {
                releaseJobSlot()
                val dampingGain = 1f - damping * (scheduledStep.toFloat() / steps) * 0.7f
                val opacity = (mix * decay.pow(scheduledStep) * dampingGain).coerceIn(0f, 1f)
                signalExit?.invoke(
                    n.map { signal -> signal.diffused(scheduledStep, diffusion, opacity) },
                )
            }
            step++
        }
    }

    override fun onChoke() {
        Heaven.cancelJobsForOwner(this)
        activeJobs.value = 0
    }

    override fun onRemovedFromChain() {
        onChoke()
        super.onRemovedFromChain()
    }

    private fun releaseJobSlot() {
        while (true) {
            val count = activeJobs.value
            if (count <= 0 || activeJobs.compareAndSet(count, count - 1)) return
        }
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        ChainDeviceShell(
            title = "Light Reverb",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(390.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LightReverbDial("size", "Size", deviceState.size) { state.update { s -> s.copy(size = it) } }
                    LightReverbDial("decay", "Decay", deviceState.decay) { state.update { s -> s.copy(decay = it) } }
                    LightReverbDial("diffusion", "Diffusion", deviceState.diffusion) { state.update { s -> s.copy(diffusion = it) } }
                    LightReverbDial("damping", "Damping", deviceState.damping) { state.update { s -> s.copy(damping = it) } }
                    LightReverbDial("dryWet", "Dry / Wet", deviceState.dryWet) { state.update { s -> s.copy(dryWet = it) } }
                }
            }
        }
    }

    companion object : ChainDeviceFactory<LightReverbChainDeviceState> {
        override val stateClass = LightReverbChainDeviceState::class
        override val serializer = LightReverbChainDeviceState.serializer()
        override fun create() = LightReverbChainDevice()
        val PARAMETERS = listOf(
            ParameterDescriptor("size", "Size", "%", 0f, 1f, 0.55f),
            ParameterDescriptor("decay", "Decay", "%", 0f, 0.95f, 0.65f),
            ParameterDescriptor("diffusion", "Diffusion", "%", 0f, 1f, 0.6f),
            ParameterDescriptor("dryWet", "Dry / Wet", "%", 0f, 1f, 0.5f),
            ParameterDescriptor("damping", "Damping", "%", 0f, 1f, 0.35f),
        )
        const val MAX_STEPS = 8
        const val MAX_ACTIVE_JOBS = 32
        internal fun decaySteps(size: Float): Int = (2 + size.coerceIn(0f, 1f) * 6f).roundToInt().coerceIn(2, MAX_STEPS)
        internal fun diffusionIntervalMs(size: Float): Double = 18.0 + size.coerceIn(0f, 1f) * 62.0
    }
}

@Serializable
data class LightReverbChainDeviceState(
    val size: Float = 0.55f,
    val decay: Float = 0.65f,
    val diffusion: Float = 0.6f,
    val damping: Float = 0.35f,
    val dryWet: Float = 0.5f,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}

@Composable
private fun LightReverbDial(id: String, title: String, value: Float, onValue: (Float) -> Unit) {
    AutomatableDial(
        parameterId = id,
        type = DialType.Continuous,
        title = title,
        text = "${(value * 100).roundToInt()}%",
        value = value,
        defaultValue = 0.5f,
        onValueChange = onValue,
        isFlat = true,
    )
}

internal fun Signal.withLightOpacity(multiplier: Float): Signal = if (this is Signal.LED) {
    copy(opacity = (opacity * multiplier).coerceIn(0f, 1f))
} else this

internal fun Signal.diffused(step: Int, amount: Float, opacityMultiplier: Float): Signal = if (this is Signal.LED) {
    val pattern = DIFFUSION_PATTERN[(step - 1) % DIFFUSION_PATTERN.size]
    val radius = (1 + step * amount.coerceIn(0f, 1f)).roundToInt()
    copy(
        x = (x + pattern.first * radius).coerceIn(0, 7),
        y = (y + pattern.second * radius).coerceIn(0, 7),
        opacity = (opacity * opacityMultiplier).coerceIn(0f, 1f),
    )
} else this

private val DIFFUSION_PATTERN = arrayOf(1 to 0, 0 to 1, -1 to 0, 0 to -1, 1 to 1, -1 to 1, -1 to -1, 1 to -1)
