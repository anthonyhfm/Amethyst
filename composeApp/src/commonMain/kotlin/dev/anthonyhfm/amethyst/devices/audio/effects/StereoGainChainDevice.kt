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

class StereoGainChainDevice : AudioChainDevice<StereoGainChainDeviceState>() {
    override val state = MutableStateFlow(StereoGainChainDeviceState())
    override val helpRef = "Stereo Gain"

    override fun processAudio(block: AudioProcessingBlock, context: AudioRenderContext) {
        val snapshot = state.value
        if (snapshot.muted) {
            block.clear()
            return
        }
        val outputGain = 10.0.pow(snapshot.gainDb.coerceFinite(0f) / 20.0).toFloat()
        val width = snapshot.width.coerceFinite(1f).coerceIn(0f, 4f)
        val balance = snapshot.balance.coerceFinite(0f).coerceIn(-1f, 1f)
        val leftBalance = if (balance > 0f) 1f - balance else 1f
        val rightBalance = if (balance < 0f) 1f + balance else 1f
        val leftPhase = if (snapshot.phaseInvertLeft) -1f else 1f
        val rightPhase = if (snapshot.phaseInvertRight) -1f else 1f

        var frame = 0
        while (frame < block.frameCount) {
            val offset = frame * block.channels
            if (block.channels == 1) {
                block.samples[offset] = (block.samples[offset] * leftPhase * outputGain).finiteAudio()
            } else {
                val inputLeft = block.samples[offset].finiteAudio() * leftPhase
                val inputRight = block.samples[offset + 1].finiteAudio() * rightPhase
                val mid = (inputLeft + inputRight) * 0.5f
                val side = if (snapshot.mono) 0f else (inputLeft - inputRight) * 0.5f * width
                block.samples[offset] = ((mid + side) * leftBalance * outputGain).finiteAudio()
                block.samples[offset + 1] = ((mid - side) * rightBalance * outputGain).finiteAudio()
            }
            frame++
        }
    }

    @Composable
    override fun Content() {
        val snapshot by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        ChainDeviceShell(
            title = "Stereo Gain",
            isSelected = selections.any { it.selectionUUID == selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(200.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(Modifier.padding(10.dp)) {
                Text("Gain ${snapshot.gainDb} dB")
                Text("Width ${(snapshot.width * 100f).toInt()}%")
            }
        }
    }

    companion object : ChainDeviceFactory<StereoGainChainDeviceState> {
        override val capabilities = setOf(DeviceCapability.AudioEffect)
        override val stateClass = StereoGainChainDeviceState::class
        override val serializer = StereoGainChainDeviceState.serializer()
        override fun create() = StereoGainChainDevice()
    }
}

@Serializable
data class StereoGainChainDeviceState(
    val gainDb: Float = 0f,
    val width: Float = 1f,
    val balance: Float = 0f,
    val phaseInvertLeft: Boolean = false,
    val phaseInvertRight: Boolean = false,
    val mono: Boolean = false,
    val muted: Boolean = false,
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState = copy(automations = automations)
}

private fun Float.coerceFinite(fallback: Float): Float = if (isFinite()) this else fallback
private fun Float.finiteAudio(): Float = if (isFinite()) this else 0f
