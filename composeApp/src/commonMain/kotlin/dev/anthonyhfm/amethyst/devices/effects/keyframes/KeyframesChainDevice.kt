package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.koin.compose.koinInject

class KeyframesChainDevice : ChainDevice<KeyframesChainDeviceState>() {
    override val state = MutableStateFlow(KeyframesChainDeviceState())

    private val customMode: KeyframesWorkspaceMode = KeyframesWorkspaceMode(this)

    init {
        customMode.modeWakeup = {
            refreshVirtualDevices()
        }

        customMode.modeClose = {
            Heaven.devices.forEach { device ->
                device.previewState.clear()
            }
        }

        customMode.onVirtualDevicePress = { x, y, offset ->
            onSetKeyFilter(x, y, offset)
        }
    }

    @Composable
    override fun Content() {
        val controller = koinInject<WorkspaceController>()

        AmethystDevice(
            title = "Keyframes",
            modifier = Modifier
                .width(200.dp)
        ) {
            Button(
                onClick = {
                    controller.switchMode(
                        mode = customMode
                    )
                }
            ) {
                Text(
                    text = "Edit keyframes",
                )
            }
        }
    }

    fun refreshVirtualDevices() {
        Heaven.devices.forEach { device ->
            device.previewState.clear()

            state.value.filters.forEach { (x, y) ->
                if (x >= device.position.value.x.toInt() &&
                    x < device.position.value.x.toInt() + 10 &&
                    y >= device.position.value.y.toInt() && 
                    y < device.position.value.y.toInt() + 10) {

                    val localX = x - device.position.value.x.toInt()
                    val localY = 9 - (y - device.position.value.y.toInt())

                    device.previewState.sendToPreview(listOf(
                        RawUpdate(localX + localY * 10, Color.Green)
                    ))
                }
            }
        }
    }

    fun onSetKeyFilter(x: Int, y: Int, offset: Offset) {
        val globalX = offset.x.toInt() + x
        val globalY = offset.y.toInt() + (9 - y)

        val coordinatePair = Pair(globalX, globalY)

        val isAlreadyFiltered = state.value.filters.contains(coordinatePair)

        state.update { currentState ->
            if (isAlreadyFiltered) {
                currentState.copy(
                    filters = currentState.filters.filter { it != coordinatePair }
                )
            } else {
                currentState.copy(
                    filters = currentState.filters + coordinatePair
                )
            }
        }

        refreshVirtualDevices()
    }

    override fun midiEnter(n: List<Signal>) {
        val filteredSignals = n.filter { signal ->
            state.value.filters.contains(Pair(signal.x, signal.y))
        }

        if (filteredSignals.isNotEmpty()) {
            midiExit?.invoke(filteredSignals)
        }
    }
}

@Serializable
data class KeyframesChainDeviceState(
    val filters: List<Pair<Int, Int>> = emptyList()
) : DeviceState()
