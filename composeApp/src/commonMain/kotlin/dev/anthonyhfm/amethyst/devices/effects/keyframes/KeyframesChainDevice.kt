package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.*
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class KeyframesChainDevice : ChainDevice<KeyframesChainDeviceState>() {
    override val state = MutableStateFlow(KeyframesChainDeviceState())

    private val customMode: KeyframesWorkspaceMode = KeyframesWorkspaceMode()

    init {
        renderAnimation()

        customMode.state = state.asStateFlow()
        customMode.parentDevice = this

        customMode.onEvent = { onEvent(it) }

        customMode.modeWakeup = {
            refreshVirtualDevices()
        }

        customMode.modeClose = {
            Heaven.devices.forEach { device ->
                device.previewState.clear()
            }
        }

        customMode.onVirtualDevicePress = { x, y ->
            onEvent(Event.OnPaintButton(x, y))
        }
    }

    @Composable
    override fun Content() {
        val bpm = WorkspaceRepository.bpm.collectAsState()
        val state by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        LaunchedEffect(bpm, state.frames) {
            renderAnimation()
        }

        AmethystDevice(
            title = "Keyframes",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
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
                state.update { state ->
                    state.copy(
                        frames = state.frames.toMutableList().apply {
                            set(
                                index = state.currentFrameIndex,
                                element = state.frames[state.currentFrameIndex].copy(
                                    entries = state.frames[state.currentFrameIndex].entries.toMutableList().apply {
                                        val index: Int = indexOfFirst { it.x == event.x && it.y == event.y }

                                        if (index != -1) {
                                            removeAt(index)
                                        }

                                        add(
                                            element = KeyframesEntry(
                                                x = event.x,
                                                y = event.y,
                                                r = state.selectedColor.first,
                                                g = state.selectedColor.second,
                                                b = state.selectedColor.third
                                            )
                                        )

                                        Heaven.midiEnter(
                                            listOf(
                                                Signal(
                                                    origin = this,
                                                    x = event.x,
                                                    y = event.y,
                                                    color = Color(
                                                        red = state.selectedColor.first,
                                                        green = state.selectedColor.second,
                                                        blue = state.selectedColor.third
                                                    ),
                                                    layer = 0
                                                )
                                            )
                                        )
                                    }
                                )
                            )
                        }
                    )
                }
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

                val keyframeItem = Selectable.KeyframeItem(
                    parent = this,
                    frameIndex = event.frameIndex
                )

                when {
                    event.rangeSelect -> {
                        SelectionManager.select(keyframeItem, single = false)
                    }
                    event.multiSelect -> {
                        SelectionManager.select(keyframeItem, single = false)
                    }
                    else -> {
                        SelectionManager.select(keyframeItem, single = true)
                    }
                }

                state.update {
                    it.copy(
                        currentFrameIndex = if (event.frameIndex < 0) {
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
                                            ?: state.value.frames[state.value.currentFrameIndex].timing
                                    )
                                )
                            } else {
                                add(Frame(state.value.frames[state.value.currentFrameIndex].timing))
                            }
                        },
                    )
                }

                onEvent(Event.OnSelectFrame(event.atIndex ?: (state.value.frames.size - 1)))

                refreshVirtualDevices()
            }

            is Event.OnDuplicateFrame -> {
                val frameIndexToDuplicate = event.frameIndex ?: state.value.currentFrameIndex
                val frameToDuplicate = state.value.frames[frameIndexToDuplicate]

                state.update {
                    it.copy(
                        frames = it.frames.toMutableList().apply {
                            add(
                                index = frameIndexToDuplicate + 1,
                                element = frameToDuplicate.copy(_internalUuid = UUID.randomUUID())
                            )
                        }
                    )
                }

                onEvent(Event.OnSelectFrame(frameIndexToDuplicate + 1))

                refreshVirtualDevices()
            }

            is Event.OnDeleteFrame -> {
                if (state.value.frames.size <= 1) return

                state.update {
                    it.copy(
                        frames = it.frames.toMutableList().apply {
                            removeAt(event.frameIndex)
                        },
                        currentFrameIndex = if (event.frameIndex == it.frames.lastIndex) {
                            it.frames.size - 2
                        } else {
                            it.currentFrameIndex
                        }
                    )
                }

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
        Heaven.clear()

        Heaven.midiEnter(
            state.value.frames[state.value.currentFrameIndex].entries.map {
                Signal(
                    origin = this,
                    x = it.x,
                    y = it.y,
                    color = Color(it.r, it.g, it.b),
                    layer = 0
                )
            }
        )
    }

    fun renderAnimation() {
        val bpm = WorkspaceRepository.bpm.value
        var animationMs = 0

        val frames = state.value.frames + Frame(Timing.Rythm(Timing.Rythm.RythmTiming._1_16))

        val renderedAnimation = buildList {
            frames.forEachIndexed { index, frame ->
                val previousFrame = frames.getOrNull(index - 1)
                val deltaMs = previousFrame?.timing?.toMsValue(bpm)?.times(frame.gate * 2)?.toInt() ?: 0
                animationMs += deltaMs

                val signals = buildList {
                    addAll(frame.entries.filter { !(previousFrame?.entries?.contains(it) ?: false) }.map { it.toSignal() })

                    if (previousFrame != null) {
                        val cleared = previousFrame.entries.filter { prev ->
                            frame.entries.none { it.x == prev.x && it.y == prev.y }
                        }.map {
                            it.toOffSignal()
                        }

                        addAll(cleared)
                    }
                }

                add(animationMs to signals)
            }
        }

        state.update { it.copy(renderedAnimation = renderedAnimation) }
    }

    private fun KeyframesEntry.toSignal() = Signal(
        origin = this,
        x = x,
        y = y,
        color = Color(r, g, b),
        layer = 0
    )

    private fun KeyframesEntry.toOffSignal() = Signal(
        origin = this,
        x = x,
        y = y,
        color = Color.Black,
        layer = 0
    )

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
