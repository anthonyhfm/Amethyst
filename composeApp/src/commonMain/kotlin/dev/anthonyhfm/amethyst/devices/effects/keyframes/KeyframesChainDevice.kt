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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

class KeyframesChainDevice : ChainDevice<KeyframesChainDeviceContract.KeyframesChainDeviceState>() {
    override val state = MutableStateFlow(KeyframesChainDeviceContract.KeyframesChainDeviceState())

    private val customMode: KeyframesWorkspaceMode = KeyframesWorkspaceMode()

    init {
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
            onEvent(KeyframesChainDeviceContract.Event.OnPaintButton(x, y, offset))
        }
    }

    @Composable
    override fun Content() {
        val controller = koinInject<WorkspaceRepository>()

        AmethystDevice(
            title = "Keyframes",
            modifier = Modifier
                .width(120.dp)
        ) {
            FilledIconButton(
                onClick = {
                    controller.switchMode(mode = customMode)
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

    fun onEvent(event: KeyframesChainDeviceContract.Event) {
        when (event) {
            is KeyframesChainDeviceContract.Event.OnPaintButton -> {
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
                                            element = KeyframesChainDeviceContract.KeyframesEntry(
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

            is KeyframesChainDeviceContract.Event.OnChangeFrameTiming -> {
                state.update {
                    it.copy(
                        frames = it.frames.toMutableList().apply {
                            set(
                                index = event.frameIndex,
                                element = it.frames[event.frameIndex].copy(timing = event.timing)
                            )
                        }
                    )
                }
            }

            is KeyframesChainDeviceContract.Event.OnColorUpdate -> {
                state.update {
                    it.copy(selectedColor = Triple(event.color.red, event.color.green, event.color.blue))
                }
            }

            is KeyframesChainDeviceContract.Event.OnSelectFrame -> {
                state.update {
                    it.copy(selectedFrameIndex = event.frameIndex)
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

    override fun midiEnter(n: List<Signal>) {
        /*val filteredSignals = n.filter { signal ->
            state.value.filters.contains(Pair(signal.x, signal.y))
        }

        if (filteredSignals.isNotEmpty()) {
            midiExit?.invoke(filteredSignals)
        }*/
    }
}
