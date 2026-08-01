package dev.anthonyhfm.amethyst.devices.ableton

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.data.DRUM_RACK_TO_XY
import dev.anthonyhfm.amethyst.core.midi.data.XY_TO_DRUM_RACK
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

class AbletonArpeggiatorChainDevice : GenericChainDevice<AbletonArpeggiatorChainDeviceState>() {
    override val state = MutableStateFlow(AbletonArpeggiatorChainDeviceState())

    override fun timelineDuration(context: TimelineDurationContext): TimelineDuration {
        val current = state.value
        if (current.steps <= 0) return TimelineDuration.None
        val rateMs = current.rate.toMsValue(context.bpm.toDouble())
        val duration = (current.steps - 1L) * rateMs + (rateMs * (current.gate / 100f)).toLong()
        return TimelineDuration.Finite(duration.coerceAtLeast(0L))
    }

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == selectionUUID }

        ChainDeviceShell(
            title = "Arpeggiator",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(200.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "This device's origin is the Ableton Converter. It is not interactable and only used for simulating Abletons Arpeggiator.",
                    color = Theme[colors][primaryForeground],
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                )
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
        n.forEach { signal ->
            when (signal) {
                is Signal.AudioSignal -> signalExit?.invoke(listOf(signal))
                is Signal.LED, is Signal.Midi -> Heaven.devices.forEach deviceLoop@ { device ->
                    val pos = device.position.value
                    val (x, y) = when (signal) {
                        is Signal.LED -> signal.x to signal.y
                        is Signal.Midi -> signal.x to signal.y
                        is Signal.AudioSignal -> error("Audio signals are handled before coordinate conversion")
                    }

                    if (pos.x > x || pos.x + device.layout.cols < x || pos.y > y || pos.y + device.layout.cols < y) {
                        return@deviceLoop
                    }

                    val signalX = x - pos.x
                    val signalY = y - pos.y

                    val local = (signalX + ((9 - (signalY)) * 10)).toInt()

                    repeat(state.value.steps) { index ->
                        Heaven.schedule(
                            delayInMs = state.value.rate.toMsValue(WorkspaceRepository.bpm.value).toDouble() * index,
                            owner = this,
                        ) {
                            val drIndex: Int = XY_TO_DRUM_RACK[local] + index

                            val newX = DRUM_RACK_TO_XY[drIndex] % 10 + pos.x.toInt()
                            val newY = 9 - DRUM_RACK_TO_XY[drIndex] / 10 + pos.y.toInt()

                            signalExit?.invoke(
                                listOf(
                                    when (signal) {
                                        is Signal.LED -> signal.copy(
                                            x = newX,
                                            y = newY,
                                            color = state.value.color?.let {
                                                Color(
                                                    red = it.first,
                                                    green = it.second,
                                                    blue = it.second,
                                                )
                                            } ?: signal.color
                                        )
                                        is Signal.Midi -> signal.copy(x = newX, y = newY)
                                        is Signal.AudioSignal -> error("Audio signals are handled before coordinate conversion")
                                    }
                                )
                            )

                            Heaven.schedule(
                                delayInMs = (state.value.rate.toMsValue(WorkspaceRepository.bpm.value).toDouble() * (state.value.gate / 100f)),
                                owner = this,
                            ) {
                                signalExit?.invoke(
                                    listOf(
                                        when (signal) {
                                            is Signal.LED -> signal.copy(x = newX, y = newY, color = Color.Black)
                                            is Signal.Midi -> signal.copy(x = newX, y = newY, velocity = 0)
                                            is Signal.AudioSignal -> error("Audio signals are handled before coordinate conversion")
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object : ChainDeviceFactory<AbletonArpeggiatorChainDeviceState> {
        override val stateClass = AbletonArpeggiatorChainDeviceState::class
        override val serializer = AbletonArpeggiatorChainDeviceState.serializer()
        override fun create() = AbletonArpeggiatorChainDevice()
    }
}

@Serializable
data class AbletonArpeggiatorChainDeviceState(
    val rate: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_8),
    val distance: Int = 12,
    val steps: Int = 0,
    val repeats: Int? = null,
    val color: Triple<Float, Float, Float>? = null,
    val gate: Float = 50f // ableton handles this differently
) : DeviceState()
