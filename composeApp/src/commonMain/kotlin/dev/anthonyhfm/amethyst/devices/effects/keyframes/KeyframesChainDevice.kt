package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.*
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

class KeyframesChainDevice : ChainDevice<KeyframesChainDeviceState>() {
    override val state = MutableStateFlow(KeyframesChainDeviceState())

    private val customMode: KeyframesWorkspaceMode = KeyframesWorkspaceMode()

    init {
        renderAnimation()

        customMode.state = state.asStateFlow()

        customMode.onEvent = { onEvent(it) }

        customMode.modeWakeup = {
            refreshVirtualDevices()
        }

        customMode.modeClose = {
            Heaven.devices.forEach { device ->
                device.previewState.clear()
            }
        }

        customMode.onVirtualDevicePress = { x, y, offset ->
            onEvent(Event.OnPaintButton(x, y, offset))
        }
    }

    @Composable
    override fun Content() {
        val bpm = WorkspaceRepository.bpm.collectAsState()
        val state by state.collectAsState()

        LaunchedEffect(bpm, state.frames) {
            renderAnimation()
        }

        AmethystDevice(
            title = "Keyframes",
            modifier = Modifier
                .width(120.dp)
        ) {
            FilledIconButton(
                onClick = {
                    WorkspaceRepository.switchMode(mode = customMode)
                },
                modifier = Modifier
                    .size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Draw,
                    contentDescription = "Draw",
                    modifier = Modifier
                        .size(36.dp)
                )
            }
        }
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.OnPaintButton -> {
                val globalX = event.offset.x.toInt() + event.x
                val globalY = event.offset.y.toInt() + (9 - event.y)

                state.update { state ->
                    state.copy(
                        frames = state.frames.toMutableList().apply {
                            set(
                                index = state.selectedFrameIndex,
                                element = state.frames[state.selectedFrameIndex].copy(
                                    entries = state.frames[state.selectedFrameIndex].entries.toMutableList().apply {
                                        val index: Int = indexOfFirst { it.x == globalX && it.y == globalY }

                                        if (index != -1) {
                                            removeAt(index)
                                        }

                                        add(
                                            element = KeyframesEntry(
                                                x = globalX,
                                                y = globalY,
                                                r = state.selectedColor.first,
                                                g = state.selectedColor.second,
                                                b = state.selectedColor.third
                                            )
                                        )
                                    }
                                )
                            )
                        }
                    )
                }

                refreshVirtualDevices()
            }

            is Event.OnColorUpdate -> {
                state.update {
                    it.copy(selectedColor = Triple(event.color.red, event.color.green, event.color.blue))
                }
            }

            is Event.OnSelectFrame -> {
                if (event.frameIndex == state.value.frames.lastIndex + 1) {
                    onEvent(Event.OnAddFrame())
                    return
                }

                state.update {
                    it.copy(
                        selectedFrameIndex = if (event.frameIndex < 0) {
                            0
                        } else if (event.frameIndex >= it.frames.size) {
                            it.frames.size - 1
                        } else {
                            event.frameIndex
                        }
                    )
                }

                refreshVirtualDevices()
            }

            is Event.OnAddFrame -> {
                state.update {
                    it.copy(
                        frames = it.frames.toMutableList().apply {
                            if (event.atIndex != null) {
                                add(
                                    index = event.atIndex,
                                    element = Frame(
                                        timing = state.value.frames.getOrNull(event.atIndex - 1)?.timing
                                            ?: state.value.frames[state.value.selectedFrameIndex].timing
                                    )
                                )
                            } else {
                                add(Frame(state.value.frames[state.value.selectedFrameIndex].timing))
                            }
                        },
                    )
                }

                onEvent(Event.OnSelectFrame(event.atIndex ?: (state.value.frames.size - 1)))

                refreshVirtualDevices()
            }

            is Event.OnChangeFramePosition -> {
                state.update {
                    it.copy(
                        frames = it.frames.toMutableList().apply {
                            add(event.to, removeAt(event.from))
                        }
                    )
                }

                refreshVirtualDevices()
            }

            is Event.OnChangeFrameTiming -> {
                state.update {
                    it.copy(
                        frames = it.frames.toMutableList().apply {
                            set(
                                index = event.frameIndex,
                                element = it.frames[event.frameIndex].copy(timing = event.timing, gate = event.gate)
                            )
                        }
                    )
                }
            }
        }
    }

    fun refreshVirtualDevices() {
        Heaven.devices.forEach { device ->
            device.previewState.clear()

            state.value.frames[state.value.selectedFrameIndex].entries.forEach { (x, y, r, g, b) ->
                if (x >= device.position.value.x.toInt() &&
                    x < device.position.value.x.toInt() + 10 &&
                    y >= device.position.value.y.toInt() &&
                    y < device.position.value.y.toInt() + 10) {

                    val localX = x - device.position.value.x.toInt()
                    val localY = 9 - (y - device.position.value.y.toInt())

                    device.previewState.sendToPreview(listOf(
                        RawUpdate(localX + localY * 10, Color(r, g, b))
                    ))
                }
            }
        }
    }

    /**
     * Weird and complex rendering logic to create an animation based on the frames and their timings.
     * This prerenders the animation so that it can be played back smoothly later.
     *
     * Supposed to run better than rendering on the fly during playback.
     */
    fun renderAnimation() {
        val renderedAnimation: MutableList<Pair<Int, List<Signal>>> = mutableListOf()
        var animationMs = 0

        state.value.frames.plus(Frame(Timing.Rythm(Timing.Rythm.RythmTiming._1_16))).forEachIndexed { index, frame ->
            animationMs += ((state.value.frames.getOrNull(index - 1)?.timing?.toMsValue(WorkspaceRepository.bpm.value) ?: 0) * (frame.gate * 2)).toInt()

            renderedAnimation.add(
                Pair(
                    first = animationMs,
                    second = frame.entries.map { entry ->
                        Signal(
                            origin = this,
                            x = entry.x,
                            y = entry.y,
                            color = Color(entry.r, entry.g, entry.b),
                            layer = 0
                        )
                    }.plus(
                        state.value.frames.getOrNull(index - 1)?.entries?.filter { previousEntry ->
                            frame.entries.find { it.x == previousEntry.x && it.y == previousEntry.y } == null
                        }?.map {
                            Signal(
                                origin = this,
                                x = it.x,
                                y = it.y,
                                color = Color.Black, // Assuming black for cleared signals
                                layer = 0
                            )
                        } ?: emptyList()
                    )
                )
            )
        }

        state.update {
            it.copy(renderedAnimation = renderedAnimation)
        }
    }

    override fun midiEnter(n: List<Signal>) {
        n.forEach {
            if (it.color != Color.Black) {
                state.value.renderedAnimation.forEach {
                    Heaven.schedule(it.first.toDouble()) {
                        midiExit?.invoke(it.second)
                    }
                }
            }
        }
    }
}
